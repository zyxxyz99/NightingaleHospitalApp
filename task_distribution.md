# Nightingale Hospital App - Direct Firebase Work Distribution

This document outlines the work distribution for **all functional features (FR-1 to FR-5)** listed in [`SRS.md`](SRS.md). It assumes the **Firebase/Firestore database is fully active, configured, and accessible to all 6 developers**.

> **Non-functional requirements** (NFRs - performance, security, scalability, maintainability) are **out of scope** for this document and will be addressed in a separate plan.

Because the team has direct access to the live (or staging) Firebase Console, developers can wire their UI to the real database immediately and inject test data directly via the Firebase Console when blocked on another developer's feature.

> **Convention used below:** Tasks marked **[NEW]** are additional assignments added after the initial distribution; tasks without that marker are the original assignments retained for reference.

---

## 🛠️ Prerequisites for All Developers

1.  **Development Environment**: Install Android Studio and the required Android SDK.
2.  **Repository Access**: Clone the `NightingaleHospitalApp` repository.
3.  **Firebase Access**: Accept the invite to the Firebase Project Console.
4.  **Google Services JSON**: Download the latest `google-services.json` from the Firebase Console and place it in the `app/` directory.

---

## 🟢 Work Already Completed

The following foundation is already in the codebase:
1.  **Data Models (`models/`)**: `User`, `Bed`, `Appointment`, `Prescription`, `Medicine`, `DiagnosticTest`, `TestBooking`, `TestResult`, `SurgeryBooking`, `Admission`, `Department`, `OperationTheatre`, `Notification`, etc.
2.  **Repositories (`repository/`)**: Skeletons for `AuthRepository`, `AppointmentRepository`, `BedRepository`, `PrescriptionRepository`, `DiagnosticRepository`, `SurgeryRepository`, `AdmissionRepository`, `DoctorRepository`, `PatientRepository`, `NotificationRepository` (the last is empty).
3.  **Core Activities**: `MainActivity`, `LoginActivity`, `RegisterActivity`, dashboard shells (`AdminDashboardActivity`, `DoctorDashboardActivity`, `PatientDashboardActivity`), `ProfileActivity`.
4.  **ViewModels**: `AuthViewModel`, `AppointmentViewModel` (with sealed `UiState`).
5.  **Doctor Screens**: `MyAppointmentsActivity`, `PatientsListActivity`, `PatientHistoryActivity` (last uses a demo-data fallback for now).
6.  **Shared UI**: `DashboardItem`/`DashboardCard` composables, `NightingaleHospitalAppTheme`.

---

## 🚀 Task Distribution & Direct Firebase Strategy

### Developer A: Core Polish & Shared UI (Auth, Roles, Platform Foundations)
*Owns the platform layer: identity, roles, profile management, audit, and the cross-cutting admin screens that don't fit elsewhere.*

- **Original**: Finalize `AuthViewModel` using Firebase Authentication. Implement session management so `FirebaseAuth.getInstance().currentUser` is globally accessible.
- **Original**: Create test user accounts (Admin, Doctor, Patient) directly in the Auth Console and share the credentials with the team.

- **[NEW] Finish User Registration**
    - Complete the `RegisterActivity` form (email, password, name, role selector) with field validation.
    - On success, write the `users/{uid}` document with the appropriate `role`. Doctors are auto-flagged `approved=false` so an Admin must approve them before they can log in.

- **[NEW] Role-Based Access Enforcement**
    - Centralize the role check inside `AuthRepository` / `AuthViewModel`. Use `AuthRepository.checkSession()` (currently defined but unused) as the single source of truth on every app launch.
    - Route to `AdminDashboardActivity` / `DoctorDashboardActivity` / `PatientDashboardActivity` from a refactored `MainActivity`.

- **[NEW] Profile + Logout (cross-role)**
    - Extend `ProfileActivity` so Admin, Doctor, and Patient all see role-appropriate fields (department for doctor, MRN for patient, etc.).
    - Centralize the logout button and ensure it returns the user to `LoginActivity` with the back-stack cleared.

- **[NEW] Manage Doctor Profiles (Admin CRUD)**
    - Wire the Admin dashboard card "Manage Doctors" to a new `DoctorManagementActivity`.
    - Extend `DoctorRepository` (currently only has `addDoctor()`) with `getDoctors()`, `getDoctor(id)`, `updateDoctor()`, `deleteDoctor()`.

- **[NEW] View System Activity (Admin audit log)**
    - Introduce a lightweight `activity_logs` collection with `{actorId, action, targetId, timestamp}`.
    - Build `ActivityLogActivity` reached from the Admin dashboard "View Statistics" card. Display the last 100 events sorted by timestamp.

**Firebase Strategy**: Seed Admin/Doctor/Patient accounts in the Firebase Auth Console. Seed a `doctors` collection with 3-4 doctor profiles so the new Doctor management UI has data on first launch. Drop one `activity_logs` document manually to verify the audit screen renders.

