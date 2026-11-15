-- ============================================================================
-- BL School Hub - Complete PostgreSQL Schema, RPC Functions & RLS Policies
-- Run this script in your Supabase Project -> SQL Editor.
-- ============================================================================

create extension if not exists "pgcrypto";

-- 1. USERS PROFILE TABLE (Synced from auth.users)
create table if not exists public.users (
    id uuid primary key references auth.users(id) on delete cascade,
    full_name text not null default '',
    email text not null default '',
    phone text not null default '',
    avatar_url text,
    account_intent text not null default 'student' check (account_intent in ('principal', 'teacher', 'student')),
    created_at timestamptz not null default now()
);

-- 2. SCHOOLS (Strictly 1 school per Principal owner_id)
create table if not exists public.schools (
    id uuid primary key default gen_random_uuid(),
    school_code text not null unique default ('BL-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6))),
    owner_id uuid not null unique references public.users(id) on delete restrict,
    name text not null,
    school_type text not null default 'Private' check (school_type in ('Private', 'Government', 'Other')),
    address text not null default '',
    city text not null default '',
    contact_number text not null default '',
    email text not null default '',
    description text not null default '',
    established_year int,
    website text,
    logo_url text,
    banner_url text,
    gallery_urls jsonb not null default '[]'::jsonb,
    timezone text not null default 'UTC',
    created_at timestamptz not null default now()
);

-- 3. SCHOOL MEMBERSHIPS (Authoritative RBAC table)
create table if not exists public.school_memberships (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    user_id uuid not null references public.users(id) on delete cascade,
    role text not null check (role in ('principal', 'teacher', 'student')),
    status text not null default 'active' check (status in ('active', 'inactive', 'pending')),
    created_at timestamptz not null default now(),
    unique (school_id, user_id)
);

-- 4. TEACHER PROFILES & INVITATIONS
create table if not exists public.teacher_profiles (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    user_id uuid not null references public.users(id) on delete cascade,
    full_name text not null,
    email text not null,
    phone text not null default '',
    avatar_url text,
    joining_date date not null default current_date,
    is_active boolean not null default true,
    permissions jsonb not null default '{
      "view_students": true,
      "edit_students": false,
      "mark_attendance": true,
      "edit_attendance": false,
      "create_tests": true,
      "enter_marks": true,
      "publish_announcements": true,
      "view_reports": true
    }'::jsonb,
    created_at timestamptz not null default now(),
    unique (school_id, user_id)
);

create table if not exists public.teacher_invitations (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    email text not null,
    full_name text not null,
    phone text not null default '',
    invite_code text not null unique default ('TCH-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6))),
    permissions jsonb not null default '{
      "view_students": true,
      "edit_students": false,
      "mark_attendance": true,
      "edit_attendance": false,
      "create_tests": true,
      "enter_marks": true,
      "publish_announcements": true,
      "view_reports": true
    }'::jsonb,
    status text not null default 'pending' check (status in ('pending', 'accepted', 'revoked')),
    invited_by uuid not null references public.users(id),
    accepted_by uuid references public.users(id),
    created_at timestamptz not null default now()
);

-- 5. ACADEMIC SESSIONS, CLASSES & SECTIONS
create table if not exists public.academic_sessions (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    name text not null,
    start_date date,
    end_date date,
    is_current boolean not null default false,
    created_at timestamptz not null default now(),
    unique (school_id, name)
);

create table if not exists public.classes (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    session_id uuid not null references public.academic_sessions(id) on delete cascade,
    name text not null,
    description text not null default '',
    created_at timestamptz not null default now(),
    unique (school_id, session_id, name)
);

create table if not exists public.sections (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    session_id uuid not null references public.academic_sessions(id) on delete cascade,
    class_id uuid not null references public.classes(id) on delete cascade,
    name text not null,
    class_teacher_id uuid references public.teacher_profiles(id) on delete set null,
    description text not null default '',
    created_at timestamptz not null default now(),
    unique (class_id, name)
);

create table if not exists public.teacher_assignments (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    session_id uuid not null references public.academic_sessions(id) on delete cascade,
    teacher_profile_id uuid not null references public.teacher_profiles(id) on delete cascade,
    section_id uuid not null references public.sections(id) on delete cascade,
    subject text not null default 'General',
    created_at timestamptz not null default now(),
    unique (teacher_profile_id, section_id, subject)
);

