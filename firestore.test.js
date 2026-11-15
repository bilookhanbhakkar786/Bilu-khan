const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const PRINCIPAL_UID = "principal_alice";
const TEACHER_UID = "teacher_bob";
const STUDENT_UID = "student_carol";
const OTHER_UID = "outsider_dave";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function ts(db) {
  return db.app ? serverTimestamp() : new Date();
}

test("Unauthenticated user: cannot read users or schools", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(PRINCIPAL_UID).get());
  await assertFails(unauthDb.collection("schools").where("memberUids", "array-contains", PRINCIPAL_UID).get());
});

test("User PII isolation: cannot read or write another user's profile", async () => {
  const aliceDb = testEnv.authenticatedContext(PRINCIPAL_UID, { email: "alice@school.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(TEACHER_UID, { email: "bob@school.com", email_verified: true }).firestore();

  const batch = aliceDb.batch();
  await assertSucceeds(
    aliceDb.collection("users").doc(PRINCIPAL_UID).set({
      userId: PRINCIPAL_UID,
      fullName: "Alice Principal",
      email: "alice@school.com",
      phone: "123456",
      accountIntent: "principal",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(bobDb.collection("users").doc(PRINCIPAL_UID).get());
  await assertFails(
    bobDb.collection("users").doc(PRINCIPAL_UID).update({ fullName: "Hacked", updatedAt: new Date() })
  );
  // Shadow update with ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(PRINCIPAL_UID).update({ isAdmin: true, updatedAt: new Date() })
  );
});

test("Principal 1-school-per-account lock & atomic creation", async () => {
  const aliceDb = testEnv.authenticatedContext(PRINCIPAL_UID, { email: "alice@school.com", email_verified: true }).firestore();
  const now = new Date();

  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("principal_locks").doc(PRINCIPAL_UID), {
    userId: PRINCIPAL_UID,
    schoolId: "school_1",
    createdAt: now,
  });
  batch.set(aliceDb.collection("schools").doc("school_1"), {
    schoolCode: "BL-100001",
    ownerId: PRINCIPAL_UID,
    name: "Beacon Light Academy",
    schoolType: "Private",
    address: "123 Campus Rd",
    city: "Lahore",
    contactNumber: "+923001234567",
    email: "info@beacon.edu",
    description: "Test school",
    galleryUrls: [],
    timezone: "UTC",
    memberUids: [PRINCIPAL_UID],
    staffUids: [PRINCIPAL_UID],
    sessionIds: [],
    classIds: [],
    sectionIds: [],
    createdAt: now,
    updatedAt: now,
  });
  batch.set(aliceDb.collection("schools").doc("school_1").collection("members").doc(PRINCIPAL_UID), {
    schoolId: "school_1",
    schoolOwnerId: PRINCIPAL_UID,
    userId: PRINCIPAL_UID,
    role: "principal",
    status: "active",
    createdAt: now,
    updatedAt: now,
  });
  batch.set(aliceDb.collection("school_directory").doc("BL-100001"), {
    schoolId: "school_1",
    schoolCode: "BL-100001",
    ownerId: PRINCIPAL_UID,
    name: "Beacon Light Academy",
    schoolType: "Private",
    city: "Lahore",
    isPublic: true,
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(batch.commit());

  // Second school creation by same principal must FAIL
  const batch2 = aliceDb.batch();
  batch2.set(aliceDb.collection("principal_locks").doc(PRINCIPAL_UID), {
    userId: PRINCIPAL_UID,
    schoolId: "school_2",
    createdAt: now,
  });
  batch2.set(aliceDb.collection("schools").doc("school_2"), {
    schoolCode: "BL-100002",
    ownerId: PRINCIPAL_UID,
    name: "Second School",
    schoolType: "Private",
    address: "456 Rd",
    city: "Lahore",
    contactNumber: "+923001234568",
    email: "info2@beacon.edu",
    description: "Second",
    galleryUrls: [],
    timezone: "UTC",
    memberUids: [PRINCIPAL_UID],
    staffUids: [PRINCIPAL_UID],
    createdAt: now,
    updatedAt: now,
  });
  batch2.set(aliceDb.collection("schools").doc("school_2").collection("members").doc(PRINCIPAL_UID), {
    schoolId: "school_2",
    schoolOwnerId: PRINCIPAL_UID,
    userId: PRINCIPAL_UID,
    role: "principal",
    status: "active",
    createdAt: now,
    updatedAt: now,
  });
  await assertFails(batch2.commit());
});

test("Privilege escalation blocked: user cannot self-assign Teacher or Student role without invite/approval", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    const now = new Date();
    await adminDb.collection("schools").doc("school_1").set({
      schoolCode: "BL-100001",
      ownerId: PRINCIPAL_UID,
      name: "Beacon Light Academy",
      schoolType: "Private",
      address: "123 Rd",
      city: "Lahore",
      contactNumber: "123456",
      email: "info@beacon.edu",
      description: "Desc",
      galleryUrls: [],
      timezone: "UTC",
      memberUids: [PRINCIPAL_UID],
      staffUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const outsiderDb = testEnv.authenticatedContext(OTHER_UID, { email: "dave@evil.com", email_verified: true }).firestore();
  const now = new Date();

  // Outsider tries to forge principal or teacher membership directly
  await assertFails(
    outsiderDb.collection("schools").doc("school_1").collection("members").doc(OTHER_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: OTHER_UID,
      role: "teacher",
      status: "active",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    outsiderDb.collection("schools").doc("school_1").collection("members").doc(OTHER_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: OTHER_UID,
      role: "student",
      status: "active",
      createdAt: now,
      updatedAt: now,
    })
  );
  // Outsider tries to read school students
  await assertFails(
    outsiderDb.collection("schools").doc("school_1").collection("students").where("schoolOwnerId", "==", PRINCIPAL_UID).get()
  );
});

test("Teacher invitation acceptance & email verification check", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    const now = new Date();
    await adminDb.collection("schools").doc("school_1").set({
      schoolCode: "BL-100001",
      ownerId: PRINCIPAL_UID,
      name: "Beacon Light Academy",
      schoolType: "Private",
      address: "123 Rd",
      city: "Lahore",
      contactNumber: "123456",
      email: "info@beacon.edu",
      description: "Desc",
      galleryUrls: [],
      timezone: "UTC",
      memberUids: [PRINCIPAL_UID],
      staffUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("members").doc(PRINCIPAL_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: PRINCIPAL_UID,
      role: "principal",
      status: "active",
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(PRINCIPAL_UID, { email: "alice@school.com", email_verified: true }).firestore();
  const perms = {
    viewStudents: true,
    editStudents: false,
    markAttendance: true,
    editAttendance: false,
    createTests: true,
    enterMarks: true,
    publishAnnouncements: true,
    viewReports: true,
  };
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("teacher_invitations").doc("TCH-123456").set({
      schoolId: "school_1",
      schoolName: "Beacon Light Academy",
      schoolOwnerId: PRINCIPAL_UID,
      email: "bob@school.com",
      emailLower: "bob@school.com",
      fullName: "Bob Teacher",
      phone: "999",
      inviteCode: "TCH-123456",
      permissions: perms,
      status: "pending",
      invitedBy: PRINCIPAL_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Email spoofing test: unverified email cannot list pending invitations by emailLower
  const spoofDb = testEnv.authenticatedContext("spoof_uid", { email: "bob@school.com", email_verified: false }).firestore();
  await assertFails(
    spoofDb.collection("teacher_invitations").where("emailLower", "==", "bob@school.com").get()
  );

  // Verified Bob accepts invitation atomically
  const bobDb = testEnv.authenticatedContext(TEACHER_UID, { email: "bob@school.com", email_verified: true }).firestore();
  await assertSucceeds(
    bobDb.collection("teacher_invitations").where("emailLower", "==", "bob@school.com").get()
  );

  const acceptBatch = bobDb.batch();
  acceptBatch.update(bobDb.collection("teacher_invitations").doc("TCH-123456"), {
    status: "accepted",
    acceptedByUid: TEACHER_UID,
    updatedAt: now,
  });
  acceptBatch.set(bobDb.collection("schools").doc("school_1").collection("members").doc(TEACHER_UID), {
    schoolId: "school_1",
    schoolOwnerId: PRINCIPAL_UID,
    userId: TEACHER_UID,
    role: "teacher",
    status: "active",
    inviteCode: "TCH-123456",
    createdAt: now,
    updatedAt: now,
  });
  acceptBatch.set(bobDb.collection("schools").doc("school_1").collection("teachers").doc(TEACHER_UID), {
    schoolId: "school_1",
    schoolOwnerId: PRINCIPAL_UID,
    userId: TEACHER_UID,
    fullName: "Bob Teacher",
    email: "bob@school.com",
    phone: "999",
    joiningDate: "2026-01-01",
    isActive: true,
    permissions: perms,
    staffUids: [PRINCIPAL_UID, TEACHER_UID],
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(acceptBatch.commit());
});

test("Student connection request & query alignment", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    const now = new Date();
    await adminDb.collection("schools").doc("school_1").set({
      schoolCode: "BL-100001",
      ownerId: PRINCIPAL_UID,
      name: "Beacon Light Academy",
      schoolType: "Private",
      address: "123 Rd",
      city: "Lahore",
      contactNumber: "123456",
      email: "info@beacon.edu",
      description: "Desc",
      galleryUrls: [],
      timezone: "UTC",
      memberUids: [PRINCIPAL_UID, STUDENT_UID],
      staffUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("students").doc("st_1").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      classId: "cls_1",
      sectionId: "sec_1",
      studentCode: "STU-1001",
      rollNumber: "12",
      fullName: "Carol Student",
      fatherName: "John",
      gender: "Female",
      parentName: "John",
      parentContact: "111",
      address: "Addr",
      admissionDate: "2026-01-01",
      status: "active",
      linkedUserId: STUDENT_UID,
      staffUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const carolDb = testEnv.authenticatedContext(STUDENT_UID, { email: "carol@school.com", email_verified: true }).firestore();
  const outsiderDb = testEnv.authenticatedContext(OTHER_UID, { email: "dave@evil.com", email_verified: true }).firestore();

  // Carol cannot submit an already-approved request!
  await assertFails(
    carolDb.collection("schools").doc("school_1").collection("student_requests").doc(STUDENT_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      schoolName: "Beacon Light Academy",
      userId: STUDENT_UID,
      requestedRollNumber: "12",
      requestedFullName: "Carol Student",
      requestedFatherName: "John",
      requestedSessionName: "2025-2026",
      requestedClassName: "8th",
      requestedSectionName: "A",
      status: "approved",
      staffUids: [PRINCIPAL_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Carol CAN submit pending request
  await assertSucceeds(
    carolDb.collection("schools").doc("school_1").collection("student_requests").doc(STUDENT_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      schoolName: "Beacon Light Academy",
      userId: STUDENT_UID,
      requestedRollNumber: "12",
      requestedFullName: "Carol Student",
      requestedFatherName: "John",
      requestedSessionName: "2025-2026",
      requestedClassName: "8th",
      requestedSectionName: "A",
      status: "pending",
      staffUids: [PRINCIPAL_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Student can query ONLY their own linked student record
  await assertSucceeds(
    carolDb.collection("schools").doc("school_1").collection("students").where("linkedUserId", "==", STUDENT_UID).get()
  );
  // Student cannot list all school students
  await assertFails(
    carolDb.collection("schools").doc("school_1").collection("students").get()
  );
  // Outsider cannot read Carol's student record
  await assertFails(
    outsiderDb.collection("schools").doc("school_1").collection("students").doc("st_1").get()
  );
});