---

### Developer B: Admin Resource UI (Beds, Theatres, Admissions)
*Owns the physical-resource admin screens.*

- **Original**: Build UI for managing `Bed` and `OperationTheatre` entities and connect them via `BedManagementViewModel` to the Firestore collections.
- **Original**: Manually add 3-4 Bed documents to the `beds` Firestore collection.

- **[NEW] Beds CRUD (full lifecycle)**
    - Extend `BedRepository` with `getBeds()`, `getBed(id)`, `updateBed()`, `deleteBed()`.
    - Build `BedManagementActivity` reached from the Admin "Manage Beds & Theatres" card. Include status transitions (Available → Occupied → Maintenance → Cleaning).

- **[NEW] Operation Theatres CRUD**
    - Create `OperationTheatreRepository` (currently absent) with `addTheatre()`, `getTheatres()`, `updateTheatre()`, `deleteTheatre()`.
    - Build `OperationTheatreManagementActivity` linked from the same Admin card. Allow admin to set capacity and current status (`FREE`, `IN_USE`, `CLEANING`).

- **[NEW] View Admissions List**
    - Extend `AdmissionRepository` (currently only has `admitPatient()`) with `getAdmissions()`, `updateAdmission()`, `dischargePatient()`.
    - Build `AdmissionsListActivity` linked from the Admin "View Admissions" card. Include admit / discharge actions.

**Firebase Strategy**: Seed 3-4 `beds` docs, 2 `operation_theatres` docs, and a few `admissions` records so each list screen renders on first launch. Confirm Bed status transitions write back to Firestore.

---

### Developer C: Doctor Schedule, Slots & Patient History
*Owns everything related to doctor scheduling and patient history aggregation.*

- **Original**: Build UI for viewing/updating `Appointment` status and viewing patient history.
- **Original**: Manually create a dummy `Appointment` document in Firestore assigned to your test Doctor account.

- **[NEW] Set and Manage Appointment Slots (Doctor)**
    - Introduce an `appointment_slots` collection: `{doctorId, date, time, capacity, bookedCount}`.
    - Build `DoctorSlotActivity` so doctors can create / edit / delete their available slots. Link from the Doctor dashboard "My Appointments" card.

- **[NEW] Manage Doctor Schedules (Admin-facing)**
    - Build an admin-side screen for setting weekly templates / bulk-creating slots for a given doctor.
    - Share a `DoctorScheduleViewModel` between the doctor's own slot management and the admin's bulk scheduling screen.

- **[NEW] Real-time Patient History (replace demo fallback)**
    - Replace `AppointmentViewModel.demoPatientHistory()` with a `loadPatientHistory(patientId)` that queries `appointments`, `prescriptions`, `test_results`, `surgery_bookings` collections and merges them by date.
    - Wire each section to the corresponding repository once Devs E and F expose `getForPatient(patientId)`.

- **[NEW] Patient-side Medical History View**
    - Build a `PatientHistoryActivity` for patients, reached from the Patient dashboard "Medical History" card.
    - Reuse `loadPatientHistory(currentUserUid)` so patients view their own aggregated history.

**Firebase Strategy**: Seed `appointment_slots` documents for the test doctor. Manually create one `prescription` and one `test_result` linked to a demo patient so the real patient-history view has data once Devs E and F land.

---

### Developer D: Patient Booking Flow
*Owns the patient-facing scheduling experience: search, book, view, cancel.*

- **Original**: Build doctor search and appointment booking UI inside `PatientDashboardActivity`. Create `BookingViewModel` to push new appointments to Firestore.
- **Original**: Look at the `doctors` and `slots` collections in Firestore, manually insert a dummy time slot, and build the booking flow against it.

- **[NEW] Doctor Search & Filter**
    - Build the "Book Appointments" screen: search by name, filter by department / specialty, view doctor profile + available slots.
    - Add `searchDoctors(query, department)` and `getSlotsForDoctor(doctorId, date)` to `DoctorRepository`.

- **[NEW] Book Appointment**
    - Patient picks a doctor slot → `BookingViewModel.bookAppointment(doctorId, patientId, slot)` writes a new `appointments` document with denormalized `patientName`, `patientAge`, `patientGender` fields.

- **[NEW] Cancel Appointment**
    - Add `cancelAppointment(appointmentId)` to `AppointmentRepository`, updating status to `CANCELLED`.
    - Patient dashboard shows upcoming appointments with a Cancel button.

- **[NEW] Patient-side "My Appointments" View**
    - Add a `MyAppointmentsActivity` for patients that uses `AppointmentRepository.observeAppointmentsForPatient(patientId)`. Mirror the doctor-side version but show only the patient's own bookings.

**Firebase Strategy**: Insert 5-6 dummy `slots` documents and 3-4 `doctors` with department metadata. Confirm the booking writes an `appointments` document with the correct `doctorId` / `patientId` so the doctor's `My Appointments` screen receives it in real time.