-- 6. STUDENTS & VERIFIED STUDENT ACCOUNTS
create table if not exists public.students (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    session_id uuid not null references public.academic_sessions(id) on delete cascade,
    class_id uuid not null references public.classes(id) on delete cascade,
    section_id uuid not null references public.sections(id) on delete cascade,
    student_code text not null default ('STU-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6))),
    roll_number text not null,
    full_name text not null,
    father_name text not null,
    date_of_birth date,
    gender text not null default 'Male',
    parent_name text not null default '',
    parent_contact text not null default '',
    address text not null default '',
    admission_date date not null default current_date,
    email text,
    avatar_url text,
    status text not null default 'active' check (status in ('active', 'transferred', 'promoted', 'inactive')),
    previous_record_id uuid references public.students(id) on delete set null,
    created_at timestamptz not null default now(),
    unique (school_id, session_id, student_code),
    unique (section_id, roll_number)
);

create table if not exists public.student_accounts (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    user_id uuid not null references public.users(id) on delete cascade,
    student_id uuid references public.students(id) on delete set null,
    session_id uuid references public.academic_sessions(id) on delete set null,
    class_id uuid references public.classes(id) on delete set null,
    section_id uuid references public.sections(id) on delete set null,
    requested_roll_number text not null,
    requested_full_name text not null,
    requested_father_name text not null,
    requested_session_name text not null default '',
    requested_class_name text not null default '',
    requested_section_name text not null default '',
    status text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
    reviewed_by uuid references public.users(id),
    created_at timestamptz not null default now(),
    unique (school_id, user_id)
);

-- 7. ATTENDANCE (Multiple Sessions Per Day Supported)
create table if not exists public.attendance_sessions (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    session_id uuid not null references public.academic_sessions(id) on delete cascade,
    class_id uuid not null references public.classes(id) on delete cascade,
    section_id uuid not null references public.sections(id) on delete cascade,
    attendance_date date not null,
    subject text not null default 'General',
    session_slot text not null default 'Morning',
    teacher_id uuid references public.users(id),
    teacher_name text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (section_id, attendance_date, subject, session_slot)
);

create table if not exists public.attendance_records (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    attendance_session_id uuid not null references public.attendance_sessions(id) on delete cascade,
    student_id uuid not null references public.students(id) on delete cascade,
    status text not null check (status in ('present', 'absent', 'leave')),
    note text not null default '',
    updated_by uuid references public.users(id),
    edit_history jsonb not null default '[]'::jsonb,
    updated_at timestamptz not null default now(),
    unique (attendance_session_id, student_id)
);

-- 8. TESTS & RESULTS
create table if not exists public.tests (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    session_id uuid not null references public.academic_sessions(id) on delete cascade,
    class_id uuid not null references public.classes(id) on delete cascade,
    section_id uuid not null references public.sections(id) on delete cascade,
    name text not null,
    subject text not null,
    test_date date not null,
    total_marks double precision not null check (total_marks > 0),
    passing_marks double precision not null check (passing_marks >= 0),
    description text not null default '',
    status text not null default 'draft' check (status in ('draft', 'submitted', 'published')),
    created_by uuid references public.users(id),
    approved_by uuid references public.users(id),
    created_at timestamptz not null default now()
);

create table if not exists public.test_results (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    test_id uuid not null references public.tests(id) on delete cascade,
    student_id uuid not null references public.students(id) on delete cascade,
    obtained_marks double precision not null check (obtained_marks >= 0),
    percentage double precision not null,
    grade text not null,
    passed boolean not null,
    rank int,
    entered_by uuid references public.users(id),
    updated_at timestamptz not null default now(),
    unique (test_id, student_id)
);

-- 9. ANNOUNCEMENTS, POSTS, LIKES & COMMENTS
create table if not exists public.announcements (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    session_id uuid references public.academic_sessions(id) on delete cascade,
    class_id uuid references public.classes(id) on delete cascade,
    section_id uuid references public.sections(id) on delete cascade,
    scope text not null default 'school' check (scope in ('school', 'class')),
    category text not null default 'Announcement',
    title text not null,
    body text not null,
    image_url text,
    allow_comments boolean not null default true,
    likes_count int not null default 0,
    publisher_id uuid not null references public.users(id),
    publisher_name text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.announcement_likes (
    id uuid primary key default gen_random_uuid(),
    announcement_id uuid not null references public.announcements(id) on delete cascade,
    user_id uuid not null references public.users(id) on delete cascade,
    created_at timestamptz not null default now(),
    unique (announcement_id, user_id)
);

create table if not exists public.announcement_comments (
    id uuid primary key default gen_random_uuid(),
    announcement_id uuid not null references public.announcements(id) on delete cascade,
    user_id uuid not null references public.users(id) on delete cascade,
    author_name text not null default '',
    body text not null,
    created_at timestamptz not null default now()
);

-- 10. TIMETABLES, HOMEWORK & NOTIFICATIONS
create table if not exists public.timetables (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    session_id uuid not null references public.academic_sessions(id) on delete cascade,
    section_id uuid not null references public.sections(id) on delete cascade,
    day_of_week text not null check (day_of_week in ('Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday')),
    subject text not null,
    start_time text not null,
    end_time text not null,
    teacher_name text not null default '',
    room_number text not null default '',
    created_at timestamptz not null default now()
);

create table if not exists public.homework (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    session_id uuid not null references public.academic_sessions(id) on delete cascade,
    section_id uuid not null references public.sections(id) on delete cascade,
    subject text not null,
    title text not null,
    instructions text not null,
    due_date date not null,
    attachment_url text,
    teacher_id uuid references public.users(id),
    teacher_name text not null default '',
    created_at timestamptz not null default now()
);

create table if not exists public.notifications (
    id uuid primary key default gen_random_uuid(),
    school_id uuid not null references public.schools(id) on delete cascade,
    user_id uuid not null references public.users(id) on delete cascade,
    title text not null,
    message text not null,
    category text not null default 'notice',
    is_read boolean not null default false,
    reference_id uuid,
    created_at timestamptz not null default now()
);

-- ============================================================================
-- HELPER FUNCTIONS & SECURE RPCs (SECURITY DEFINER)
-- ============================================================================

create or replace function public.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.users (id, full_name, email, account_intent)
    values (
        new.id,
        coalesce(new.raw_user_meta_data->>'full_name', new.raw_user_meta_data->>'name', split_part(coalesce(new.email, ''), '@', 1)),
        coalesce(new.email, ''),
        coalesce(new.raw_user_meta_data->>'account_intent', 'student')
    )
    on conflict (id) do update
    set email = excluded.email,
        full_name = case when public.users.full_name = '' then excluded.full_name else public.users.full_name end;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_auth_user();

create or replace function public.is_school_principal(p_school_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.schools s
        where s.id = p_school_id and s.owner_id = auth.uid()
    );
$$;

create or replace function public.is_school_member(p_school_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.school_memberships m
        where m.school_id = p_school_id and m.user_id = auth.uid() and m.status = 'active'
    );
$$;

create or replace function public.is_school_teacher(p_school_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.school_memberships m
        join public.teacher_profiles tp on tp.school_id = m.school_id and tp.user_id = m.user_id
        where m.school_id = p_school_id and m.user_id = auth.uid() and m.role = 'teacher' and m.status = 'active' and tp.is_active = true
    );
$$;

create or replace function public.teacher_has_permission(p_school_id uuid, p_perm text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select public.is_school_principal(p_school_id) or exists (
        select 1 from public.teacher_profiles tp
        where tp.school_id = p_school_id
          and tp.user_id = auth.uid()
          and tp.is_active = true
          and coalesce((tp.permissions->>p_perm)::boolean, false) = true
    );
$$;

create or replace function public.teacher_assigned_section(p_section_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.sections sec
        where sec.id = p_section_id and public.is_school_principal(sec.school_id)
    ) or exists (
        select 1 from public.teacher_assignments ta
        join public.teacher_profiles tp on tp.id = ta.teacher_profile_id
        where ta.section_id = p_section_id and tp.user_id = auth.uid() and tp.is_active = true
    ) or exists (
        select 1 from public.sections sec
        join public.teacher_profiles tp on tp.id = sec.class_teacher_id
        where sec.id = p_section_id and tp.user_id = auth.uid() and tp.is_active = true
    );
$$;

-- Principal creates their ONLY school atomically
create or replace function public.create_school_with_principal(
    p_name text,
    p_school_type text,
    p_address text,
    p_city text,
    p_contact_number text,
    p_email text,
    p_description text,
    p_established_year int,
    p_website text,
    p_logo_url text,
    p_banner_url text
)
returns public.schools
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_school public.schools;
begin
    if v_uid is null then
        raise exception 'Authentication required';
    end if;

    if exists (select 1 from public.schools where owner_id = v_uid) then
        raise exception 'A Principal account can only create and own one school.';
    end if;

    if exists (select 1 from public.school_memberships where user_id = v_uid and role = 'teacher') then
        raise exception 'Teacher accounts cannot create a new school.';
    end if;

    insert into public.schools (
        owner_id, name, school_type, address, city, contact_number,
        email, description, established_year, website, logo_url, banner_url
    ) values (
        v_uid, p_name, p_school_type, p_address, p_city, p_contact_number,
        p_email, p_description, p_established_year, nullif(p_website, ''), p_logo_url, p_banner_url
    )
    returning * into v_school;

    insert into public.school_memberships (school_id, user_id, role, status)
    values (v_school.id, v_uid, 'principal', 'active');

    update public.users set account_intent = 'principal' where id = v_uid;

    return v_school;
end;
$$;

-- Teacher accepts an invite code issued by a Principal
create or replace function public.accept_teacher_invitation(p_invite_code text)
returns public.teacher_profiles
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_user_email text;
    v_user_name text;
    v_inv public.teacher_invitations;
    v_profile public.teacher_profiles;
begin
    if v_uid is null then
        raise exception 'Authentication required';
    end if;

    select email, full_name into v_user_email, v_user_name from public.users where id = v_uid;

    select * into v_inv
    from public.teacher_invitations
    where upper(invite_code) = upper(trim(p_invite_code))
      and status = 'pending'
    limit 1;

    if v_inv.id is null then
        raise exception 'Invalid or expired teacher invitation code.';
    end if;

    if lower(trim(v_inv.email)) <> lower(trim(coalesce(v_user_email, ''))) then
        raise exception 'This invitation was issued to %, please sign in with that email.', v_inv.email;
    end if;

    update public.teacher_invitations
    set status = 'accepted', accepted_by = v_uid
    where id = v_inv.id;

    insert into public.school_memberships (school_id, user_id, role, status)
    values (v_inv.school_id, v_uid, 'teacher', 'active')
    on conflict (school_id, user_id) do update set role = 'teacher', status = 'active';

    insert into public.teacher_profiles (
        school_id, user_id, full_name, email, phone, permissions, is_active
    ) values (
        v_inv.school_id, v_uid,
        case when coalesce(v_user_name, '') <> '' then v_user_name else v_inv.full_name end,
        v_inv.email, v_inv.phone, v_inv.permissions, true
    )
    on conflict (school_id, user_id) do update
    set permissions = v_inv.permissions, is_active = true
    returning * into v_profile;

    update public.users set account_intent = 'teacher' where id = v_uid;

    return v_profile;
end;
$$;

-- Student requests connection to a school
create or replace function public.request_student_connection(
    p_school_id uuid,
    p_roll_number text,
    p_full_name text,
    p_father_name text,
    p_session_name text,
    p_class_name text,
    p_section_name text
)
returns public.student_accounts
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_req public.student_accounts;
    v_owner_id uuid;
begin
    if v_uid is null then
        raise exception 'Authentication required';
    end if;

    select owner_id into v_owner_id from public.schools where id = p_school_id;
    if v_owner_id is null then
        raise exception 'School not found';
    end if;

    insert into public.student_accounts (
        school_id, user_id, requested_roll_number, requested_full_name,
        requested_father_name, requested_session_name, requested_class_name, requested_section_name, status
    ) values (
        p_school_id, v_uid, trim(p_roll_number), trim(p_full_name),
        trim(p_father_name), trim(p_session_name), trim(p_class_name), trim(p_section_name), 'pending'
    )
    on conflict (school_id, user_id) do update
    set requested_roll_number = excluded.requested_roll_number,
        requested_full_name = excluded.requested_full_name,
        requested_father_name = excluded.requested_father_name,
        requested_session_name = excluded.requested_session_name,
        requested_class_name = excluded.requested_class_name,
        requested_section_name = excluded.requested_section_name,
        status = 'pending'
    returning * into v_req;

    insert into public.notifications (school_id, user_id, title, message, category, reference_id)
    values (
        p_school_id, v_owner_id,
        'New Student Connection Request',
        p_full_name || ' (Roll #' || p_roll_number || ') requested to link their account.',
        'student_request',
        v_req.id
    );

    return v_req;
end;
$$;

-- Principal approves student connection and links to verified student record
create or replace function public.approve_student_connection(
    p_request_id uuid,
    p_student_id uuid
)
returns public.student_accounts
language plpgsql
security definer
set search_path = public
as $$
declare
    v_req public.student_accounts;
    v_stu public.students;
begin
    select * into v_req from public.student_accounts where id = p_request_id;
    if v_req.id is null then
        raise exception 'Request not found';
    end if;

    if not public.teacher_has_permission(v_req.school_id, 'edit_students') then
        raise exception 'Permission denied';
    end if;

    select * into v_stu from public.students where id = p_student_id and school_id = v_req.school_id;
    if v_stu.id is null then
        raise exception 'Student record not found in this school';
    end if;

    update public.student_accounts
    set student_id = v_stu.id,
        session_id = v_stu.session_id,
        class_id = v_stu.class_id,
        section_id = v_stu.section_id,
        status = 'approved',
        reviewed_by = auth.uid()
    where id = p_request_id
    returning * into v_req;

    insert into public.school_memberships (school_id, user_id, role, status)
    values (v_req.school_id, v_req.user_id, 'student', 'active')
    on conflict (school_id, user_id) do update set role = 'student', status = 'active';

    insert into public.notifications (school_id, user_id, title, message, category, reference_id)
    values (
        v_req.school_id, v_req.user_id,
        'School Connection Approved',
        'Your account is now linked to ' || v_stu.full_name || ' (Roll #' || v_stu.roll_number || ').',
        'approval',
        v_stu.id
    );

    return v_req;
end;
$$;

-- Toggle announcement like safely
create or replace function public.toggle_announcement_like(p_announcement_id uuid)
returns int
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_school_id uuid;
    v_count int;
begin
    select school_id into v_school_id from public.announcements where id = p_announcement_id;
    if v_school_id is null or not public.is_school_member(v_school_id) then
        raise exception 'Not authorized';
    end if;

    if exists (select 1 from public.announcement_likes where announcement_id = p_announcement_id and user_id = v_uid) then
        delete from public.announcement_likes where announcement_id = p_announcement_id and user_id = v_uid;
    else
        insert into public.announcement_likes (announcement_id, user_id) values (p_announcement_id, v_uid);
    end if;

    select count(*) into v_count from public.announcement_likes where announcement_id = p_announcement_id;
    update public.announcements set likes_count = v_count where id = p_announcement_id;
    return v_count;
end;
$$;

-- ============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- ============================================================================

alter table public.users enable row level security;
alter table public.schools enable row level security;
alter table public.school_memberships enable row level security;
alter table public.teacher_profiles enable row level security;
alter table public.teacher_invitations enable row level security;
alter table public.academic_sessions enable row level security;
alter table public.classes enable row level security;
alter table public.sections enable row level security;
alter table public.teacher_assignments enable row level security;
alter table public.students enable row level security;
alter table public.student_accounts enable row level security;
alter table public.attendance_sessions enable row level security;
alter table public.attendance_records enable row level security;
alter table public.tests enable row level security;
alter table public.test_results enable row level security;
alter table public.announcements enable row level security;
alter table public.announcement_likes enable row level security;
alter table public.announcement_comments enable row level security;
alter table public.timetables enable row level security;
alter table public.homework enable row level security;
alter table public.notifications enable row level security;

-- USERS
create policy "Users view own profile" on public.users for select using (id = auth.uid());
create policy "Users update own profile" on public.users for update using (id = auth.uid());
create policy "Users insert own profile" on public.users for insert with check (id = auth.uid());

-- SCHOOLS (Public discovery for authenticated users so students can search schools; only Principal edits)
create policy "Authenticated users can search schools" on public.schools for select to authenticated using (true);
create policy "Principal updates own school" on public.schools for update using (owner_id = auth.uid());

-- MEMBERSHIPS (Read-only for users; mutations only via SECURITY DEFINER RPCs or Principal deactivation)
create policy "Members read own or school memberships" on public.school_memberships
    for select using (user_id = auth.uid() or public.is_school_principal(school_id));
create policy "Principal updates memberships" on public.school_memberships
    for update using (public.is_school_principal(school_id));

-- TEACHER PROFILES & INVITATIONS
create policy "School members view active teachers" on public.teacher_profiles
    for select using (public.is_school_member(school_id));
create policy "Principal manages teachers" on public.teacher_profiles
    for all using (public.is_school_principal(school_id));

create policy "Principal manages invitations" on public.teacher_invitations
    for all using (public.is_school_principal(school_id));
create policy "Invited teacher can read own invitation" on public.teacher_invitations
    for select using (lower(email) = lower(coalesce(auth.jwt()->>'email', '')));

-- SESSIONS, CLASSES, SECTIONS, ASSIGNMENTS
create policy "Members read sessions" on public.academic_sessions for select using (public.is_school_member(school_id));
create policy "Principal manages sessions" on public.academic_sessions for all using (public.is_school_principal(school_id));

create policy "Members read classes" on public.classes for select using (public.is_school_member(school_id));
create policy "Principal manages classes" on public.classes for all using (public.is_school_principal(school_id));

create policy "Members read sections" on public.sections for select using (public.is_school_member(school_id));
create policy "Principal manages sections" on public.sections for all using (public.is_school_principal(school_id));

create policy "Members read assignments" on public.teacher_assignments for select using (public.is_school_member(school_id));
create policy "Principal manages assignments" on public.teacher_assignments for all using (public.is_school_principal(school_id));

-- STUDENTS (Strict privacy: students ONLY see their own linked record)
create policy "Staff view students" on public.students
    for select using (
        public.is_school_principal(school_id)
        or (public.teacher_has_permission(school_id, 'view_students') and public.teacher_assigned_section(section_id))
        or exists (select 1 from public.student_accounts sa where sa.student_id = students.id and sa.user_id = auth.uid() and sa.status = 'approved')
    );
create policy "Authorized staff insert/update/delete students" on public.students
    for all using (
        public.is_school_principal(school_id)
        or (public.teacher_has_permission(school_id, 'edit_students') and public.teacher_assigned_section(section_id))
    );

-- STUDENT ACCOUNTS
create policy "Users view own student requests or staff view school requests" on public.student_accounts
    for select using (user_id = auth.uid() or public.is_school_principal(school_id) or public.teacher_has_permission(school_id, 'edit_students'));

-- ATTENDANCE SESSIONS & RECORDS
create policy "Staff or section student read attendance sessions" on public.attendance_sessions
    for select using (
        public.is_school_principal(school_id)
        or (public.is_school_teacher(school_id) and public.teacher_assigned_section(section_id))
        or exists (select 1 from public.student_accounts sa where sa.section_id = attendance_sessions.section_id and sa.user_id = auth.uid() and sa.status = 'approved')
    );
create policy "Authorized staff insert attendance sessions" on public.attendance_sessions
    for insert with check (
        public.is_school_principal(school_id)
        or (public.teacher_has_permission(school_id, 'mark_attendance') and public.teacher_assigned_section(section_id))
    );
create policy "Authorized staff update attendance sessions" on public.attendance_sessions
    for update using (
        public.is_school_principal(school_id)
        or (public.teacher_has_permission(school_id, 'edit_attendance') and public.teacher_assigned_section(section_id))
    );

create policy "Staff or own student read attendance records" on public.attendance_records
    for select using (
        public.is_school_principal(school_id)
        or public.is_school_teacher(school_id)
        or exists (select 1 from public.student_accounts sa where sa.student_id = attendance_records.student_id and sa.user_id = auth.uid() and sa.status = 'approved')
    );
create policy "Authorized staff write attendance records" on public.attendance_records
    for insert with check (
        public.is_school_principal(school_id) or public.teacher_has_permission(school_id, 'mark_attendance')
    );
create policy "Authorized staff edit attendance records" on public.attendance_records
    for update using (
        public.is_school_principal(school_id) or public.teacher_has_permission(school_id, 'edit_attendance')
    );

-- TESTS & RESULTS (Students only see PUBLISHED tests and ONLY their own result row)
create policy "Staff view all tests, students view published tests in own section" on public.tests
    for select using (
        public.is_school_principal(school_id)
        or (public.is_school_teacher(school_id) and public.teacher_assigned_section(section_id))
        or (status = 'published' and exists (
            select 1 from public.student_accounts sa
            where sa.section_id = tests.section_id and sa.user_id = auth.uid() and sa.status = 'approved'
        ))
    );
create policy "Authorized staff manage tests" on public.tests
    for all using (
        public.is_school_principal(school_id)
        or (public.teacher_has_permission(school_id, 'create_tests') and public.teacher_assigned_section(section_id))
    );

create policy "Staff view test results, students view ONLY own published results" on public.test_results
    for select using (
        public.is_school_principal(school_id)
        or public.is_school_teacher(school_id)
        or (
            exists (select 1 from public.tests t where t.id = test_results.test_id and t.status = 'published')
            and exists (select 1 from public.student_accounts sa where sa.student_id = test_results.student_id and sa.user_id = auth.uid() and sa.status = 'approved')
        )
    );
create policy "Authorized staff write test results" on public.test_results
    for all using (
        public.is_school_principal(school_id) or public.teacher_has_permission(school_id, 'enter_marks')
    );

-- ANNOUNCEMENTS, LIKES, COMMENTS
create policy "Members read school or own section announcements" on public.announcements
    for select using (
        public.is_school_principal(school_id)
        or public.is_school_teacher(school_id)
        or (public.is_school_member(school_id) and (
            scope = 'school' or exists (
                select 1 from public.student_accounts sa
                where sa.section_id = announcements.section_id and sa.user_id = auth.uid() and sa.status = 'approved'
            )
        ))
    );
create policy "Staff publish announcements" on public.announcements
    for insert with check (
        public.is_school_principal(school_id)
        or (scope = 'class' and public.teacher_has_permission(school_id, 'publish_announcements') and public.teacher_assigned_section(section_id))
    );
create policy "Publisher or Principal edit/delete announcement" on public.announcements
    for update using (public.is_school_principal(school_id) or publisher_id = auth.uid());
create policy "Publisher or Principal delete announcement" on public.announcements
    for delete using (public.is_school_principal(school_id) or publisher_id = auth.uid());

create policy "Members read comments" on public.announcement_comments
    for select using (exists (select 1 from public.announcements a where a.id = announcement_id and public.is_school_member(a.school_id)));
create policy "Members add comments" on public.announcement_comments
    for insert with check (user_id = auth.uid() and exists (select 1 from public.announcements a where a.id = announcement_id and a.allow_comments = true and public.is_school_member(a.school_id)));

create policy "Members read likes" on public.announcement_likes
    for select using (exists (select 1 from public.announcements a where a.id = announcement_id and public.is_school_member(a.school_id)));

-- TIMETABLES, HOMEWORK, NOTIFICATIONS
create policy "Members read timetables" on public.timetables for select using (public.is_school_member(school_id));
create policy "Staff manage timetables" on public.timetables
    for all using (public.is_school_principal(school_id) or (public.is_school_teacher(school_id) and public.teacher_assigned_section(section_id)));

create policy "Members read homework" on public.homework
    for select using (
        public.is_school_principal(school_id)
        or public.is_school_teacher(school_id)
        or exists (select 1 from public.student_accounts sa where sa.section_id = homework.section_id and sa.user_id = auth.uid() and sa.status = 'approved')
    );
create policy "Staff manage homework" on public.homework
    for all using (public.is_school_principal(school_id) or (public.is_school_teacher(school_id) and public.teacher_assigned_section(section_id)));

create policy "Users read own notifications" on public.notifications for select using (user_id = auth.uid());
create policy "Users mark own notifications read" on public.notifications for update using (user_id = auth.uid());
create policy "Staff send notifications in school" on public.notifications for insert with check (public.is_school_principal(school_id) or public.is_school_teacher(school_id));

-- ============================================================================
-- STORAGE BUCKET SETUP
-- ============================================================================
insert into storage.buckets (id, name, public)
values ('school-media', 'school-media', true)
on conflict (id) do nothing;

create policy "Public read school media" on storage.objects
    for select using (bucket_id = 'school-media');
create policy "Authenticated upload school media" on storage.objects
    for insert to authenticated with check (bucket_id = 'school-media');
create policy "Authenticated update own media" on storage.objects
    for update to authenticated using (bucket_id = 'school-media');
