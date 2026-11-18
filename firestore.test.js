const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const assert = require("node:assert");

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

test("Privilege escalation blocked: outsider cannot self-assign Teacher or enter a hidden School", async () => {
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
      isPublic: false,
      memberUids: [PRINCIPAL_UID],
      staffUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const outsiderDb = testEnv.authenticatedContext(OTHER_UID, { email: "dave@evil.com", email_verified: true }).firestore();
  const now = new Date();

  // Outsider tries to forge teacher membership directly
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
  // Outsider CANNOT self-join a hidden school as a student
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

test("Student connection request & school-wide read-only access vs write denial", async () => {
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
    await adminDb.collection("schools").doc("school_1").collection("members").doc(STUDENT_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: STUDENT_UID,
      role: "student",
      status: "active",
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
      admissionDate: "01-01-2026",
      status: "active",
      linkedUserId: STUDENT_UID,
      staffUids: [PRINCIPAL_UID],
      memberUids: [PRINCIPAL_UID, STUDENT_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("attendance_records").doc("att_1").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      attendanceSessionId: "as_1",
      studentId: "st_1",
      linkedUserId: STUDENT_UID,
      status: "present",
      note: "",
      editHistory: [],
      staffUids: [PRINCIPAL_UID],
      memberUids: [PRINCIPAL_UID, STUDENT_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("test_results").doc("tr_1").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      testId: "t_1",
      studentId: "st_1",
      linkedUserId: STUDENT_UID,
      obtainedMarks: 88,
      percentage: 88.0,
      grade: "A",
      passed: true,
      rank: 1,
      staffUids: [PRINCIPAL_UID],
      memberUids: [PRINCIPAL_UID, STUDENT_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const carolDb = testEnv.authenticatedContext(STUDENT_UID, { email: "carol@school.com", email_verified: true }).firestore();
  const outsiderDb = testEnv.authenticatedContext(OTHER_UID, { email: "dave@evil.com", email_verified: true }).firestore();

  // Outsider guest CAN submit a pending student request even without pre-known class/section
  await assertFails(
    outsiderDb.collection("schools").doc("school_1").collection("student_requests").doc(OTHER_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      schoolName: "Beacon Light Academy",
      userId: OTHER_UID,
      requestedRollNumber: "External",
      requestedFullName: "Dave Guest",
      requestedFatherName: "N/A",
      requestedSessionName: "",
      requestedClassName: "",
      requestedSectionName: "",
      status: "approved",
      staffUids: [PRINCIPAL_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    outsiderDb.collection("schools").doc("school_1").collection("student_requests").doc(OTHER_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      schoolName: "Beacon Light Academy",
      userId: OTHER_UID,
      requestedRollNumber: "External",
      requestedFullName: "Dave Guest",
      requestedFatherName: "N/A",
      requestedSessionName: "",
      requestedClassName: "",
      requestedSectionName: "",
      status: "pending",
      staffUids: [PRINCIPAL_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Approved student can read school-wide students, attendance records, and test results via memberUids
  await assertSucceeds(
    carolDb.collection("schools").doc("school_1").collection("students").where("memberUids", "array-contains", STUDENT_UID).get()
  );
  await assertSucceeds(
    carolDb.collection("schools").doc("school_1").collection("attendance_records").where("memberUids", "array-contains", STUDENT_UID).get()
  );
  await assertSucceeds(
    carolDb.collection("schools").doc("school_1").collection("test_results").where("memberUids", "array-contains", STUDENT_UID).get()
  );

  // Student CANNOT create, update, or delete student records
  await assertFails(
    carolDb.collection("schools").doc("school_1").collection("students").doc("st_1").update({
      fullName: "Tampered",
      updatedAt: new Date(),
    })
  );
  await assertFails(
    carolDb.collection("schools").doc("school_1").collection("students").doc("st_2").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      classId: "cls_1",
      sectionId: "sec_1",
      studentCode: "STU-1002",
      rollNumber: "1",
      fullName: "Fake Student",
      fatherName: "Fake",
      gender: "Male",
      parentName: "Fake",
      parentContact: "000",
      address: "",
      admissionDate: "01-01-2026",
      status: "active",
      staffUids: [PRINCIPAL_UID],
      memberUids: [PRINCIPAL_UID, STUDENT_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertFails(
    carolDb.collection("schools").doc("school_1").collection("students").doc("st_1").delete()
  );

  // Outsider cannot read Carol's student record or query school students
  await assertFails(
    outsiderDb.collection("schools").doc("school_1").collection("students").doc("st_1").get()
  );
  await assertFails(
    outsiderDb.collection("schools").doc("school_1").collection("students").get()
  );
  await assertFails(
    outsiderDb.collection("schools").doc("school_1").collection("students").where("schoolOwnerId", "==", PRINCIPAL_UID).get()
  );
  await assertFails(
    outsiderDb.collection("schools").doc("school_1").collection("students").where("memberUids", "array-contains", STUDENT_UID).get()
  );
});

test("Teacher self-service request, principal approval, and shared teacher class/lecture/attendance writes", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
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
      sessionIds: [],
      classIds: [],
      sectionIds: [],
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
  const bobDb = testEnv.authenticatedContext(TEACHER_UID, { email: "bob@school.com", email_verified: true }).firestore();

  // Teacher CANNOT forge an approved teacher request
  await assertFails(
    bobDb.collection("schools").doc("school_1").collection("teacher_requests").doc(TEACHER_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      schoolName: "Beacon Light Academy",
      userId: TEACHER_UID,
      fullName: "Bob Teacher",
      email: "bob@school.com",
      phone: "03001112222",
      subjectSpecialty: "Physics",
      note: "Self approving",
      status: "approved",
      staffUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Teacher CAN submit pending join request
  await assertSucceeds(
    bobDb.collection("schools").doc("school_1").collection("teacher_requests").doc(TEACHER_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      schoolName: "Beacon Light Academy",
      userId: TEACHER_UID,
      fullName: "Bob Teacher",
      email: "bob@school.com",
      phone: "03001112222",
      subjectSpecialty: "Physics",
      note: "Would like to join faculty",
      status: "pending",
      staffUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Principal approves teacher join request
  const fullPerms = {
    viewStudents: true,
    editStudents: true,
    markAttendance: true,
    editAttendance: true,
    createTests: true,
    enterMarks: true,
    publishAnnouncements: true,
    viewReports: true,
  };
  const approveBatch = aliceDb.batch();
  approveBatch.update(aliceDb.collection("schools").doc("school_1").collection("teacher_requests").doc(TEACHER_UID), {
    status: "approved",
    staffUids: [PRINCIPAL_UID, TEACHER_UID],
    updatedAt: now,
  });
  approveBatch.set(aliceDb.collection("schools").doc("school_1").collection("members").doc(TEACHER_UID), {
    schoolId: "school_1",
    schoolOwnerId: PRINCIPAL_UID,
    userId: TEACHER_UID,
    role: "teacher",
    status: "active",
    createdAt: now,
    updatedAt: now,
  });
  approveBatch.set(aliceDb.collection("schools").doc("school_1").collection("teachers").doc(TEACHER_UID), {
    schoolId: "school_1",
    schoolOwnerId: PRINCIPAL_UID,
    userId: TEACHER_UID,
    fullName: "Bob Teacher",
    email: "bob@school.com",
    phone: "03001112222",
    joiningDate: "15-11-2026",
    isActive: true,
    permissions: fullPerms,
    staffUids: [PRINCIPAL_UID, TEACHER_UID],
    memberUids: [PRINCIPAL_UID, TEACHER_UID],
    createdAt: now,
    updatedAt: now,
  });
  approveBatch.update(aliceDb.collection("schools").doc("school_1"), {
    memberUids: [PRINCIPAL_UID, TEACHER_UID],
    staffUids: [PRINCIPAL_UID, TEACHER_UID],
    updatedAt: now,
  });
  await assertSucceeds(approveBatch.commit());

  // Approved teacher CAN create class, section with customLectures, and attendance session without teacher_assignments
  const classRef = bobDb.collection("schools").doc("school_1").collection("classes").doc("cls_9");
  await assertSucceeds(
    classRef.set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      name: "9th",
      description: "Grade 9",
      staffUids: [PRINCIPAL_UID, TEACHER_UID],
      memberUids: [PRINCIPAL_UID, TEACHER_UID],
      createdAt: now,
      updatedAt: now,
    })
  );
  const secRef = bobDb.collection("schools").doc("school_1").collection("sections").doc("sec_9a");
  await assertSucceeds(
    secRef.set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      classId: "cls_9",
      name: "A",
      description: "Section A",
      customLectures: ["Lecture 7"],
      staffUids: [PRINCIPAL_UID, TEACHER_UID],
      memberUids: [PRINCIPAL_UID, TEACHER_UID],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("schools").doc("school_1").collection("attendance_sessions").doc("att_s1").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      classId: "cls_9",
      sectionId: "sec_9a",
      attendanceDate: "15-11-2026",
      subject: "Physics",
      sessionSlot: "Lecture 7",
      teacherId: TEACHER_UID,
      teacherName: "Bob Teacher",
      staffUids: [PRINCIPAL_UID, TEACHER_UID],
      memberUids: [PRINCIPAL_UID, TEACHER_UID],
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Principal replacement handover invite: atomic ownership transfer & old owner lock cleanup", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("principal_locks").doc(PRINCIPAL_UID).set({
      userId: PRINCIPAL_UID,
      schoolId: "school_1",
      createdAt: now,
    });
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
    await adminDb.collection("school_directory").doc("BL-100001").set({
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
  });

  const aliceDb = testEnv.authenticatedContext(PRINCIPAL_UID, { email: "alice@school.com", email_verified: true }).firestore();
  const newPrincipalDb = testEnv.authenticatedContext(TEACHER_UID, { email: "bob@school.com", email_verified: true }).firestore();
  const outsiderDb = testEnv.authenticatedContext(OTHER_UID, { email: "dave@evil.com", email_verified: true }).firestore();

  // Principal creates PRN invite
  await assertSucceeds(
    aliceDb.collection("principal_invitations").doc("PRN-777888").set({
      schoolId: "school_1",
      schoolName: "Beacon Light Academy",
      schoolCode: "BL-100001",
      schoolOwnerId: PRINCIPAL_UID,
      email: "bob@school.com",
      emailLower: "bob@school.com",
      fullName: "Bob Next Principal",
      inviteCode: "PRN-777888",
      status: "pending",
      invitedBy: PRINCIPAL_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Outsider cannot tamper school ownerId without matching atomic batch
  await assertFails(
    outsiderDb.collection("schools").doc("school_1").update({
      ownerId: OTHER_UID,
      lastAcceptedPrincipalInviteCode: "PRN-777888",
      memberUids: [PRINCIPAL_UID, OTHER_UID],
      staffUids: [PRINCIPAL_UID, OTHER_UID],
      updatedAt: now,
    })
  );

  // Bob accepts PRN-777888 atomically
  const transferBatch = newPrincipalDb.batch();
  transferBatch.update(newPrincipalDb.collection("principal_invitations").doc("PRN-777888"), {
    status: "accepted",
    acceptedByUid: TEACHER_UID,
    updatedAt: now,
  });
  transferBatch.set(newPrincipalDb.collection("principal_locks").doc(TEACHER_UID), {
    userId: TEACHER_UID,
    schoolId: "school_1",
    createdAt: now,
  });
  transferBatch.update(newPrincipalDb.collection("schools").doc("school_1"), {
    ownerId: TEACHER_UID,
    lastAcceptedPrincipalInviteCode: "PRN-777888",
    memberUids: [PRINCIPAL_UID, TEACHER_UID],
    staffUids: [PRINCIPAL_UID, TEACHER_UID],
    updatedAt: now,
  });
  transferBatch.set(newPrincipalDb.collection("schools").doc("school_1").collection("members").doc(TEACHER_UID), {
    schoolId: "school_1",
    schoolOwnerId: TEACHER_UID,
    userId: TEACHER_UID,
    role: "principal",
    status: "active",
    inviteCode: "PRN-777888",
    createdAt: now,
    updatedAt: now,
  });
  transferBatch.update(newPrincipalDb.collection("school_directory").doc("BL-100001"), {
    ownerId: TEACHER_UID,
    updatedAt: now,
  });
  await assertSucceeds(transferBatch.commit());

  // New Principal can now clean up old Principal's lock and member record
  await assertSucceeds(newPrincipalDb.collection("principal_locks").doc(PRINCIPAL_UID).delete());
  await assertSucceeds(newPrincipalDb.collection("schools").doc("school_1").collection("members").doc(PRINCIPAL_UID).delete());
});

test("Principal account & school deletion cleans up nested data and blocks outsider delete", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(PRINCIPAL_UID).set({
      userId: PRINCIPAL_UID,
      fullName: "Alice",
      email: "alice@school.com",
      phone: "123",
      accountIntent: "principal",
      activeSchoolId: "school_1",
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("principal_locks").doc(PRINCIPAL_UID).set({
      userId: PRINCIPAL_UID,
      schoolId: "school_1",
      createdAt: now,
    });
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
    await adminDb.collection("school_directory").doc("BL-100001").set({
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
    await adminDb.collection("schools").doc("school_1").collection("announcements").doc("ann_1").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      scope: "school",
      category: "Notice",
      title: "Notice 1",
      body: "Body",
      allowComments: true,
      likesCount: 0,
      publisherId: PRINCIPAL_UID,
      publisherName: "Alice",
      staffUids: [PRINCIPAL_UID],
      memberUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("announcements").doc("ann_1").collection("comments").doc("c1").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      announcementId: "ann_1",
      userId: PRINCIPAL_UID,
      authorName: "Alice",
      body: "Welcome",
      memberUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(PRINCIPAL_UID, { email: "alice@school.com", email_verified: true }).firestore();
  const outsiderDb = testEnv.authenticatedContext(OTHER_UID, { email: "dave@evil.com", email_verified: true }).firestore();

  // Outsider cannot delete school or lock
  await assertFails(outsiderDb.collection("schools").doc("school_1").delete());
  await assertFails(outsiderDb.collection("principal_locks").doc(PRINCIPAL_UID).delete());

  // Principal deletes nested comments, announcements, directory, school, lock, and user doc
  await assertSucceeds(aliceDb.collection("schools").doc("school_1").collection("announcements").doc("ann_1").collection("comments").doc("c1").delete());
  await assertSucceeds(aliceDb.collection("schools").doc("school_1").collection("announcements").doc("ann_1").delete());
  await assertSucceeds(aliceDb.collection("school_directory").doc("BL-100001").delete());
  await assertSucceeds(aliceDb.collection("schools").doc("school_1").delete());
  await assertSucceeds(aliceDb.collection("principal_locks").doc(PRINCIPAL_UID).delete());
  await assertSucceeds(aliceDb.collection("users").doc(PRINCIPAL_UID).delete());
});

test("Principal removes accepted teacher & approved student, immediately blocking further reads", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
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
      memberUids: [PRINCIPAL_UID, TEACHER_UID, STUDENT_UID],
      staffUids: [PRINCIPAL_UID, TEACHER_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("members").doc(TEACHER_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: TEACHER_UID,
      role: "teacher",
      status: "active",
      inviteCode: "TCH-111222",
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("teachers").doc(TEACHER_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: TEACHER_UID,
      fullName: "Bob Teacher",
      email: "bob@school.com",
      phone: "999",
      joiningDate: "01-01-2026",
      isActive: true,
      permissions: {
        viewStudents: true, editStudents: true, markAttendance: true, editAttendance: true,
        createTests: true, enterMarks: true, publishAnnouncements: true, viewReports: true,
      },
      staffUids: [PRINCIPAL_UID, TEACHER_UID],
      memberUids: [PRINCIPAL_UID, TEACHER_UID, STUDENT_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("members").doc(STUDENT_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: STUDENT_UID,
      role: "student",
      status: "active",
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("student_requests").doc(STUDENT_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      schoolName: "Beacon Light Academy",
      userId: STUDENT_UID,
      requestedRollNumber: "1",
      requestedFullName: "Carol Student",
      requestedFatherName: "John",
      requestedSessionName: "2026",
      requestedClassName: "8th",
      requestedSectionName: "A",
      status: "approved",
      staffUids: [PRINCIPAL_UID, TEACHER_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("students").doc("st_1").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      classId: "cls_1",
      sectionId: "sec_1",
      studentCode: "STU-1",
      rollNumber: "1",
      fullName: "Carol Student",
      fatherName: "John",
      gender: "Female",
      parentName: "John",
      parentContact: "111",
      address: "Addr",
      admissionDate: "01-01-2026",
      status: "active",
      linkedUserId: STUDENT_UID,
      staffUids: [PRINCIPAL_UID, TEACHER_UID],
      memberUids: [PRINCIPAL_UID, TEACHER_UID, STUDENT_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(PRINCIPAL_UID, { email: "alice@school.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(TEACHER_UID, { email: "bob@school.com", email_verified: true }).firestore();
  const carolDb = testEnv.authenticatedContext(STUDENT_UID, { email: "carol@school.com", email_verified: true }).firestore();

  // Before removal, both Bob and Carol can read students
  await assertSucceeds(bobDb.collection("schools").doc("school_1").collection("students").where("memberUids", "array-contains", TEACHER_UID).get());
  await assertSucceeds(carolDb.collection("schools").doc("school_1").collection("students").where("memberUids", "array-contains", STUDENT_UID).get());

  // Principal removes teacher Bob and approved student Carol
  await assertSucceeds(aliceDb.collection("schools").doc("school_1").collection("teachers").doc(TEACHER_UID).delete());
  await assertSucceeds(aliceDb.collection("schools").doc("school_1").collection("members").doc(TEACHER_UID).delete());
  await assertSucceeds(aliceDb.collection("schools").doc("school_1").collection("student_requests").doc(STUDENT_UID).delete());
  await assertSucceeds(aliceDb.collection("schools").doc("school_1").collection("members").doc(STUDENT_UID).delete());
  await assertSucceeds(
    aliceDb.collection("schools").doc("school_1").collection("students").doc("st_1").update({
      linkedUserId: null,
      staffUids: [PRINCIPAL_UID],
      memberUids: [PRINCIPAL_UID],
      updatedAt: now,
    })
  );

  // Immediately afterward, Bob and Carol cannot read school student record
  await assertFails(bobDb.collection("schools").doc("school_1").collection("students").doc("st_1").get());
  await assertFails(carolDb.collection("schools").doc("school_1").collection("students").doc("st_1").get());
});

test("Approved student can read ALL tests (draft, submitted, published) across classes read-only", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
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
    await adminDb.collection("schools").doc("school_1").collection("members").doc(STUDENT_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: STUDENT_UID,
      role: "student",
      status: "active",
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("tests").doc("test_sub").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      classId: "cls_10",
      sectionId: "sec_10b",
      name: "Mid Term",
      subject: "Chemistry",
      testDate: "10-11-2026",
      totalMarks: 100,
      passingMarks: 40,
      description: "Other class test",
      status: "submitted",
      staffUids: [PRINCIPAL_UID],
      memberUids: [PRINCIPAL_UID, STUDENT_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const carolDb = testEnv.authenticatedContext(STUDENT_UID, { email: "carol@school.com", email_verified: true }).firestore();

  // Single-field memberUids query succeeds and returns submitted/draft tests
  const snap = await assertSucceeds(
    carolDb.collection("schools").doc("school_1").collection("tests").where("memberUids", "array-contains", STUDENT_UID).get()
  );
  if (snap.docs.length !== 1) {
    throw new Error("Expected 1 submitted test readable by student");
  }

  // Write remains denied
  await assertFails(
    carolDb.collection("schools").doc("school_1").collection("tests").doc("test_sub").update({
      status: "published",
      updatedAt: now,
    })
  );
});

test("Super Admin (verified bilookhanbhakkar@gmail.com) reads platform analytics & visits; normal/unverified users denied", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(PRINCIPAL_UID).set({
      userId: PRINCIPAL_UID,
      fullName: "Alice Principal",
      email: "alice@school.com",
      phone: "111",
      accountIntent: "principal",
      createdAt: now,
      updatedAt: now,
    });
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
    await adminDb.collection("school_directory").doc("BL-100001").set({
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
    await adminDb.collection("schools").doc("school_1").collection("students").doc("st_1").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      classId: "cls_1",
      sectionId: "sec_1",
      studentCode: "STU-1",
      rollNumber: "1",
      fullName: "Carol Student",
      fatherName: "John",
      gender: "Female",
      parentName: "John",
      parentContact: "111",
      address: "Addr",
      admissionDate: "01-01-2026",
      status: "active",
      staffUids: [PRINCIPAL_UID],
      memberUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(PRINCIPAL_UID, { email: "alice@school.com", email_verified: true }).firestore();
  const spoofAdminDb = testEnv.authenticatedContext("spoof_admin", { email: "bilookhanbhakkar@gmail.com", email_verified: false }).firestore();
  const superAdminDb = testEnv.authenticatedContext("super_admin_uid", { email: "BilooKhanBhakkar@gmail.com", email_verified: true }).firestore();

  // Normal user can log their own app visit
  await assertSucceeds(
    aliceDb.collection("app_visits").doc("vis_1").set({
      userId: PRINCIPAL_UID,
      userEmail: "alice@school.com",
      userName: "Alice Principal",
      roleIntent: "principal",
      activeSchoolId: "school_1",
      activeSchoolName: "Beacon Light Academy",
      dayKey: "15-11-2026",
      monthKey: "11-2026",
      dateSortKey: "2026-11-15",
      hourBucket: 14,
      createdAt: now,
    })
  );

  // Normal user cannot spoof another userId in app_visits or read app_visits
  await assertFails(
    aliceDb.collection("app_visits").doc("vis_spoof").set({
      userId: OTHER_UID,
      userEmail: "alice@school.com",
      userName: "Spoof",
      roleIntent: "principal",
      dayKey: "15-11-2026",
      monthKey: "11-2026",
      dateSortKey: "2026-11-15",
      hourBucket: 14,
      createdAt: now,
    })
  );
  await assertFails(aliceDb.collection("app_visits").get());
  await assertFails(aliceDb.collection("users").get());

  // Unverified email spoofing bilookhanbhakkar@gmail.com is DENIED
  await assertFails(spoofAdminDb.collection("app_visits").get());
  await assertFails(spoofAdminDb.collection("schools").get());
  await assertFails(spoofAdminDb.collection("users").get());

  // Verified bilookhanbhakkar@gmail.com CAN read visits, users, schools, students, and toggle directory visibility
  const visitsSnap = await assertSucceeds(superAdminDb.collection("app_visits").get());
  if (visitsSnap.docs.length !== 1) throw new Error("Expected 1 app visit visible to Super Admin");

  const usersSnap = await assertSucceeds(superAdminDb.collection("users").get());
  if (usersSnap.docs.length !== 1) throw new Error("Expected 1 user visible to Super Admin");

  const schoolsSnap = await assertSucceeds(superAdminDb.collection("schools").get());
  if (schoolsSnap.docs.length !== 1) throw new Error("Expected 1 school visible to Super Admin");

  const studentsSnap = await assertSucceeds(superAdminDb.collection("schools").doc("school_1").collection("students").get());
  if (studentsSnap.docs.length !== 1) throw new Error("Expected 1 student visible to Super Admin");

  await assertSucceeds(
    superAdminDb.collection("school_directory").doc("BL-100001").update({
      isPublic: false,
      updatedAt: now,
    })
  );
});

test("Approved student reads full school detail collections (incl. homework, timetables, teacher_assignments, attendance) without memberUids, writes strictly denied", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
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
    await adminDb.collection("schools").doc("school_1").collection("members").doc(STUDENT_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: STUDENT_UID,
      role: "student",
      status: "active",
      createdAt: now,
      updatedAt: now,
    });
    // Intentionally omit STUDENT_UID from memberUids on academic docs to prove isSchoolMember(schoolId) works school-wide
    await adminDb.collection("schools").doc("school_1").collection("teacher_assignments").doc("ta_1").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      teacherProfileId: TEACHER_UID,
      sectionId: "sec_1",
      subject: "Physics",
      staffUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("homework").doc("hw_1").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      sectionId: "sec_1",
      subject: "Physics",
      title: "Chapter 3 Numericals",
      instructions: "Solve Q1-Q5",
      dueDate: "20-11-2026",
      teacherId: TEACHER_UID,
      teacherName: "Mr. Bob",
      staffUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("timetables").doc("tt_1").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      sectionId: "sec_1",
      dayOfWeek: "Monday",
      subject: "Physics",
      startTime: "09:00",
      endTime: "09:45",
      teacherName: "Mr. Bob",
      roomNumber: "204",
      staffUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("attendance_sessions").doc("as_1").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      classId: "cls_1",
      sectionId: "sec_1",
      attendanceDate: "15-11-2026",
      subject: "Physics",
      sessionSlot: "Lecture 1",
      teacherId: TEACHER_UID,
      teacherName: "Mr. Bob",
      staffUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("attendance_records").doc("ar_1").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      attendanceSessionId: "as_1",
      studentId: "st_1",
      status: "absent",
      note: "Sick",
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
      studentCode: "STU-101",
      rollNumber: "1",
      fullName: "Zain Ali",
      fatherName: "Ali",
      gender: "Male",
      parentName: "Ali",
      parentContact: "0300",
      address: "Lahore",
      admissionDate: "01-04-2026",
      status: "active",
      staffUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("tests").doc("t_submitted").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      classId: "cls_1",
      sectionId: "sec_1",
      name: "Physics Quiz",
      subject: "Physics",
      testDate: "15-11-2026",
      totalMarks: 50,
      passingMarks: 20,
      description: "Quiz",
      status: "submitted",
      staffUids: [PRINCIPAL_UID],
      memberUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("test_results").doc("tr_1").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      testId: "t_submitted",
      studentId: "st_1",
      obtainedMarks: 42,
      percentage: 84,
      grade: "A",
      passed: true,
      rank: 1,
      staffUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const carolDb = testEnv.authenticatedContext(STUDENT_UID, { email: "carol@school.com", email_verified: true }).firestore();

  // Approved student reads ALL collections using where("schoolId", "==", "school_1")
  for (const col of ["teacher_assignments", "homework", "timetables", "attendance_sessions", "attendance_records", "students", "tests", "test_results"]) {
    const snap = await assertSucceeds(
      carolDb.collection("schools").doc("school_1").collection(col).where("schoolId", "==", "school_1").get()
    );
    if (snap.docs.length !== 1) {
      throw new Error(`Expected 1 doc in ${col} for student`);
    }
  }

  // Student CANNOT write/edit/delete ANY academic document
  await assertFails(
    carolDb.collection("schools").doc("school_1").collection("homework").doc("hw_1").update({ title: "Tampered", updatedAt: now })
  );
  await assertFails(
    carolDb.collection("schools").doc("school_1").collection("homework").doc("hw_new").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      sectionId: "sec_1",
      subject: "Physics",
      title: "Fake HW",
      instructions: "Fake",
      dueDate: "20-11-2026",
      teacherName: "Carol",
      staffUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    carolDb.collection("schools").doc("school_1").collection("timetables").doc("tt_1").delete()
  );
  await assertFails(
    carolDb.collection("schools").doc("school_1").collection("attendance_records").doc("ar_1").update({ status: "present", updatedAt: now })
  );
  await assertFails(
    carolDb.collection("schools").doc("school_1").collection("attendance_sessions").doc("as_1").update({ subject: "Fake", updatedAt: now })
  );
  await assertFails(
    carolDb.collection("schools").doc("school_1").collection("classes").doc("cls_hack").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      name: "Hack",
      description: "",
      staffUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Principal and active Teacher can update school details, logo, banner & gallery; Student and Outsider denied", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("schools").doc("school_1").set({
      schoolCode: "BL-100001",
      ownerId: PRINCIPAL_UID,
      name: "Beacon Light Academy",
      schoolType: "Private",
      address: "123 Rd",
      city: "Lahore",
      contactNumber: "123456",
      email: "info@beacon.edu",
      description: "Initial desc",
      galleryUrls: [],
      timezone: "UTC",
      memberUids: [PRINCIPAL_UID, TEACHER_UID, STUDENT_UID],
      staffUids: [PRINCIPAL_UID, TEACHER_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("school_directory").doc("BL-100001").set({
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
    await adminDb.collection("schools").doc("school_1").collection("members").doc(TEACHER_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: TEACHER_UID,
      role: "teacher",
      status: "active",
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("members").doc(STUDENT_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: STUDENT_UID,
      role: "student",
      status: "active",
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(PRINCIPAL_UID, { email: "alice@school.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(TEACHER_UID, { email: "bob@school.com", email_verified: true }).firestore();
  const carolDb = testEnv.authenticatedContext(STUDENT_UID, { email: "carol@school.com", email_verified: true }).firestore();
  const daveDb = testEnv.authenticatedContext(OTHER_UID, { email: "dave@evil.com", email_verified: true }).firestore();

  // Principal updates school name, logo, banner & directory
  const pBatch = aliceDb.batch();
  pBatch.update(aliceDb.collection("schools").doc("school_1"), {
    name: "Beacon International",
    schoolType: "Government",
    city: "Karachi",
    logoUrl: "data:image/jpeg;base64,LOGO1",
    bannerUrl: "data:image/jpeg;base64,BANNER1",
    galleryUrls: ["data:image/jpeg;base64,GAL1"],
    updatedAt: now,
  });
  pBatch.update(aliceDb.collection("school_directory").doc("BL-100001"), {
    name: "Beacon International",
    schoolType: "Government",
    city: "Karachi",
    updatedAt: now,
  });
  await assertSucceeds(pBatch.commit());

  // Active Teacher updates school name, logo, banner, gallery & directory
  const tBatch = bobDb.batch();
  tBatch.update(bobDb.collection("schools").doc("school_1"), {
    name: "Beacon Model High",
    schoolType: "Private",
    city: "Islamabad",
    description: "Edited by teacher",
    logoUrl: "data:image/jpeg;base64,LOGO2",
    bannerUrl: "data:image/jpeg;base64,BANNER2",
    galleryUrls: ["data:image/jpeg;base64,GAL1", "data:image/jpeg;base64,GAL2"],
    updatedAt: now,
  });
  tBatch.update(bobDb.collection("school_directory").doc("BL-100001"), {
    name: "Beacon Model High",
    schoolType: "Private",
    city: "Islamabad",
    updatedAt: now,
  });
  await assertSucceeds(tBatch.commit());

  // Teacher CANNOT change ownerId
  await assertFails(
    bobDb.collection("schools").doc("school_1").update({
      ownerId: TEACHER_UID,
      updatedAt: now,
    })
  );

  // Student and Outsider CANNOT update school or directory
  await assertFails(
    carolDb.collection("schools").doc("school_1").update({
      name: "Hacked by Student",
      updatedAt: now,
    })
  );
  await assertFails(
    carolDb.collection("school_directory").doc("BL-100001").update({
      name: "Hacked by Student",
      updatedAt: now,
    })
  );
  await assertFails(
    daveDb.collection("schools").doc("school_1").update({
      name: "Hacked by Outsider",
      updatedAt: now,
    })
  );
});

test("Principal generates open shareable teacher invite link (blank email) and invited teacher redeems by code", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
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
      isPublic: true,
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
    editStudents: true,
    markAttendance: true,
    editAttendance: false,
    createTests: true,
    enterMarks: true,
    publishAnnouncements: true,
    viewReports: true,
  };

  // Principal creates open invite link without knowing teacher email
  await assertSucceeds(
    aliceDb.collection("teacher_invitations").doc("TCH-778899").set({
      schoolId: "school_1",
      schoolName: "Beacon Light Academy",
      schoolOwnerId: PRINCIPAL_UID,
      email: "",
      emailLower: "",
      fullName: "Invited Teacher",
      phone: "",
      inviteCode: "TCH-778899",
      permissions: perms,
      status: "pending",
      invitedBy: PRINCIPAL_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Any signed-in teacher who opens the shared invite link can fetch by code and accept atomically
  const bobDb = testEnv.authenticatedContext(TEACHER_UID, { email: "newteacher@gmail.com", email_verified: true }).firestore();
  await assertSucceeds(bobDb.collection("teacher_invitations").doc("TCH-778899").get());

  const acceptBatch = bobDb.batch();
  acceptBatch.update(bobDb.collection("teacher_invitations").doc("TCH-778899"), {
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
    inviteCode: "TCH-778899",
    createdAt: now,
    updatedAt: now,
  });
  acceptBatch.set(bobDb.collection("schools").doc("school_1").collection("teachers").doc(TEACHER_UID), {
    schoolId: "school_1",
    schoolOwnerId: PRINCIPAL_UID,
    userId: TEACHER_UID,
    fullName: "Bob Link Teacher",
    email: "newteacher@gmail.com",
    phone: "",
    joiningDate: "15-11-2026",
    isActive: true,
    permissions: perms,
    staffUids: [PRINCIPAL_UID, TEACHER_UID],
    memberUids: [PRINCIPAL_UID, TEACHER_UID],
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(acceptBatch.commit());
});

test("Student joins public school without approval; Principal Hide School toggle immediately blocks Students while keeping Principal & Teacher access", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
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
      isPublic: true,
      memberUids: [PRINCIPAL_UID, TEACHER_UID],
      staffUids: [PRINCIPAL_UID, TEACHER_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("school_directory").doc("BL-100001").set({
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
    await adminDb.collection("schools").doc("school_1").collection("members").doc(PRINCIPAL_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: PRINCIPAL_UID,
      role: "principal",
      status: "active",
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("members").doc(TEACHER_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: TEACHER_UID,
      role: "teacher",
      status: "active",
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("students").doc("st_1").set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      classId: "cls_1",
      sectionId: "sec_1",
      studentCode: "STU-101",
      rollNumber: "1",
      fullName: "Zain Ali",
      fatherName: "Ali",
      gender: "Male",
      parentName: "Ali",
      parentContact: "0300",
      address: "Lahore",
      admissionDate: "01-04-2026",
      status: "active",
      staffUids: [PRINCIPAL_UID, TEACHER_UID],
      memberUids: [PRINCIPAL_UID, TEACHER_UID, STUDENT_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(PRINCIPAL_UID, { email: "alice@school.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(TEACHER_UID, { email: "bob@school.com", email_verified: true }).firestore();
  const carolDb = testEnv.authenticatedContext(STUDENT_UID, { email: "carol@school.com", email_verified: true }).firestore();
  const daveDb = testEnv.authenticatedContext(OTHER_UID, { email: "dave@school.com", email_verified: true }).firestore();

  // Carol enters public school WITHOUT any Principal approval
  await assertSucceeds(
    carolDb.collection("schools").doc("school_1").collection("members").doc(STUDENT_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: STUDENT_UID,
      role: "student",
      status: "active",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(carolDb.collection("schools").doc("school_1").get());
  await assertSucceeds(carolDb.collection("schools").doc("school_1").collection("students").where("schoolId", "==", "school_1").get());

  // Teacher CANNOT hide school (Principal-only action)
  await assertFails(
    bobDb.collection("schools").doc("school_1").update({ isPublic: false, updatedAt: now })
  );

  // Principal toggles Hide School (isPublic = false on schools & school_directory)
  const hideBatch = aliceDb.batch();
  hideBatch.update(aliceDb.collection("schools").doc("school_1"), { isPublic: false, updatedAt: now });
  hideBatch.update(aliceDb.collection("school_directory").doc("BL-100001"), { isPublic: false, updatedAt: now });
  await assertSucceeds(hideBatch.commit());

  // Existing student Carol immediately loses ALL read access & Dave cannot join hidden school
  await assertFails(carolDb.collection("schools").doc("school_1").get());
  await assertFails(carolDb.collection("schools").doc("school_1").collection("students").where("schoolId", "==", "school_1").get());
  await assertFails(carolDb.collection("schools").doc("school_1").collection("students").doc("st_1").get());
  await assertFails(
    daveDb.collection("schools").doc("school_1").collection("members").doc(OTHER_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: OTHER_UID,
      role: "student",
      status: "active",
      createdAt: now,
      updatedAt: now,
    })
  );
  // Public directory query returns 0 hidden schools
  const dirSnap = await assertSucceeds(daveDb.collection("school_directory").where("isPublic", "==", true).get());
  if (dirSnap.docs.length !== 0) throw new Error("Hidden school must not appear in public directory");

  // Principal and Teacher STILL retain full access while school is hidden
  await assertSucceeds(aliceDb.collection("schools").doc("school_1").collection("students").where("schoolId", "==", "school_1").get());
  await assertSucceeds(bobDb.collection("schools").doc("school_1").get());
  await assertSucceeds(bobDb.collection("schools").doc("school_1").collection("students").where("schoolId", "==", "school_1").get());

  // Principal makes school public again -> Carol regains read access
  const unhideBatch = aliceDb.batch();
  unhideBatch.update(aliceDb.collection("schools").doc("school_1"), { isPublic: true, updatedAt: now });
  unhideBatch.update(aliceDb.collection("school_directory").doc("BL-100001"), { isPublic: true, updatedAt: now });
  await assertSucceeds(unhideBatch.commit());

  await assertSucceeds(carolDb.collection("schools").doc("school_1").collection("students").where("schoolId", "==", "school_1").get());
});

test("Permanent Student Account Lock & Pinned Schools (max 3): Student CANNOT become Teacher/Principal", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      fullName: "Carol Student",
      email: "carol@gmail.com",
      phone: "",
      accountIntent: "student",
      createdAt: now,
      updatedAt: now,
    });
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
      isPublic: true,
      memberUids: [PRINCIPAL_UID, STUDENT_UID],
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
    await adminDb.collection("schools").doc("school_1").collection("members").doc(STUDENT_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: STUDENT_UID,
      role: "student",
      status: "active",
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(PRINCIPAL_UID, { email: "alice@school.com", email_verified: true }).firestore();
  const carolDb = testEnv.authenticatedContext(STUDENT_UID, { email: "carol@gmail.com", email_verified: true }).firestore();

  // Student CAN pin up to 3 schools
  await assertSucceeds(
    carolDb.collection("users").doc(STUDENT_UID).update({
      pinnedSchoolIds: ["school_1", "school_2", "school_3"],
      activeSchoolId: "school_1",
      updatedAt: now,
    })
  );

  // Student CANNOT pin a 4th school
  await assertFails(
    carolDb.collection("users").doc(STUDENT_UID).update({
      pinnedSchoolIds: ["school_1", "school_2", "school_3", "school_4"],
      updatedAt: now,
    })
  );

  // Student CANNOT change accountIntent away from student
  await assertFails(
    carolDb.collection("users").doc(STUDENT_UID).update({
      accountIntent: "teacher",
      updatedAt: now,
    })
  );
  await assertFails(
    carolDb.collection("users").doc(STUDENT_UID).update({
      accountIntent: "principal",
      updatedAt: now,
    })
  );

  // Student CANNOT create a principal lock / school
  await assertFails(
    carolDb.collection("principal_locks").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      schoolId: "school_fake",
      createdAt: now,
    })
  );

  const perms = {
    viewStudents: true,
    editStudents: true,
    markAttendance: true,
    editAttendance: false,
    createTests: true,
    enterMarks: true,
    publishAnnouncements: true,
    viewReports: true,
  };

  await assertSucceeds(
    aliceDb.collection("teacher_invitations").doc("TCH-556677").set({
      schoolId: "school_1",
      schoolName: "Beacon Light Academy",
      schoolOwnerId: PRINCIPAL_UID,
      email: "",
      emailLower: "",
      fullName: "Invited Teacher",
      phone: "",
      inviteCode: "TCH-556677",
      permissions: perms,
      status: "pending",
      invitedBy: PRINCIPAL_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Student CANNOT accept teacher invitation even atomically in a batch
  const blockedBatch = carolDb.batch();
  blockedBatch.update(carolDb.collection("teacher_invitations").doc("TCH-556677"), {
    status: "accepted",
    acceptedByUid: STUDENT_UID,
    updatedAt: now,
  });
  blockedBatch.update(carolDb.collection("schools").doc("school_1").collection("members").doc(STUDENT_UID), {
    schoolOwnerId: PRINCIPAL_UID,
    role: "teacher",
    status: "active",
    inviteCode: "TCH-556677",
    updatedAt: now,
  });
  blockedBatch.set(carolDb.collection("schools").doc("school_1").collection("teachers").doc(STUDENT_UID), {
    schoolId: "school_1",
    schoolOwnerId: PRINCIPAL_UID,
    userId: STUDENT_UID,
    fullName: "Carol Blocked",
    email: "carol@gmail.com",
    phone: "",
    joiningDate: "16-11-2026",
    isActive: true,
    permissions: perms,
    staffUids: [PRINCIPAL_UID, STUDENT_UID],
    memberUids: [PRINCIPAL_UID, STUDENT_UID],
    createdAt: now,
    updatedAt: now,
  });
  await assertFails(blockedBatch.commit());
});

test("Class Fees: Principal and Teacher can create fee details & mark students Paid/Unpaid; Student read-only; Outsider denied", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
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
      isPublic: true,
      memberUids: [PRINCIPAL_UID, TEACHER_UID, STUDENT_UID],
      staffUids: [PRINCIPAL_UID, TEACHER_UID],
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
    await adminDb.collection("schools").doc("school_1").collection("members").doc(TEACHER_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: TEACHER_UID,
      role: "teacher",
      status: "active",
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("members").doc(STUDENT_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: STUDENT_UID,
      role: "student",
      status: "active",
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(PRINCIPAL_UID, { email: "alice@school.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(TEACHER_UID, { email: "bob@school.com", email_verified: true }).firestore();
  const carolDb = testEnv.authenticatedContext(STUDENT_UID, { email: "carol@school.com", email_verified: true }).firestore();
  const daveDb = testEnv.authenticatedContext(OTHER_UID, { email: "dave@evil.com", email_verified: true }).firestore();

  const feeRef = bobDb.collection("schools").doc("school_1").collection("class_fees").doc("fee_annual_1");

  // Teacher creates Annual Function Fee for class/section
  await assertSucceeds(
    feeRef.set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      sessionId: "sess_1",
      classId: "cls_8",
      sectionId: "sec_8a",
      title: "Annual Function Fee",
      description: "Stage & Refreshment Contribution",
      amountText: "Rs. 500",
      feeDate: "17-11-2026",
      paidStudentIds: ["stu_1"],
      paidDetails: { stu_1: "Paid • 17-11-2026" },
      createdBy: TEACHER_UID,
      createdByName: "Bob Teacher",
      updatedByName: "Bob Teacher",
      staffUids: [PRINCIPAL_UID, TEACHER_UID],
      memberUids: [PRINCIPAL_UID, TEACHER_UID, STUDENT_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Principal updates title and adds stu_2 as Paid (stu_3 stays unpaid)
  await assertSucceeds(
    aliceDb.collection("schools").doc("school_1").collection("class_fees").doc("fee_annual_1").update({
      title: "Annual Function Fee 2026",
      paidStudentIds: ["stu_1", "stu_2"],
      paidDetails: { stu_1: "Paid • 17-11-2026", stu_2: "Paid • 17-11-2026" },
      updatedByName: "Alice Principal",
      updatedAt: now,
    })
  );

  // Public student can read class fees read-only, CANNOT modify or delete
  await assertSucceeds(
    carolDb.collection("schools").doc("school_1").collection("class_fees").where("schoolId", "==", "school_1").get()
  );
  await assertFails(
    carolDb.collection("schools").doc("school_1").collection("class_fees").doc("fee_annual_1").update({
      paidStudentIds: ["stu_1", "stu_2", "stu_3"],
      updatedAt: now,
    })
  );
  await assertFails(
    carolDb.collection("schools").doc("school_1").collection("class_fees").doc("fee_annual_1").delete()
  );

  // Outsider cannot read or write class fees
  await assertFails(
    daveDb.collection("schools").doc("school_1").collection("class_fees").doc("fee_annual_1").get()
  );
  await assertFails(
    daveDb.collection("schools").doc("school_1").collection("class_fees").where("schoolId", "==", "school_1").get()
  );
});

test("teacher_credentials lifecycle: Principal generation, duplicate block, teacher acceptance, student blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(PRINCIPAL_UID).firestore();
  const bobDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const carolDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const daveDb = testEnv.authenticatedContext(OTHER_UID).firestore();

  const now = new Date();
  const perms = {
    viewStudents: true,
    editStudents: true,
    markAttendance: true,
    editAttendance: true,
    createTests: true,
    enterMarks: true,
    publishAnnouncements: true,
    viewReports: true,
  };

  const username = "ali.science";
  const usernameLower = "ali.science";
  const inviteCode = "TCH-990011";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("schools").doc("school_1").set({
      schoolId: "school_1",
      ownerId: PRINCIPAL_UID,
      name: "Beacon Light Academy",
      schoolCode: "BL-1234",
      schoolType: "Private",
      address: "123 Main St",
      city: "Lahore",
      contactNumber: "03001234567",
      email: "info@beacon.edu",
      description: "Model School",
      establishedYear: 2020,
      galleryUrls: [],
      timezone: "UTC",
      isPublic: true,
      memberUids: [PRINCIPAL_UID],
      staffUids: [PRINCIPAL_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  // Principal atomically creates teacher_invitation and teacher_credentials
  const createBatch = aliceDb.batch();
  createBatch.set(aliceDb.collection("teacher_invitations").doc(inviteCode), {
    schoolId: "school_1",
    schoolName: "Beacon Light Academy",
    schoolOwnerId: PRINCIPAL_UID,
    email: "ali@school.com",
    emailLower: "ali@school.com",
    fullName: "Ali Raza",
    phone: "03001234567",
    inviteCode: inviteCode,
    permissions: perms,
    status: "pending",
    invitedBy: PRINCIPAL_UID,
    authType: "credentials",
    username: username,
    usernameLower: usernameLower,
    accessPassword: "Pass1234",
    createdAt: now,
    updatedAt: now,
  });
  createBatch.set(aliceDb.collection("teacher_credentials").doc(usernameLower), {
    username: username,
    usernameLower: usernameLower,
    accessPassword: "Pass1234",
    inviteCode: inviteCode,
    schoolId: "school_1",
    schoolName: "Beacon Light Academy",
    schoolOwnerId: PRINCIPAL_UID,
    status: "pending",
    invitedBy: PRINCIPAL_UID,
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(createBatch.commit());

  // Duplicate username cannot be created
  await assertFails(
    aliceDb.collection("teacher_credentials").doc(usernameLower).set({
      username: username,
      usernameLower: usernameLower,
      accessPassword: "DifferentPass",
      inviteCode: "TCH-990022",
      schoolId: "school_1",
      schoolName: "Beacon Light Academy",
      schoolOwnerId: PRINCIPAL_UID,
      status: "pending",
      invitedBy: PRINCIPAL_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Any authenticated user can get credential doc to verify
  const credSnap = await assertSucceeds(daveDb.collection("teacher_credentials").doc(usernameLower).get());
  assert.equal(credSnap.data().accessPassword, "Pass1234");
  assert.equal(credSnap.data().status, "pending");

  // Student (carol) CANNOT accept teacher credentials
  const studentBlockedBatch = carolDb.batch();
  studentBlockedBatch.update(carolDb.collection("teacher_credentials").doc(usernameLower), {
    status: "accepted",
    acceptedByUid: STUDENT_UID,
    updatedAt: now,
  });
  studentBlockedBatch.update(carolDb.collection("schools").doc("school_1").collection("members").doc(STUDENT_UID), {
    schoolOwnerId: PRINCIPAL_UID,
    role: "teacher",
    status: "active",
    inviteCode: inviteCode,
    updatedAt: now,
  });
  await assertFails(studentBlockedBatch.commit());

  // Valid non-student user (bob) can accept teacher credentials atomically
  const bobAcceptBatch = bobDb.batch();
  bobAcceptBatch.update(bobDb.collection("teacher_invitations").doc(inviteCode), {
    status: "accepted",
    acceptedByUid: TEACHER_UID,
    updatedAt: now,
  });
  bobAcceptBatch.update(bobDb.collection("teacher_credentials").doc(usernameLower), {
    status: "accepted",
    acceptedByUid: TEACHER_UID,
    updatedAt: now,
  });
  bobAcceptBatch.set(bobDb.collection("schools").doc("school_1").collection("members").doc(TEACHER_UID), {
    schoolId: "school_1",
    schoolOwnerId: PRINCIPAL_UID,
    userId: TEACHER_UID,
    role: "teacher",
    status: "active",
    inviteCode: inviteCode,
    createdAt: now,
    updatedAt: now,
  });
  bobAcceptBatch.set(bobDb.collection("schools").doc("school_1").collection("teachers").doc(TEACHER_UID), {
    schoolId: "school_1",
    schoolOwnerId: PRINCIPAL_UID,
    userId: TEACHER_UID,
    fullName: "Sir Ali Raza",
    email: "ali@school.com",
    phone: "03001234567",
    joiningDate: "16-11-2026",
    isActive: true,
    permissions: perms,
    staffUids: [PRINCIPAL_UID, TEACHER_UID],
    memberUids: [PRINCIPAL_UID, TEACHER_UID],
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(bobAcceptBatch.commit());

  // Principal can delete the teacher_credentials
  await assertSucceeds(aliceDb.collection("teacher_credentials").doc(usernameLower).delete());
});

test("Student and Teacher self-deletion for Play Store compliance (with strict cross-user & escalation protection)", async () => {
  const now = new Date();
  const perms = {
    viewStudents: true, editStudents: true, markAttendance: true, editAttendance: true,
    createTests: true, enterMarks: true, publishAnnouncements: true, viewReports: true,
  };
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(TEACHER_UID).set({
      userId: TEACHER_UID,
      fullName: "Bob Teacher",
      email: "bob@school.com",
      phone: "03001112222",
      accountIntent: "teacher",
      activeSchoolId: "school_1",
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("users").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      fullName: "Carol Student",
      email: "carol@school.com",
      phone: "",
      accountIntent: "student",
      activeSchoolId: "school_1",
      pinnedSchoolIds: ["school_1"],
      createdAt: now,
      updatedAt: now,
    });
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
      isPublic: true,
      memberUids: [PRINCIPAL_UID, TEACHER_UID],
      staffUids: [PRINCIPAL_UID, TEACHER_UID],
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
    await adminDb.collection("schools").doc("school_1").collection("members").doc(TEACHER_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: TEACHER_UID,
      role: "teacher",
      status: "active",
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("teachers").doc(TEACHER_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: TEACHER_UID,
      fullName: "Bob Teacher",
      email: "bob@school.com",
      phone: "03001112222",
      joiningDate: "16-11-2026",
      isActive: true,
      permissions: perms,
      staffUids: [PRINCIPAL_UID, TEACHER_UID],
      memberUids: [PRINCIPAL_UID, TEACHER_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("members").doc(STUDENT_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      userId: STUDENT_UID,
      role: "student",
      status: "active",
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("schools").doc("school_1").collection("student_requests").doc(STUDENT_UID).set({
      schoolId: "school_1",
      schoolOwnerId: PRINCIPAL_UID,
      schoolName: "Beacon Light Academy",
      userId: STUDENT_UID,
      requestedRollNumber: "1",
      requestedFullName: "Carol Student",
      requestedFatherName: "John",
      requestedSessionName: "2026",
      requestedClassName: "8th",
      requestedSectionName: "A",
      status: "pending",
      staffUids: [PRINCIPAL_UID, TEACHER_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(TEACHER_UID, { email: "bob@school.com", email_verified: true }).firestore();
  const carolDb = testEnv.authenticatedContext(STUDENT_UID, { email: "carol@school.com", email_verified: true }).firestore();

  // Student CANNOT delete Teacher's member doc, Principal's member doc, or modify school doc
  await assertFails(carolDb.collection("schools").doc("school_1").collection("members").doc(TEACHER_UID).delete());
  await assertFails(carolDb.collection("schools").doc("school_1").collection("members").doc(PRINCIPAL_UID).delete());
  await assertFails(carolDb.collection("schools").doc("school_1").update({ memberUids: [PRINCIPAL_UID], updatedAt: now }));

  // Student CAN delete their own student_requests, student membership, and user profile
  await assertSucceeds(carolDb.collection("schools").doc("school_1").collection("student_requests").doc(STUDENT_UID).delete());
  await assertSucceeds(carolDb.collection("schools").doc("school_1").collection("members").doc(STUDENT_UID).delete());
  await assertSucceeds(carolDb.collection("users").doc(STUDENT_UID).delete());

  // Teacher CANNOT remove Principal from school memberUids/staffUids
  await assertFails(
    bobDb.collection("schools").doc("school_1").update({
      memberUids: [],
      staffUids: [],
      updatedAt: now,
    })
  );
  // Teacher CANNOT delete Principal's member doc
  await assertFails(bobDb.collection("schools").doc("school_1").collection("members").doc(PRINCIPAL_UID).delete());

  // Teacher CAN remove ONLY themselves from school memberUids/staffUids, then delete teachers/{uid}, members/{uid}, users/{uid}
  await assertSucceeds(
    bobDb.collection("schools").doc("school_1").update({
      memberUids: [PRINCIPAL_UID],
      staffUids: [PRINCIPAL_UID],
      updatedAt: now,
    })
  );
  await assertSucceeds(bobDb.collection("schools").doc("school_1").collection("teachers").doc(TEACHER_UID).delete());
  await assertSucceeds(bobDb.collection("schools").doc("school_1").collection("members").doc(TEACHER_UID).delete());
  await assertSucceeds(bobDb.collection("users").doc(TEACHER_UID).delete());
});