---

### Developer E: Clinical Treatment (Prescriptions & Medicine)
*Owns the prescription lifecycle and medicine inventory checks.*

- **Original**: Build the "Create Prescription" UI for Doctors and "View Prescription" UI for Patients. Create `PrescriptionViewModel` to push/pull from the `prescriptions` collection.
- **Original**: Manually grab an existing `appointment_id` from Firestore and use it to test saving a new prescription document.

- **[NEW] Create Prescription (Doctor)**
    - Build `CreatePrescriptionActivity` reachable from the Doctor dashboard "Write Prescription" card. Form: pick an appointment, add medicines with dosage / duration / instructions.

- **[NEW] View Prescriptions (Patient & Doctor)**
    - Patient dashboard "View Prescriptions" → `PrescriptionsListActivity` showing all prescriptions for `currentUser.uid`.
    - Doctor's patient-history view shows prescriptions by patient (consumed by Dev C).

- **[NEW] Check Medicine Availability**
    - Populate the `medicines` collection: `{name, dosage, stock, manufacturer}`.
    - Add `MedicineRepository.getMedicines()`, `updateStock()`, `isAvailable(name)`.
    - Doctor's prescription form shows a green / red chip next to each medicine based on `isAvailable()`.

- **[NEW] Prescription Detail Sharing**
    - Patient can view a single prescription's full detail (doctor name, date, medicines, instructions).
    - Add `getPrescriptionsForPatient(patientId)` to `PrescriptionRepository`.
    - Hook a `sendNotification(...)` call (provided by Dev F) so the patient receives an FCM push when a prescription is shared.

**Firebase Strategy**: Manually insert 6-8 `medicines` documents with varying stock levels. Manually insert one `prescription` linked to a test `appointment_id`. Confirm the patient's prescription list updates in real time.

---

### Developer F: Diagnostics, Notifications & Admin Reports
*Owns the diagnostic-test catalog, test-result delivery, in-app notifications, and admin reporting.*

- **Original**: Build UI for Admin to upload test results, and for Patient to view test results.
- **Original**: Manually insert a dummy `TestResult` document into Firestore linked to a test Patient ID.

- **[NEW] Manage Diagnostic Tests Catalog (Admin)**
    - Introduce a `diagnostic_tests` collection: `{name, description, price, department, sampleType}`.
    - Build `DiagnosticTestManagementActivity` for the Admin to CRUD the catalog.
    - Extend `DiagnosticRepository` with `addTest()`, `getTests()`, `updateTest()`, `deleteTest()`.

- **[NEW] View Test Results (Patient)**
    - Patient dashboard "Test Results" → `TestResultsListActivity` showing all `test_results` for the current patient.
    - Add `getTestResultsForPatient(patientId)` to `DiagnosticRepository`.

- **[NEW] Upload Test Results (Admin)**
    - Build the Admin flow to upload a new `TestResult` linked to a `TestBooking` and a patient.
    - Implement status transitions: `Pending → InProgress → Completed`.

- **[NEW] Receive Notifications (Patient & Doctor)**
    - Implement FCM: register the device token on login, store it in `users/{uid}.fcmToken`.
    - Build an in-app `NotificationsActivity` reading from the `notifications` collection.
    - Add `sendNotification(userId, title, body)` to `NotificationRepository` (currently empty). Trigger it from the prescription-create flow (Dev E) and the test-result-upload flow (Dev F).

- **[NEW] Generate Admin Reports**
    - Implement the Admin "View Statistics" screen (separate from Dev A's activity log): counts of appointments by status, prescriptions issued, tests completed, beds occupied.
    - Add a `ReportsViewModel` that aggregates across `appointments`, `prescriptions`, `test_results`, `beds` using Firestore count queries.

**Firebase Strategy**: Manually insert one `TestResult` linked to a test patient; insert 3-4 `diagnostic_tests` catalog entries; trigger a notification by creating a new prescription document with the `sendNotification` hook.

---

## 🔗 Resolving Cross-Developer Dependencies via Firebase

In a traditional setup, Developer E (Prescriptions) would be blocked waiting for Developer D (Booking) to finish so an appointment actually exists to prescribe against.

**Because Firebase is active and accessible, all blockers are removed:**
If Developer E needs an appointment to attach a prescription to, they simply open the Firebase Console in their web browser, manually create an `Appointment` JSON document, and immediately continue coding. 

### Execution Flow
1.  **Days 1-2**: Developers wire their `ViewModels` directly to their respective Firestore collections.
2.  **Days 3-6**: Developers use the Firebase Console to inject test data for their specific screens, allowing them to build UI fully independent of other developers' progress.
3.  **Days 7-10**: End-to-end testing. Developers stop using manually injected data and test the live flows (e.g., Dev D books an appointment, Dev E sees it appear in the database in real-time and prescribes against it).
