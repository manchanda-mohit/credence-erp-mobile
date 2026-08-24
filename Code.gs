/**
 * CREDENCE — Student & Therapy Fee Management System (Version 1)
 * Backend: Google Apps Script bound to a Google Sheet.
 *
 * SCOPE (V1): Login → Student Information → Therapy Selection (on student
 * record) → Fee Management → Payment → Due Tracking → Dashboard → Reports.
 * No separate Therapy Management module — see README for future-module notes.
 */

// ─────────────────────────────────────────────────────────────────────────
// CONFIG
// ─────────────────────────────────────────────────────────────────────────

const SHEETS = {
  STUDENTS: 'STUDENTS',
  FEES: 'FEES',
  PAYMENTS: 'PAYMENTS',
  USERS: 'USERS',
  RATES: 'TherapyFees',
  ENQUIRIES: 'Enquiries',
  ENQUIRY_FOLLOWUPS: 'EnquiryFollowups',
  AUDIT_LOG: 'Audit_log',
  DELETED_STUDENTS: 'Deleted_students',
  DELETED_FEES: 'Deleted_fees',
  DELETED_PAYMENTS: 'Deleted_payments',
  DELETED_USERS: 'Deleted_users',
  DELETED_ENQUIRIES: 'Deleted_enquiries',
  THERAPISTS: 'Therapists',
  DELETED_THERAPISTS: 'Deleted_therapists',
  EXPENSES: 'Expenses',
  DELETED_EXPENSES: 'Deleted_expenses',
  THERAPIST_LEAVE_REQUESTS: 'TherapistLeaveRequests',
  TODOS: 'ToDos',
  LEAVE_ADJUSTMENTS: 'LeaveAdjustments',
  STAFF_AUDIT_LOG: 'StaffAuditLog'
};

// ── Four-database architecture ─────────────────────────────────────────
// The app can run as one bound spreadsheet (default — nothing to
// configure) or split across up to four physical Google Sheets. To split,
// create extra blank Sheets, copy each one's ID from its URL, and set
// these Script Properties (Project Settings → Script Properties in
// the Apps Script editor) — no code change needed:
//   ENQUIRY_SPREADSHEET_ID, STUDENT_FEE_SPREADSHEET_ID,
//   STAFF_SPREADSHEET_ID, EXPENSE_SPREADSHEET_ID
// Any property left unset falls back to this script's bound spreadsheet,
// so existing single-spreadsheet deployments keep working untouched.
// IDs are never sent to the frontend — only sheet data is.
const DB_FOR_SHEET_ = {};
[SHEETS.STUDENTS, SHEETS.FEES, SHEETS.PAYMENTS, SHEETS.USERS, SHEETS.RATES, SHEETS.AUDIT_LOG,
  SHEETS.DELETED_STUDENTS, SHEETS.DELETED_FEES, SHEETS.DELETED_PAYMENTS, SHEETS.DELETED_USERS
].forEach(function (n) { DB_FOR_SHEET_[n] = 'studentFee'; });
[SHEETS.ENQUIRIES, SHEETS.ENQUIRY_FOLLOWUPS, SHEETS.DELETED_ENQUIRIES, SHEETS.TODOS
].forEach(function (n) { DB_FOR_SHEET_[n] = 'enquiry'; });
[SHEETS.THERAPISTS, SHEETS.DELETED_THERAPISTS, SHEETS.THERAPIST_LEAVE_REQUESTS, SHEETS.LEAVE_ADJUSTMENTS, SHEETS.STAFF_AUDIT_LOG
].forEach(function (n) { DB_FOR_SHEET_[n] = 'staff'; });
[SHEETS.EXPENSES, SHEETS.DELETED_EXPENSES
].forEach(function (n) { DB_FOR_SHEET_[n] = 'expense'; });

// Memoizes each bucket's opened Spreadsheet object for the lifetime of
// one script execution (one google.script.run round trip). Without this,
// every call to getSheet_() re-ran getSpreadsheet_() from scratch — cheap
// in the default single-spreadsheet setup (SpreadsheetApp.getActiveSpreadsheet()
// has no real cost), but a genuine redundant network round-trip
// (SpreadsheetApp.openById()) for anyone running the optional split-
// database setup. getDashboardStats() alone calls getSheet_() for
// Students, Fees, Payments (all 'studentFee'), and Enquiries — 3 opens of
// the same physical file where 1 would do — and the "Last updated" tile's
// getLastActivityTimestamp_() calls it 3 more times on top of that. This
// cache collapses all of those down to one real open per distinct
// spreadsheet per request, regardless of how many sheets/functions need
// it. Safe to memoize: which physical spreadsheet a bucket points to is
// fixed for the life of a request (it only changes if someone edits
// Script Properties, which takes effect on the next request either way).
const SPREADSHEET_CACHE_ = {};
function getSpreadsheet_(dbKey) {
  if (SPREADSHEET_CACHE_[dbKey]) return SPREADSHEET_CACHE_[dbKey];
  const props = PropertiesService.getScriptProperties();
  const propName = {
    studentFee: 'STUDENT_FEE_SPREADSHEET_ID', enquiry: 'ENQUIRY_SPREADSHEET_ID',
    staff: 'STAFF_SPREADSHEET_ID', expense: 'EXPENSE_SPREADSHEET_ID'
  }[dbKey];
  const rawId = propName ? props.getProperty(propName) : null;
  const id = rawId ? rawId.trim() : null;
  if (!id) {
    SPREADSHEET_CACHE_[dbKey] = SpreadsheetApp.getActiveSpreadsheet();
    return SPREADSHEET_CACHE_[dbKey];
  }
  // A misconfigured split-database Script Property (typo, stray space,
  // wrong ID, or a spreadsheet this script account can't access) used to
  // surface as either a vague error or — in some environments — an
  // indefinitely hanging request, since SpreadsheetApp.openById() doesn't
  // always fail fast or clearly. Wrapping it here converts any failure
  // into one specific, actionable message naming exactly which property
  // is wrong, rather than leaving every caller (Therapists list, Leave
  // dropdown, etc.) to fail in its own unclear way.
  try {
    SPREADSHEET_CACHE_[dbKey] = SpreadsheetApp.openById(id);
    return SPREADSHEET_CACHE_[dbKey];
  } catch (e) {
    throw new Error('Could not open the spreadsheet configured for "' + propName + '" ' +
      '(Script Properties value: "' + id + '"). Check that this is a valid spreadsheet ID ' +
      '(the string between /d/ and /edit in its URL) and that this script has access to it. ' +
      'Original error: ' + e.message);
  }
}

const THERAPIES = ['OT/PT/Reflexes', 'SP', 'SpEd', 'ABA', 'Sports'];
const THERAPY_LABELS = {
  'OT/PT/Reflexes': 'Occupational / Physiotherapy / Reflex Integration',
  'SP': 'Speech & Language Therapy',
  'SpEd': 'Special Education',
  'ABA': 'Applied Behavior Analysis',
  'Sports': 'Sports Therapy'
};

const SESSION_TYPES = ['20 Sessions', 'Monthly [M-F]', 'Monthly [M-S]'];

// Roles: Admin owns everything (incl. deletes). Coordinator (front-desk
// staff) can add/update students, create/edit fees, and record payments,
// but cannot delete any record. Role matching is case-insensitive.
const ROLES = { ADMIN: 'Admin', MANAGER: 'Manager', CENTER_HEAD: 'CenterHead', COORDINATOR: 'Coordinator' };

// ── Enquiry module config ──────────────────────────────────────────────
const ENQUIRY_FOR = ['OT/PT/Reflexes', 'SP', 'SPED', 'Sports', 'ABA', 'BrainGym/BodyGym'];
const ENQUIRY_SOURCES = ['Phone Call', 'WhatsApp', 'Walk-in', 'Instagram', 'Facebook', 'Google',
  'Referral', 'Existing Parent', 'Doctor Referral', 'Camp/Event', 'Other'];
const ENQUIRY_STATUSES = ['New', 'Follow-up', 'Visit / Assessment', 'Converted', 'Lost', 'On Hold'];
const LOST_REASONS = ['Not Interested', 'Fees', 'Location', 'Timing', 'Chose Another Centre',
  'Could Not Contact', 'Child Not Ready', 'Other'];
const CONTACT_MODES = ['Phone', 'WhatsApp', 'Walk-in', 'Other'];

// Staff Management module config (Therapist/Service list, etc.) lives in
// StaffManagement.gs.

const HEADERS = {
  STUDENTS: [
    'Student ID', 'Registration Date', 'Student Name', 'Date of Birth', 'Age',
    'Gender', 'Father Name', 'Mother Name', 'Parent/Guardian Name',
    'Parent Mobile', 'Alternate Mobile', 'Parent Email', 'Parents Occupation',
    'Address', 'City', 'Joining Date', 'Exit Date', 'Student Status',
    'Therapies Taking', 'Notes', 'Created By', 'Created Date',
    'Last Updated', 'Updated By'
  ],
  FEES: [
    'Fee ID', 'Student ID', 'Student Name', 'Therapy', 'Session Type',
    'Billing Month', 'Billing Year', 'Fee Amount', 'Discount', 'Net Amount',
    'Amount Paid', 'Balance Due', 'Payment Status', 'Session Start Date',
    'Created Date', 'Updated Date'
  ],
  PAYMENTS: [
    'Payment ID', 'Fee ID', 'Student ID', 'Student Name', 'Therapy',
    'Billing Month', 'Billing Year', 'Amount Received', 'Discount Given', 'Payment Mode',
    'Payment Date', 'Receipt Number', 'Remarks', 'Created By', 'Created Date'
  ],
  USERS: ['Username', 'Password', 'Full Name', 'Role', 'Active'],
  RATES: ['Therapy', 'Session Type', 'Fee Amount'],
  // Enquiry sheet columns beyond the spec's base list — Assigned To,
  // Source Detail, Last Follow-up Date, Converted Date, Student ID — are
  // additions required by other parts of the spec (role-based visibility,
  // Existing Parent/Doctor Referral name capture, follow-up tracking, and
  // conversion linkage). See README for the full note.
  ENQUIRIES: [
    'Enquiry ID', 'Enquiry Date', 'Child Name', 'Parent/Guardian Name', 'Mobile Number',
    'Age', 'City/Area', 'Enquiry For', 'Source', 'Source Detail', 'Status', 'Assigned To',
    'Last Follow-up Date', 'Next Follow-up Date', 'Remarks', 'Lost Reason', 'Converted Date',
    'Student ID', 'Created By', 'Created Date', 'Last Updated', 'Updated By'
  ],
  ENQUIRY_FOLLOWUPS: [
    'Follow-up ID', 'Enquiry ID', 'Follow-up Date', 'Contact Mode', 'Remarks',
    'Next Follow-up Date', 'Status', 'Followed Up By', 'Created Date'
  ],
  AUDIT_LOG: ['Timestamp', 'User', 'Action', 'Entity Type', 'Entity ID', 'Description'],
  // The Staff Management module's therapist directory. Session/booking
  // scheduling was removed — this is now a straightforward staff record:
  // who they are, what they do, their shift, and their salary. 'Joining
  // Date' was added later (appended at the end, same as 'Shift' and
  // 'Monthly Salary' before it) so setup() adds it as a missing column on
  // an existing Therapists sheet without touching any other column or row.
  THERAPISTS: ['Therapist ID', 'Therapist Name', 'Therapy/Service', 'Mobile', 'Status', 'Notes',
    'Created By', 'Created Date', 'Last Updated', 'Updated By', 'Shift', 'Monthly Salary', 'Joining Date'],
  EXPENSES: ['Expense ID', 'Date', 'Category', 'Description', 'Amount', 'Payment Mode',
    'Paid To', 'Reference Number', 'Remarks', 'Created By', 'Created Date', 'Last Updated', 'Updated By'],
  // Therapist leave requests — a straightforward HR record (apply →
  // approve/reject → history) for therapists, who typically aren't
  // logged-in users of this app themselves, so any logged-in staff member
  // (Admin, Manager, or Coordinator) files a request on a therapist's
  // behalf. Lives in Staff Management alongside the Therapists directory.
  // Every therapist accrues 1 paid leave per calendar month (12/year,
  // resetting each January) — 'Paid Or Unpaid' records which bucket a
  // given request draws from; see getLeaveBalances_ in Leaves.gs.
  THERAPIST_LEAVE_REQUESTS: ['Leave ID', 'Therapist ID', 'Therapist Name', 'Therapy', 'Leave Type',
    'Start Date', 'End Date', 'Number Of Days', 'Reason', 'Status', 'Applied Date', 'Approved By',
    'Approved Date', 'Remarks', 'Created By', 'Created Date', 'Last Updated', 'Updated By', 'Paid Or Unpaid'],
  // A shared team task list — visible to everyone, anyone can complete
  // or reopen any task, matching the same "transparent to the team"
  // pattern already used for the Leave console rather than Enquiries'
  // per-user scoping.
  TODOS: ['Task ID', 'Title', 'Status', 'Created By', 'Created Date', 'Completed By', 'Completed Date',
    'Comment', 'Comment By', 'Comment Date'],
  // Manual grants of extra paid leave on top of the standard monthly
  // accrual — e.g. a bonus for covering someone else's shift, or a
  // one-off goodwill grant. Only Manager/Admin can add these (see
  // grantBonusLeave in Leaves.gs); every grant is added into that
  // therapist's Accrued figure on the Paid Leave Balance table.
  LEAVE_ADJUSTMENTS: ['Adjustment ID', 'Therapist ID', 'Therapist Name', 'Days', 'Reason',
    'Granted By', 'Granted Date'],
  // Staff Management's own audit log — deliberately separate from the
  // shared Audit_log sheet (which lives in the 'studentFee' database
  // bucket). Every Staff Management write (therapist CRUD, leave
  // requests, bonus grants) used to call the shared audit_() as a side
  // effect, meaning a Staff Management action could stall on a totally
  // unrelated database's health even when the Staff spreadsheet itself
  // was fine. Routing this to the 'staff' bucket instead means every
  // core Staff Management operation only ever needs the one spreadsheet.
  STAFF_AUDIT_LOG: ['Timestamp', 'User', 'Action', 'Entity Type', 'Entity ID', 'Description']
};

// ─────────────────────────────────────────────────────────────────────────
// WEB APP ENTRY POINT
// ─────────────────────────────────────────────────────────────────────────

// PWA icon — reuses the same logo already embedded on the login page, so
// there's no second image asset to keep in sync.
const PWA_ICON_DATA_URI_ = 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAQDAwMDAgQDAwMEBAQFBgoGBgUFBgwICQcKDgwPDg4MDQ0PERYTDxAVEQ0NExoTFRcYGRkZDxIbHRsYHRYYGRj/2wBDAQQEBAYFBgsGBgsYEA0QGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBgYGBj/wAARCAGkAaQDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD7+ooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAoozSZoAWimtIi8syj6nFM+025OBPET6bxU8y7gS0UgZSMgg/TmlzTuAUUZFFMAooooAKKKz9Z13R/DulSanrmpW1haJ96a4kCLn056n2HNCV9EJtJXZoUV5tB8e/hVcXv2ZfFKIScCWW2mSM/8AAymK9Dtbu2vrOK7s7iK4t5VDxyxOHVwehBHBFXOnOHxKxFOtTqfBJMmoooqDQKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiig9KACivmj4sfGbVf+E4t9N8Hah5NppNwHlnQ5W7mU4ZD6xjlT6nJ7CvePBviqw8Z+DbPX9POEmXEkROTFIOGQ+4P5jB71wYfMaOIqyowesf60JU020jfooorvKCiiigAoqlqurabomlTanq17DZ2kK7pJpm2qP/AK/t1NfPPjn9oXUL55NP8ERfYrblTqFwmZX/ANxDwg9zk+wrhxuY0MHG9V69upMpqO573r3irw74YtPtOvava2KYyolf5n/3VHLfgK8j8Q/tJ6VbO8XhnQrjUCOBcXT+RH9QvLEfXFfO11d3eo38l7qN1Nd3Mhy807l3Y+5PNR4r5TFcSV5u1FKK+9nNKu3sej6r8dviLqpZYNQttLjP8NlAoI/4E+41yF74u8X6ixN94p1mfPUNeOB+QIFZAwKdxXi1cbiKvxzb+Zk5ye7HPcXUrZlup5CepeQt/M1EQwOd7/nT6D0rn5pdxaj4r2/gbNvqN5Cf+mc7r/I1sWfjjxtp5H2PxfrcQHRTds6/k2RWFW74S8H654219dK0S33YIM9y+RFbr/ec/wAgOT+tbUZV5SUKTd32HFu9kdho3xr+J6XkFna3MWszSMEjt5bQPJIfQbNpJr6P8G33jDUNENz4x0ew0u5YgxwWsxkYD/bzwp9gTWf4C+Gnh7wFp4FhEbnUHXE9/OMySew/ur7D8c12lffZXg8RRhfEVG326I7IRa3YUUUV65oIelfCfxS8d6h4++IN5ezTv/Z1pNJb2Ftn5Y0Viu/H95sZJ9wOgr7rcAqQTwRX54+INFvPDni7VNCv42juLS7ljIbjcNxKsPUFSCD6GvZyWMXUk3utj57iGc1SjFbN6mf2r2b9nXx5f6J49i8IXVzI+k6oSsMTtlYJwpYFfQNggj1wa8Y7da9B+CGiXeufHLRTbxsYrCQ31w46IqA4z9WKgV7WOhGVCXP2PnsunOGJh7Pqz7fHIzS0i8KB6UtfGH6EFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABXl3xv8eN4S8DnTtOuPL1bUw0MJU/NFH/HJ7HBwD6n2r1AnAr4r+Kfih/FvxV1LUUk3Wdu32O0Hby0JGR/vNuP4ivGzzGvDYd8vxS0X6mVWXLE5EICOeRXrXwE8YN4f8cf8I5cykWOrttQMeEuAPlI/3gNv/fNeTj7tOSee2uIrm1cxzwuJI3HVWByD+Yr4TB4mWHrRqrockJcruff46UVieENej8T+BtL1+PAF5brKyj+F8YYfgwIrbr9RhJTipLZnoBXO+MvGejeCPDcmravNgfdht0I8yd+yoD+p6AcmtDXtd07w34duta1acQWlsm92PU9gAO5JwAPU18YeN/GWp+OfFkus6kSkYylrbBsrbx54Ue56k9z7AV5Wb5osFC0dZvb/ADMqtTkRL428e69471j7Xqtw0dqhzb2EbfuoB9P4m9WPPpgcVy5xmlOBV3SNE1jxDqqaboWmXF/dN0ihH3R6sTwo9yQK/P5zq4ipd3lJnHdyZTDAd6v6Xo2qa2ZhplnJMkI3TTkbIoR6vI2FUfU16PN4A8H/AA8sor74j6l/aeqOu+Hw/p8mA3/XR+u334H+9XFeI/GGqeJFS1kjt9P0qE/6PpVivl28Q7Hb/E3+0efpW9TCLDr9+/e7Lf59vzKceX4jHuYLa3fyob1Ltx954VIjHspOC31wB6ZqCkAA6Clrjb10ICiir2i6NqXiLxBbaJpFuZ7y5bai9AvqzHsoHJNOMZTkoxWrEtXZGn4L8G6t458UR6PpS7FXD3Ny6kpbx55Y+p9B3P419h+FPCejeDfDkWjaLbCKJfmeQ8vM56u57k//AFhxVTwJ4K0zwN4Ui0mwAeZsPc3JXDTyY5Y+g7AdhXT1+hZPlUcHDmkvfe/l5HdTp8i8worO1bW7LRYBNfJeGM87re0lnx9fLU4rjbj45/CyzuTb33ipLOUfwXdrPCf/AB9BXuxpyl8KuOdWEPidj0OiuNsPix8NdTIFn420Viege5WM/wDj2K6mz1Cx1CETWF5b3UZ/jgkWQfmCaUoSj8SsONSEvhaZZrzb4nfBvQfiOqXrTPpmsxJ5cd/CgfevZZFONwHbkEdj2r0nNFOnUlTlzQdmKrRhWi4VFdHyta/sqeJzqAW78W6SlpnmSK2kaQj/AHSQB+de9eAPhx4e+HeiPZaNE8lxNhrm9mwZZyBxnHRRk4UcDP4119Fb18bWrLlnLQ5sPl1DDy5qcdQoyKQ18i/G34t6v4h8V33hPQ72S00Sxla3maByrXki8NuYfwA5AHfBJzxSwuFniZ8kR43GwwlPnn8j6tj1nSJb37HFqlk9x08lZ0L/APfOc1ezmvzbSKNJA6IFcchl4I/Gvor4AfFnVpfEcPgTxLfPeQzof7PuZmLSI6jPlMx+8pAO3PIIx3GO3FZTKjBzi72PNweeQr1FTnG19j6aoooryT3gooooAKKKKACiiigAooooAKKKKACiiikAUUUUwCiiigAooooAKKKKACiiigDnfHesHQPhtrerq2Hgs5DGf9sjav6kV8OoMQIOpCgH3r63+Ply9v8AA/UlQ482a3jP0Mqn+lfJCHIAr4fieo3XhDsvzOTEPVInW3kbTJ70fchljiP1cOR/6AahXnJIrq7PTN3wV1fUivTWrSPPsIZf6uK5c8V89Up8ijfqrmLVrH07+zprJvPh1eaO7ZbT7sheeiSDeP13V7H2r5t/ZnvGXxT4hsC3yvawzY91dl/9mr3jxbr0XhfwPqmvyjIs7dpVX+82MKv4sQPxr9ByivfAxnJ7L8jtpv3Ez54+P/jl9Z8Vr4QsZT9i0xg9ztPElwRnB9kB/Mn0rxwttUk4AHc1Ykjv9W1dpFSe81C9lLssal3lkY5OAOSSSa+iPhf8DbfR0i8QeNY0ur/h4dOYhorb3fs7/oPfrXyKoV82xEqkdu/RI5uV1ZHn3w++C2u+MRBqmrl9K0Rvm3sv7+4X/pmp6A/3j+ANdj4u+Ivhn4ZaXJ4N+Gdhai+GUub1RvWFwMZZj/rZPrwP0qH4tfGlpWuPC/gu5xEMx3WpxN97sUiI7erj6D1rwYAGujEYmhgV7HB6y6y/yKcow0iT3d5eahfS31/dS3VzM2+SaZtzufUmo6QKBS14Em27vc50FGcd6KAkkkixxRvJIxCqiLuZiegAHU0JX2GLGkk08cEEbSSyOESNASzseAAB1J9K+svg/wDDRfBGgHUNViRtdvEHnMOfITqIlP6se59gKxPg78Im8OeX4o8TwqdXdc29q2CLMEdT/wBNCP8Avkcdc17RX22RZR7FfWKy97ou3/BOujS5dWFFFFfTm4VUv9L03VLY2+p2FteQnrHcRLIp/Bgat0UbCaT0Z5b4h/Z++G2uRO1tpDaPO2cS6c/lgH/rmcr+grxbxL+zx8QPCsr3/g+/GqwJlgLRja3S/wDAQ2G/A/hX13SEAjBFdtHH1qWid12ep5+IyvD1tWrPutD4c0j4xfFXwpqLWVxr95M9udkljrEPmlfY7gHH516/4W/ai0ydktvGGhS2DHg3dgTNF9Sh+Yfhur1zxl8PfCvjrTzba9pqSSgYju4vkni/3XHP4HI9q+UPiX8EvE/gHzNRs/M1nQ15N5Cn7yAf9NUHQf7Q49cV6NKpg8Z7tSPLLyPIrUsfgHzUpc8PM+wdA8T6B4p0wah4e1e01G3PV4H3bT6MOqn2IFa9fnJo+r6toOqpquh6ldafeL0ntpCpPsezD2ORX0d8Ov2lIp3i0r4hJFbSHCrq0C4jY/8ATVP4P95ePYVzYrKalJc1P3l+J14LPKVZ8lX3X+B9GGvzs1m2uLHxRqtjeArcwXs0coPUMHOa/Q63uILy1jubWaOeGRQySRsGV1PQgjgivD/jJ8CJvF+qP4q8IzwW+sOB9qtJjsju8DAYNj5XwAOeDgZxjNTleKjQqNT0TLzrBzxNJOnq0fKfWuw+FNrc3vxt8MQWmfMW/SVsdkTLMfpgGr0PwS+Ksl8LQ+EbhGzgyyTwiMe+7f0r6H+DvwZj+Hwk1rWLmK916ePyi0XMdqhOSiE8knjLcdMAY6+xjMfRjSkoyu2eDl+WV51ouUWkn1PXF6UtFFfKH3AUUUUAFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFABRRRQBFNdW1u8SXFxFE0r7Iw7hS7YzgZ6nAPA9Klr52/aY1ZjqPh/Q0YjYst6xBxg5CL/7PXI+Bfjh4r8LtHZas763pa8bLh/38Q/2JD1+jZ+orw62eUaGJdCorJdTJ1UpWZ9b0VzvhPxr4e8aaV9u0O+WUrxNA/wAssJ9HXt9eh7Guir2adSNSKlB3TNU76nmnx5tGuvgdqjL/AMsJYJj9BKuf518ip2x0r7r8XaMPEPgTV9FIybu0kiX2Yqdp/PFfC2ySJfLlUrInyup6gjgj86+L4npNV4VOjVvuOWutUz1XTrBX/ZG1u8HJTWo5D/wHy0/9mrynO45zXvXgaxOqfsd+KLWMbn8y6kAHcoEcf+g14EjfKCe/NebmVPlp0Jd4ozqKyXoe2fs1wn/hYWtTqMqunIpPuZcj+Rrvvj5LqV/4U0nwjosElxqGs34RII+rpGpdifRQdpJPArH/AGadJaLw7reuOmBdXCW8Z9RGpJ/V/wBK9ue1tmu0u3gRp0QoshXLKpwSAewOBn6CvqcswrqZcqTdua/3NnTTV4JHn3wx+FOm+BdPW/vfLvNdkTEtzjKwg9Ujz0Hqep9hxXnPxl+Lj3r3HhLwpd7bUZivr6I8ynvHGf7v95h16DjOb3xt+LbW0s/gnwzclZx8mo3kTYMfrCpH8X949unXOPn9MuVjjjZyeAiKWJ+gFeVmuYQw8PqWD0S3a/r7zKpU5fcgMVQBgYwPSngYq/FoWvTnFv4f1eX/AHLKU/8AstaVp4A8e3rAW3g3WiD3ktjGPzbFfORw9WXwxb+RzqDfQ56kyPUV6dpPwF+IOpMv2u1s9LjPVrqcM3/fKbq9E8Ofs4aFYyJceJdXudVcYP2eAfZ4voSMsfzFd9DJcZW+xZeehoqMmeA6B4b13xXqg03w9p8l7Pn5yvCRD1djwo+v619N/DX4N6V4LMer6q0eoa6BxNj91b56iMHv/tHn0xV/WfHnw5+Gem/2VA9tFLEPl0zTYw0mf9oDhT7sRXjnin9oDxZrBe30CGHQ7Y8CQYlnI/3iNq/gPxr2KNHA5W+erLnn5dDWMYU9W9T6b1DVNN0m0N1qd/bWcI6yXEixr+ZrzrWfj78PdLZo7W8u9VkHaxgJX/vttq/ka+Vr/UNQ1W7N1qt/dX056yXUrSt+bdKr9ayxHE9WWlGNvXUmWIfRHvepftLzvxo3hRFHZ725Of8AvlB/WuXu/wBoP4hXDH7ONHtB6R2pc/mzmvLBmlry6mdY2bu6jXpoZutN9T0Rvjn8TWORrlqvsLGP/CpYPjz8SoXBk1DTbgDtJYqM/wDfJFebUVkszxad1Uf3i9pPue26V+0prkTquteHLC6Tu1pK0Lfk24frXo/h746+A9bdILq8m0i4bA2X6bUJ9pBlfzIr5LxSEZGK7aHEGLpv3nzLzLVeS3Pv2GeC4gSa3mSWNxuV0YMrD1BHWnOiyKUdQykYIIzkV8QeEfHPijwTeCXQtTkWDOXspiXgk+qHp9Vwa+mvh98YNA8bBLC4A0zWMf8AHpK+VlPcxt/F9Dg/XrX0+X53RxVoP3Zdv8johVU9DzT4u/s/Ji48T/D+zCPzJdaRGPlbuWhHY+qdD254PzaSBlc8g4IPY1+knavnH4+fBlZ47nx74TtNtwuZdTsoV/1y95kA/iHVgOoGeoOfuMuzNpqlVenRnz2bZQmnWoLXqv8AI8r+GHxf1/4c6glozyajoDtmbT2bmPPVoSfun26H2619l+GvE2i+LvDkGt6FepdWkw4I4ZD3Vl6qw7g1+eKAMuRgg967P4dfETWfhx4mGo2G64spiFvLFmws6DuPRx2b8DxXXj8tjVTnTVpfmcGV5vKg1Tqu8fyPvHA9BS1k+G/EekeK/DVrruiXa3NncLlWHBU91YdmB4IrWr5hpp2Z9pGSkrrYKKKyNb8VeG/DcSya/run6arfd+1TrGW+gJyfwoSbdkEpKKu2a9FYmh+MfCviUsNA8Q6bqTLyyW1wrsPcqDmtvNDTWjCMlJXi7hRRRSGFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFAHyV+0FeNc/GqSAnItbKGIe2cuf8A0KvMPpXffG8sfjxre49BCB9PJSuCHSvzDMpc2LqN92cFR+8y3pGsar4f1mLVdFvpbK9iOVlj6kehHRlPcHivqf4XfGDT/G8KaVqix2OvKvMQOI7gDq0ZPfuV6jtkV8nEDrSxyy21xHcW0skM0bB45I22sjDkEEdDWuXZnUwU/d1j1QU6ri/I/QDqK+RPjl4Sl8M/E2W/ggK6bq2biJx91Zf+Wiexz8w9m9q9k+DvxVHjKwOia26R65apndwBdoP4wOzD+IfiOOnbeNPCGmeN/CU+iamuFf54Z1GWhkH3XX6encEivr8bSp5rhL0nruvXsdUkqkdDzj9nxIr74ParpkmCpvpo2B9HiT/E18zvp93HqDaYsDPdpMbYRKPmZwxXaPcnivp34H6Brfg/VfFHhbXYdk0csNzDIgPlzxsGXeh7j5RnuDwas+HvhKLX45614y1JENot0Z9NhGDl3UFpCO20lgB65PYV5lXLamKw2HhazWj8l/SIdNyjFHa/D/wyPCHw50rQDgzQQgzsP4pW+Zz/AN9E/gBXKfGT4kjwb4f/ALJ0uYf23fIREy8/Z4+hlPv2X357V23i3xNp3hDwhea9qbfubdPlQH5pXPCovuTgV8Ta3r+p+JfEl3rusSmS6unLEA/LGP4UX2AwBXXnOPWBoLD0fiasvJFVZ8isih5c01xtjR555XwAMs8jE/mSSfzNfWHwh+Fdv4K0hdX1eFJfEF0mZG+8LVT/AMs09/7zdzx0FcZ8Afh8k5Xx5qsIZBlNNjcd+jTfzVfxPpX0LjHQVhw/lfLFYqstXt/mTRp2957i4pDiuP8AG/xL8M+BLMHVLkzXrruisLfDSye+Oir7n9a+bvGnxh8X+MHkt0uDpOmNwLO0chmH/TSTq30GB7V6mOzjD4TRu8uyNJ1Iw3PfvF/xn8FeEWktGvTqWoLx9ksMOVP+0/3V/E59q8I8X/Grxj4qElra3A0XT248izYiRh/tS9T9BgV5uFCjAGPpTgDXyGMzzE4j3U+WPl/mc067lsNCAMSByTknufrT6XFJ2zXjGA08U0SKDgkD8a1NA0LU/E/iC30bRrcz3c5wF6BQOrMeyjua+nPBPwL8KeG7aO51q3h1zU+rS3CZhQ+iRnjHuck+3SvTwGVVsa7w0XdmsKTmfKJcbcggj1BpFkVvusp+hzX3zHpunQwCCGxto4gMBEiULj6YrkfFXwm8FeKrWQT6PDZXjD5b2yQRSKfU4GG+hBr16vC9SMbwqXfpY1eH7M+N6K6nx34C1nwDrosdT2zW0xJtbyNcJMo9v4WHcfzFcp0NfNVaU6MnTqKzRg007McTSA5pfak4FZCDpQGKuroSrqQyspwVI6EEdDRk4qf7BfiwF8bC8FoTgXJgfyif9/GP1ppN7LYD6A+EXxoe/uIPCvjG6BunOyz1B+PNPaOQ/wB/0bv356+8nDDHWvz/AMV9Q/BH4lSeJtJPhrW5y+rWUYMczn5rmEcZPq68A+owfWvssizd1LYes9ej7+R1Uat/dZ4x8dvhmvgnxZ/buk22zQtTclVX7ttPyWj9lPLL/wACHYV5H1FfoF438KWXjbwJqHhu+wI7qIhJcZMUg5Rx7hgD+dfA2o6de6Prd7o+pReVeWU7W8yY6Mpwce3cexFfqeVYv20OSW6/I+RzrAqhV9pBe7L8z0T4MfFCT4feLBY6jKx8P6hIBcg8/Z36CYfyb1HPavtOOVJYlkiZXRgGVlOQQehFfnAMd6+q/wBm74gnWvCz+CtTnLX+lIGtmdstLbZwB7lDhfoVrnzfBae3h8/8zsyLMGn9WqP0/wAj0b4n+Mx4E+Geoa/GqPdqBDaRvyrTOcLn2HJPsDXwxqeoahrmsTatrN7JfX053SXE53Mx/oPQDgV9Z/tL6XdX/wAGPtdsrMthfRXMwX+5hkJPsC4NfIY4HORWmS04eyc+tzHiCrN1lTfwpE1jcXOm6jDqGn3MlrdwsHjuIW2OhHcEV9s/Bzx3N4/+G0Wo35X+0rWVrS72jAd1AIcDtuUg49c18QE19Vfst6XdWvw51bUpgyw3uoHyQR94RoFLD8cj/gNVnNODpcz3RGQVZqvyLZo93ooor5k+zCiiigAooooAKKKKACiiigAooooAKKKKACiiigD5G+Ptm1r8b7qYg4urWGYfguz/ANkrzM56ZNe+/tM6IVuND8Soh27XsZWx0/jT/wBnrwMc8mvzXOKTpYyou+v3nDV0kzV1LSmtdI0vVofmtL+FsN/cmjbbIh9wcMPZxWZXpXw106Dxp4P8Q+AJ3RbzA1TSnf8AhmUbXH0YbQfYk9q83mimt7h7e4iaKaNikkbjBRgcEEeoOa5a9HljGrH4Zfg1uiJRsk0TWF/faTrFtqumXDW95bSCWKVeqkfzHYjuCa+1PAXi+18beB7TXbdRHI4MdxCDnypV4Zfp3HsRXxHXsv7O/iZ9P8b3PhmaX/R9SiMsSk9Joxnj6pn/AL5FerkGNdCuqUn7svz6GlCdnY+nMDOcUp45orkviV4sHgz4bajrUZH2oJ5NqvrM/C/l976Cvu6tWNKDnLZanY3bU8C+PHjdvEfjE+GrOTOn6RJhyDxLcY+Y/wDAQdo991cJ4K8KXHjLx1YaBCWWOV99xKv/ACzhXl2/Lge5FYTyPJI0sjmR2JZnY5LE8kn3NfSX7OXhgWfhi+8VXEWJr+QwW7EciFDyR9Xz/wB8ivz7CxlmmP5qm279F0OOP7yep7PY2VrpumQWNlCsNtBGsUUajARQMAD8K8r+LXxgi8IxvoPh94p9cdfnkPzJZgjgkd3PZe3U9geg+K/j1fAnghrm2KNql2TBZRtzhu8hHoo5+uB3r47lkmuLmS5uZpJppXMkkshyzsTkknuSa9/O82eFX1eh8X5I2rVeXRDr26utQv5tR1C6lubmZjJLNM25nPqSa7+w+CHxEv8Aw7HrENhaRiRBIlnNcbJ2UjI4IwD7Eg/SuZ8F2tpe/EfQbO+VWt5dQgSRW6MN44P1r7lAwK8jJcrp45TqVm9DKlTU7uR8DXlndaffzWN/bSW11CxSWGVcMjDqCKhr6T+PPw6GraS3jPSLcHULNMXaIOZ4R/Fx1ZP1XPoK+agQeR0rzcxwEsFWdN6ro/IyqQcHYcTimsTjrTqjl/1RUfxcfnXCld2IPqX4A+C49C8D/wDCSXMZ/tDVwHBbrHAD8ij0z94/Uelev1U0q1jsdCsrKIBY4IEiUDsFUAfyq3X6ng8PHD0Y049EelFWVkFFFFdRRy/xA8IWvjXwLeaLMAs5XzLWU9Yph91v6H2Jr4nMUsLNBcRlJoyUkRuqsDgj8wa+/wA9DXxT8TrSOx+MniO3iUKn2xpAB23BWP6sa+Q4ow8UoV1vsc2IjszlSeaOtJSgFmCqCxJwABkk+gHrXx5ynbfCvwQPHHj6Oxulb+zLVftF4ynG5c4WPP8AtHj6Bq+wH02xfRzpbWkBsjH5Jt9g8vZjG3b0xjtXE/CHwMfBXgCKO8iC6rekXF4e6nHyx/8AARx9S1d+7KkZdiAoGST2FfouT5esLh/fXvS3/wAjupQ5Ynwt4o0yHRPHWs6PbljDaXksMe45O0McD8sVX0TWL7QPEdlrWmyeXdWkqyoezY6qfYjIP1qTxFqK6z4w1bV16Xd5LMPozkj9MVm4r4GpPkqylT76HE3aV0fdvh7W7PxJ4XsdbsG3W93EJF55XPVT7g5B+lfM/wC054RTTPF2n+L7WPEepr9mucdPORflb8U4/wCAV2X7N3iQyadqnhO4lJMDfbLZT2RjhwPYNg/8Crt/jb4ZHij4K6zbJGHubSP7db+oeL5jj6ruH41+rZDmHtFTr99H+oZhQWJwsl13Xqj4ezW34Q8TXngvxxp3iayLFrSUNIg/5axHh0P1Un8cVhxkNErA5BGRT+3FffygpxcZbM+ChOUJKcd0foiv9meI/DKllivNN1C3ztYZWWJ17+xBr5r8Y/sw61DqElx4I1S2ubNjuWzv5DHJEP7okwQwHvg/Wu8/Zr8VHWvhhLoVxIWudFm8hcnkwv8ANGfw+Zf+A17TXx6q1cFVlCDPvXQoZjRhUmuh8p+Ff2YfEt1qMcni/VbTT7JWy8NjIZppB6BsBVz68n2r6f0jSdP0PQ7XSNKtY7aztYxFDEg4VR/nr3q7RWeIxdXEO9Rm2EwNHCp+yW4UUUVzHYFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFFFFAHLfETwovjL4daloS7VuJE8y2dv4Zl5Q/nwfYmviaSOW3nkt7iFoZo2KSRsMFWBwQfoQa/QKvmz4+fDiaz1STxzo9vutLgj+0I0H+qk6CX/dbgH0PPevmOIsvdWCxEFqt/T/gHPXhdXR5P4S8QTeFvHGl+IIM4tZw0ij+OM/K6/ipP6V6v8dvBMCmH4h6GolsrwJ9s8roCwGyb6MMA++D3rw8NX0l8DvEtj4s+Hl54B1xEuHs4jGIpefOtX4A/4CTt9htrxMq5MTCeDqddY+plS95ODPm/vXQeBdQOk/E7w/qIOBFfRBv91mCH9GNWPH/gm98BeMpdJuN0lpJmWyuD/wAtYs8Z/wBodD+fcVh6YW/t2w2feN1Fj6+YteYoTw+IUZKzi1+ZlZxlY+9h0r5o/aS8RNd+KtN8KwyfubSE3cyg9ZHOFz9FBP8AwKvpXPyV8P8AjvWm8RfFDXNXLZSS6eOI/wCwh2L+ij86+z4jxHs8Kqa3kzsrytE5wRyOBHEN0jEKgHdicD9a+7/DGjxeH/B2maLCoC2lskJx3YDk/icn8a+NvAOnrqfxU8PWLLuR7+JmB7qp3n9Fr7L8QamNH8H6nq7EAWlrLP8A98oT/SuHhimowq1n6fqRh1o2fJfxh8VP4o+LOoBJC1lpzfYbcZ4+U/O34vn8AK4bFJl5f3spJkf53J7seSfzNOr5jEVnWqyqS3bOaUuZ3Y63uZrK9hvbc4mt5FmjPoykMP1FfdXhzWrbxH4U0/XLMgw3kCzKB2yOR9Qcj8K+EscGvff2d/GoVrjwRfy45NzYFj17yRj/ANCA92r2+HcYqVd0pbS/M2w8rOx9BsoZSrAEHjBr5P8AjH8M5PB2vPrekW7NoV5JnCji0kP8B9FP8J/D0r6x7VV1DT7LVdLn07ULaO4tZ0McsUgyrKeoNfV5ll8MbS5JbrZnTOCmrHwTmmyAmJsdccfWu/8Aib8L9Q8Aambm3D3GgzPiC6PJiJPEcnofQ9/rxXAdq/Oa9Cph6jp1FZo4JJxdmfdfhbVYdb8FaVq0Dh0ubWOTI9SoyPwOR+Fa9fPX7Pnj2GCJ/A+qzhCXMunO5wGzy0X1zlh65Ir6FBzX6Tl2LjiqEakfn6noQkpK6Ciiiu4oaxwp7V8PeN9Xj174ka5q8Dbobi8kMTeqA7VP4hQa+k/jR8QYvCXg6TSrGYHWdSQxRKp5hjPDSn04yB7/AENfJSAIgUDgDAr4vibFxnKOHi9tWcuIlskPPSvafgP8OW1jU18aaxARY2sh+wxuOJ5R/wAtP91T09WHtXL/AAt+GN54+1j7VeLJBoFu+J5xwZm/55IfX1PYe9fXVlZ22n6fDY2UEcFvAgjjijXCoo4AA9KjIMqdWSxNZe6tvPzCjTv7zJ+1cH8YPEy+GPhPqc6Sbbu7T7FbAdd8gIJ/Bdx/Cu7JwM18n/HHxovijx6NKspd+naQWhBU8STniRvoMbR9D619DnOMWFw0mnq9EbVZ8sbnl0a7YlX0AFOo6UnU1+bnAdr8JdYbRvjLoc27EVxKbOXnAKyDaP8Ax7afwr7ImjSa3eKZQ0bqVZT3B4Ir4Is7qSx1G3vYjh7eVJlPurBv6V97QSpcWscyHKSKHB9iM19rwvVvSnT7O/3nXQd4tH5465pLaD4q1TQ3zmxvJrYZ9FcgfpiqFeifHawXTfj/AK6qDC3PlXQ/4FGuf1BrzzNfsWGnz0oy7o/PsVT9nWnDs2er/s66/wD2L8aIdPZtsOrwPasCeDIo3p/6Cw/4FX2Tnivzw8O6o+h+NNG1mNips76GckeiuC36ZFfoYwjuLUq2GjkXB9wR/hXz2dUuWqp90fU8PVuajKn2f5klFeffCrxXNrej6n4e1KXfq/h29k0y6LdZUViIpf8AgSAZ9wa9BryZxcHys92nUVSKlEKKKKksKKKKACiiigAooooAKKKKACiiigAoorwLxJ+1l4C0HXb7RodF1+9vbKeS2mHkpAgdGKsMu2eoPasa1enRV6jsTKSjqz32ivlO5/bPi3EWXw9lYes+pBT+QjNVV/bPvd/z/DuEr/s6oc/+iq5HmuGX2vzM/b0+59bVFcW8NzayW9xEksUilHRxlWUjBBHcV8xWX7Z+jPIq6n4D1KBf4mtrxJsfgVWu20P9qn4SaxKsV1qGo6O5/wCgjZOqZ/303CtY47D1NFNFKtB9Tz34p/BrUfClzNrnhyCW80I5d4kG6Sz74I6sno3bv61wXhLxFeeE/F9j4isctJbNlo84EsZ+8h9iP1we1fa2jeIvD/iaw+16DrFhqluRy9pOsoGfXB4/GvOPG3wH8N+IZJr/AEGQaJqEmWIjTdbyN/tJ/D9Vx9K+ex2RSU1iME9d7f5GU6OvNA3fFnhzRfi18MYJ7KdN8kf2nT7sjmJyOjex+6w/qK+aPCnhvUV+NWjeGtTtZILyHUYxPCw6BGDsfcYXIPcEV7T8KdC+Ifw78QS+G9f0s32g3jl4L2ylEqW0vckHDKjd+ODg9zXp9z4P0K68eWXjGS126tZwvbpKpwGRhj5h3I5we2411Vcv+v8As8RKPLNNcy/r8C5U+e0nuX9avP7O8NahqGcfZ7aSbP8AuqT/AEr4IhLNErucswyc+vU19ufEuc23wf8AEsq9Rp0w/NCP618Rxfdx+FedxTP36cfJmeI6HoXwVjEvxy0QN/D5r/lE9fR3xdleH4HeJmQ4P2F149DgH+dfNfweuVtPjboLvwHleL8WjcCvpz4oWrXvwZ8TQIMsdOmYD/dXd/SujI9cBVS31/Iqh8DPiwcKPpS0inKA+oBpa+NOMKmsr2803UrfUbCdoLq3kWWKVeqsDkGoPpQOnNOLad0PbVH2l8OvHVn488HRanEEivIv3V5bKc+VIB2/2T1B9Poa6+viLwP4z1LwN4si1jTz5kR+S6tScLPH6exHUHsfYmvsnw94g0zxP4dtdb0i4E1rcLuU9Cp7qw7MDwRX6Jk+ZrGU+WT99b+fmd1KpzrzLd/YWWqabNp+oW0dzazoY5YZV3K6nsRXzN8RPgXq2gyzat4SSbU9M+81oPmntx7f89FH/fQ9D1r6ioxXVj8uo4yNqi16MqcFNan5/iR45FaMvHLG2QQSrIwP6EGvZvBX7Qur6XbR2Hi+xfVIU+Vb63YLPj/bU4Dn3BB+teyeLPhR4K8ZTNdanpQhvmGDe2h8qU/Ujhv+BA15Zq37NF2jltA8URMh6R30BBH/AAJD/SvmY5VmGAm5YV3X9dGYKnOD907qL4+/DiSEO2o3sTH/AJZvZSbh7cAj9a5HxX+0dAtpJb+DtIklmYYF3fjai+4jBy34kVyJ/Zw8eiXAv9CIz97z5P5bK39I/Zq1JpAdd8TW0ad0soGcn/gTkAflXRLEZzVXIoKPn/TKvVfQ8Q1HVNR1jWJtS1e7mvLyd8vLIcsx7Aeg7ADp0Fer/Dz4Gar4hlh1XxSk+l6WCHFqw2T3I9MdUU+p+Y9sda9r8J/CXwT4PuEvNP0v7Rfp0vLw+bIP93PC/gBXc4q8Dw6lL2uKfM+3+fcIUNby1KunadY6TpcGm6baxWtpAgSKGJcKgHYCrVFYnizxRpfg/wAK3Ou6tLthhGFjX70rn7qKO5J/x7V9PKUacLvRI32OP+MnxBHg7wgbHTpwus6gDHAAeYU6NLj26D3+hr5JAwO/Jzyc1r+JvEmpeLfFV3r2quDPcMNsa/dhQfdRfYD8zk96ya/Ns1zB42tzL4Vt/mcNWfOw4ooorzLGQjjMbD1U/wAq+6fCcxuPAWiTt1ksIGP4xrXws3+rb/dP8q+5fBiGP4c6BG3VdOtwf+/a19XwrfnqeiOrDdT5W/aZhEXxyjkH/LXS4WP1DuP6CvIwBivYf2m3DfG62UHlNJiB/GSQ148Olfs+Xf7tD0PiM0X+11Ldxsg3RMBwSCK/QTwVf/2r8N9B1EtuNxp8EhPuYxn9c1+fncfWvuP4KTm4+AXheQnJFn5f/fLsv9K8/PY+5CXmenw5L97OPkeQ3fiA/D39ty+Z38vTtbaGK5HQfvUUK5+ki/kWr6dHSvkL9p+28n4yWM6fI02mRNuHYrJIAf5V9I/DTxMfF/wr0XXpGBnmtws+P+eqEo//AI8p/OvOxlO9GnWXVWfyPWy+tavWw76O6+Z1lFFFeaewFFFFABRRRQAUUUUAFFFFIAooopgHavgz9qHwaPDHx0m1mCIpZ6/GLxWA+XzgNko+uQrf8Dr7zryP9or4et48+DtzJYwGXVtIJv7NVHzPtUiSMf7yZwPULXBmWHdeg0t1qZVoc8Gj4BopqOHQMOhGRT8e9fGbHliUo4pD1o6Gi4FrTdS1LRdSXUNH1K8067Qhlns5micEe6kV9AfDr9rHxJo9zBpvxAtTrWn/AHTqNugS6iHqyjCyj6YP1r51pCM8etbUMVVoO8GXGpKD0Z+ovhzxLoXizw/Brnh3U7fUbCcfJNA2RnupHVWHcHBFa1fmr8N/iZ4m+F3icaroNxvtZWX7bp0rHybpR6+jAdHHI9xxX6A+APHugfEbwVb+I/D87NC/ySwycSW8o+9G47EfkQQRwa+qwGYRxKs9Jdj0KNZVPUj+J8bS/BzxKigk/wBnTHj2XP8ASviePpmvvDxPZnUPBOsWAXcZ7KaID3KEV8GW2fIQnrtGRXznFUf3lN+TM8R0Nnw7qf8AY3jHSdXJwLS8imJ9g4z+ma+5b22i1HSJ7N8NFcRNEe+VYEfyNfA7oGjZD3GK+0/hh4gHiX4VaPqDMGnWAW8/PIkj+Rs/XAP41fDFVfvKD66hhpbo+NLyyl0zUrjTJ1Ky2krW7g9ihKn+VQ16N8ctBbQ/i/d3SR7bbVEW7jI6bvuyD67hn/gVec183i6DoVp030ZhKPK2goPSiiuYQmK734WfEi58A+IjHdNJLol0wFzCOfLPTzUHqO47j3Arg6Q4PUVvhsROhUVSm7NDjLld0ffVnd21/YQ3tnOk8EyCSOWM5V1IyCD6VPXzX8BviK+m6mngnV5ybS6c/YJHP+qkPJj/AN1uo9Dx3r6UHNfpWX42GMoqpHfquzO6E1JXQUUUV3FhRRRQAUUUUAVdR1Gz0rS7jUdQuEt7W3QySyucBVHU18d/En4gXvxA8VG6/eQ6XbFksrUnGB3dh/fbH4Dj1rsvjz8Qm1rXD4O0qY/YLGQG7dTxNMP4f91P/QvpXjYGBXw+f5o6s3hqT91b+bOStUv7qCiiivmTnuFFFFAhCjSYiQEs/wAgx6nj+tfe+m24s9HtbMdIYUj/AO+VA/pXxT4D0w6z8UvD+m7NyyXqM4/2UO9v0U19vn7tfZcLU7QqT7tI68OtGz4t/aGuhdftAaiqnIt7W3g/HZuP/odeYjpXQePdXXX/AIqeIdXR90c9/N5beqK2xf0UVgV+yYSHJRhHyPgMZU9pXnLu2IRyPrX258CMj9nvw0D/AM8ZP/Rr18Sn5efSvun4Q2Zsfgb4XgIwf7PjkI/3sv8A+zV5eeP91FeZ7HDq/fTfl+p4F+1MR/wtTRR3Gl8/9/Xrt/2WNZ+0eBdZ0Fyd1jeidAT/AASrn/0JG/OvO/2nLlZ/jPbQg58jTYlPtueRv8Ktfsvaj9n+Kmp6aThbvTiwHq0cin+TmonT5suXlr+JdOtyZs/N2/A+tqKKK+ePrQooooAKKKKACiiigAoooqQCiiiqAKQ9KWigD4F/aE+Fsnw9+I8upadbbPD2ru09qUX5YJScyQn05JZf9k/7JryDPpX6aePPBOkfEHwJe+F9aQiC4XKTIPngkHKSJ7g/mMg8Gvzr8beCdf8AAHjW68NeIbcxzwndFMoPl3MR+7JGe6n9DkHkV8lmmBdGftI/C/wPOxFLlfMtijotzpMWoCDXrKa406b5ZXtiFng9JIyeCR/cbhhxwcEdD4r+Geu+G9Cg8T2U0Wv+FrobrbXNOQmPH92ZPvQuDwQ3AORmuOBwK7v4XfFvxB8L9ec26DUdBuzi/wBIlPySjoWTPCvjjPRuh7EcNH2cvdqaLv2/4BlDlekjgQQelIwPavrHVvgd8N/jD4bbxp8INXh0m6lyZbBlItxL1KSRj5oH/wB3K9wCOa+b/GPgbxb4C1f+z/FeiXGnuxxFK3zwze8cg+Vvp19hV4jBVKPvPWPdbDnSlHXoc9ivQfg78Ub74WfEOLUt8smi3eIdTtV5DR54kUf30ySPUZHevPwcjNGM1z0qkqU1OO6IjJxd0fqjaXdtqOmQ31nPHPa3EayxSocrIjAEEHuCCK+GfEWlSaJ411fSJF2m1vJYh/uhztP5EV7V+yd43Ot/Da58HXk2660Jx5G48m2kyVH/AAFg6/TbXL/tA6G2mfFpdURCINUtVlyOnmJhG/HGw/jXqZ9bE4OGIj0f5nfUfPBSR5ZXuH7OXilbXW9Q8I3UuEux9rtQT/GoAdR9Vwf+AmvD6t6Rqd7oniGx1nT32XVnMs0Z7EjsfYjIPsa+ay/FPC141enX0MKcuSVz6l+OXg5/E3w5a+s4fMv9Kf7VGAMl4/8Aloo/4CM/Va+T8g9Dmvunw3r1h4q8JWWuWBDW93EHCnkqejIfcHIP0r5Q+LfgiTwR48dbeJv7Kvy09o/ZOctF9VJ49iK9/iLBqajjKez3/Rm1eN/eRwtFHaivkznCiijNK4gWSWKVZIZGjkQhkdTgqw5BHuDX2r8N/FB8YfDXTNcl2i5kj8u5A7SodrfmRn8a+KcZr6O/Zq1Iv4a1vRy3y29yk6L6B0wf1Svo+G8Q4Yl0+kl+RvQlaVj3OiiivvDsCiiigArmfiD4jbwn8NdX16PHn28BEAPeRvlT/wAeI/Kumrxz9o++MHwvtLFSQbu/QH3CIzfzArjzCs6OGqVFukyZu0Wz5iLSPI0ksjSSOxZ3Y5LE8kn6miiivy1vU87cKKKKAsAJ5oopGIVc9qTCx7F+zroB1Dx/e+IJEJi023MSN/01k4/RQ3/fVe++PPECeFvhtrWvOwBtbR2jzxmQjag/FitYPwb8Kt4V+FlnFcRlL2+JvrgHqC/3V/Bdo/OvOv2o/FQt/D2leD7aYCW8l+2XKjr5ScID9X5/4BX6lw7gHClTpNavV/16GuJqrDYaU3vb8T5ihUrEoY5bHJPc1JTVPFFfpqPzzcCjzEQRDLyny0A7k8D9a/RDRdPGleGdP0xelrbRwDH+ygX+lfDXwy0Y+IPjD4c0zaWQ3qTyD0SL943/AKDj8a+7L68g07SrnULptkNvE00jeiqCxP5CvnM7necII+r4dp8sJ1X6HxD8adUXV/jz4jlRspbzpaD/ALZxqp/XdWp+z1KYf2gdLUHia2uYz9PL3f8Astea3t7Nqms32q3BJlvLmW5bPq7Fv616P+z8rP8AtB6MV/ghuWP08oj+tejWp8mDceyPGw9T2mOjNdZfqfa1FFFfIn34UUUUAFFFFABRRRQAUUUUkAUUUUwCiiigArg/in8K/D/xT8J/2bqq+RfQZex1CNQZLZz/AOhIeNy9/YgEd5RUVKcakXGS0Ymk1Zn5keOPAviT4d+LJPD/AIms/JnALwzx5MNzHnh427j1HUHgiucr9OfGPgnw3488OSaJ4m0yK9tm+ZCfleFscPG45Vh6j8civjf4o/s1+LvBcs+qeF4rnxJogy2IUBurdf8AbjH3x/tIPqBXy2OyudFuVJXj+RwVcO46x2PLvBvjfxN8P/Eqa34Y1N7SfgSxH5orhR/BInRl/UdiK+xvAPxq+Hfxr0MeFfFWnWVrqs67ZdH1JRJDcn+9CzDDfThh+tfCYl3sVKsrA7WUjBU+h9DUqx5dXBIZTkEHBB9QexrmwuOqYf3d49jOlVdPTofXPjz9kLS7lZb74car/ZkpJb+zNQZpYD7JJ99Podw+lfNnjDwF4t8AXn2bxZodzp+ThZ2XdBJ/uyDKn6Zz7V6t8Nv2ofFXhGOHSfFsUniLSUwoneTF5Cvs54kHs3P+1X1J4S+I3w5+KmiyW+i6nY6mHXFxpl2gEqjuHhfkj3wR716P1bCY1c1J8suxv7OnV+F2Z8c/s0eJzof7RelQCcrb6tHJp0i9mLLvT/x5B+dfV3x48Nf278K5dTgj33elN9rXjJMeMSD8sN/wGqtx+zl8N08c6b4t0Cyn0DUbC8jvFTT5MQSMjbsNE2QAeny4616zNDFPbPBMivG6lGRhkMCMEGuyhl8lhp4ers9jalScYODPgIEHoQfpS10nj/wnJ4J8f32iFX+y7vOs3I+/C33fy5U+4rms56A1+d1qcqM3TlujkkmnZnsXwJ+IS6F4iPhPVLgJp+oPutpHPEU/TbnsH4H+8B617v488GWHjrwZc6LeYSU/vLa4xkwSj7rD27EdwTXxEAJZfLBy3YA85r67+D/jjUPFXhj7BrVleQ6lYoqtcSwsqXKdA4YjG7sR+Pevrchxir03g6yuun+R1UZXXKz5V1bSNQ0HXLnR9VgMF5bP5cqds+oPcEcg9wapZHrX1l8XfhgnjbRf7R0iNI9etVxExIUXCdfLY9vYnofY14pbfAf4k3BzJptlbD/preJ/7LmvJxuS4ijVcKUXJdGZTpSTskec5oyK9bg/Zy8cSAedqWiw/WWRv5JVxP2a/FB+/wCI9JU/7MchrCOT42X/AC7ZPsp9jxckCvbf2aZyPF2vW+eHs4n/ACkI/wDZqT/hmfxAw58VacPpbyH+td/8KvhNf/D3Xr/ULzWLa+FzbrCFiiZCuG3Z5Jr08qyrF0cVCpOFkvTsaU6UlJNo9Wooor7k6wooooAK8A/abnIsvDdsDw0s8hH0VB/Wvf6+dv2nSftvhnKtt23OWxx1j715Geu2Bqf11M6vwM8JzzSUgYEZHP0o9q/NkcItFFFUAV3Hwm8Fnxp8RIYJ49+m2BW6vCRwwB+SP/gRH5Bq4u3t7m9vIrOzgknuZnEcUUYyzsTgAfU19k/DLwPD4F8DQaewRtQm/fXsy/xykdAf7qj5R9M969nJcv8ArVdSkvdjv/ka0Yczv2Osubi3srCW6uZUhghQySSOcKigZJPsAK+B/Hniufxx8R9V8SSbhDPLstUP8ECjCD8sk+7Gvfv2kfiKmn6J/wAIBpkwN5fRiS+ZT/qoM8J9XI/75B9RXy+vSv2jJsLyxdaS32Pns/xqnNYeL0W/qLRRTS2BwrMeyr1PsK9xux86e+/sv+Gjd+KdX8VTx5jsohZwEj/lpJhnI+ihR/wKvS/2h/FK+H/g1c2MUm261eRbGMA4IQ/NIfptBH/AhXSfCjwl/wAIX8K9M0mWPbdun2m79fOflgfpwv8AwGvmT4/+MP8AhKfi3PpttKJNO0ZfssW1shpScyt+YC/8Br5imvrmNcui/JH19V/UMvUPtP8AN7nlor2P9mixNz8aprvHFppsrE+hd0Uf1rx0Y6V9Lfsr6NtsPEOvvGR5ksVnG/8Augu36stevmc+TDS89Dwsop8+Lh5an0ZRRRXyB98FFFFABRRRQAUUUUAFFFFK4BRRRTAKKKKACiiigApMcUtFAHAeNfgt8N/H8z3XiDw3bm+Yf8f9rmCf8XXG7/gWa8U8Q/sbQFy/hTxnJAnaDU7bzP8Ax+Mr/wCg19VUVyVsDQraziZypRluj4fuv2Q/ilFJtt9U8L3Cdm+0zJ+hjNXNH/ZF+JkWow3s/ibw/pc0TbkntZJ5JYz6qQq4P4ivtSobi6trS2a4uriKCFRlpJWCqB7k8VzLKMNHWz+8j6tDc5P4feGfF/hjRvsXirxzN4ocKBHJNZrC0eP9sEs/1bmuyrzXxH8e/hP4ZDreeMbK6mTrBp2bt8+n7sED8SK8q139szw/AWTw14N1PUD2lvp0tVP4De38q3eLw9Fcrnt8y3OEVqz3Dxx8N/DfxAWy/t1LhXs3LRyW0gjcqRyhOD8uQD9RWZpnwW+GmmFWTw1DcuP4ryR5v0Y4/SvlzWv2uPifqWV0qx0TRU7GOE3D/m5x/wCO157rHxf+KWvqw1Px5rZR+sdtP9mT8ogteVWxuC53UVPml3sYyxFPtc/Q6HTPC/h6ASQWGk6ZGv8AEsUcIH44FY2p/Ff4aaOD/aHjvw/CR1QXyO35KSa/Nq4nubuQyXl5dXTnktcTvKT+LE1GAMcDH0FS86cdKcEiHi+yPv2+/aa+DNkDs8VPeMO1pYzyfrsArBuf2vPhZCD5Fp4kuT22WAQH/vpxXxDRjisXnVd7JEPFyPsa5/bL8GoT9l8I+IZh28wwx/8AsxrMk/bS0wH9z8Pr9v8Afv0X+SGvkzGaNuayeb4l9V9wvrUz6sb9tSI/c+HE346oB/7Sph/bTx/zTlj/ANxT/wC1V8rbBTSg7g0lmuJ/m/AX1mp3Pq5P21IP4/hxOP8Ad1MH/wBpVdg/bQ0RiPtPgLVYx/0zu43/AJqK+RNvtTqFm2JX2vwD6zPufZtr+2P4Bfi88NeJYD/sRRSD9HFbNr+1p8JLhgJpddtM95tNcgf98k18MEUm2tFnOIXZlfWpI/Quw/aJ+DeoYEfjiygY/wAN3FLBj6llArqbXxv8P/EMIjtPFPh3UVb/AJZreQyZ/wCAk1+ZmPfFJsU9QD74rZZ3U+1BMaxb6o/TG/8Ah34A1pPMuvC2kTbv+WkUCoT/AMCTFcpqP7Pnw8vCxtYtQ09z0+z3RYD8H3V8EWGr6zpkgk0zWtTsWHQ2t5LF/wCgsK7TSfjn8W9EK/ZfHeqTKv8ABelLoH/v4pP61MsdhK38WivwK+swe8T6S1b9mu7XLaF4ohcdo763I/8AHkP/ALLXC6r8EfiXphZk0W31CMfx2NwHP/fLbT+lc5pf7XnxOsNqajpeg6uo6l4nt3P4oxH/AI7Xomhftk6JPsXxH4K1GyP8Uljcpcr9cMEP86wlgsrrfC3H+vmP9zLyOx+CPwtudEX/AISzxPYmDVG3JaWsow1uh4LsD0dunsPcmvQ/iF4503wB4KuNavyJJeY7S2Bw1xKQSEHt3J7AGuV0X9o34Qa1sT/hLYdOmb/llqcT22P+BMNv61s+IvCPw8+LWnwXF1PBqqwKywXenXxzGGxnBRiOcDqD0r6TK6eFoQjTg7xW9t2XVUvZNULc3Q+I9V1fUdf8QXet6vcGe+vJDLNKeMk9gOwAwAOwAquDivpDW/2VITvk8NeLp4j1WDUYFkH03pg/oa8w8Q/A74m+Ht0jaA2pwKcebpjif8dnD/pX3lDH4aSUYysfD18sxdNuU4t+a1PPsZ5r1H4C+BX8X/E1NRvIN2k6PtuZWYcSTZ/dx/mNx9lHrXnFnpGs33iS18PQaZdjU7mUQxWssRjcsfUMMgdyewBNfcXgnwvpHwu+FcVhcXcSJaRNc398/wAoeTGXc+3GB7AVhmmLVOnyQesjpyfAutW9pUXux/Mo/GDx/D4B+HNxcQTBdWvA1vYJ1PmEcyY9EB3fXA718QoWcl3dmdjlmY5JJ6k+9dZ8TvHt38RfiDNq8geKwgzBYW7f8s4s/eI/vN94/gO1cmuBV5bhPq9O8viZnm2O+tVvd+FbCOQuSxAFfcPwU8OyeGvgro9pcR7Lm4Q3s477pTuAP0UqPwr5M+G3hGXxt8T9L0Xyy1qJRcXhxkCFDuYH68L/AMCr7yRVVFVVCgDAA7Vw51W+GkvVnp8O4d+9Xfov1HUUUV4B9SFFFFABRRRQAUUUUAFFFFSAUUUVQBRRRQAUUUjMqgliAAMkntQAtFeS+Ov2jPhn4IMtr/a41vUkyPsWkkTFT6PJnYn4nPtXzt4s/al+Jfi+Z7HwbYHQrduB9hhN5dMPd9pC/wDAV/GuKvmFGlo3d9kZTrQj1Ps/XPEmgeGtNa/8QazY6ZbKMmW7mWIfhk8/QV4n4p/a2+HekCSHw7bah4iuV+60SfZ4Cf8Aro4yR9FNfL0Xwy+M3jbUP7Qm8KeKtSuJTn7Xqismc/7UxHH0rt9J/ZO+K9+FN8dF0oEfMLi78xh+Eat/OvOqY/F1dKFOxlKrUfwRIvFH7V3xS1pni0Y6b4dgOQBaw+fLj/rpJkZ+iivJda8U+I/E9z9o8Sa9qOrSHveXDSAfRScD8BX0fp/7F+oSBTq/j+3i9Vs9PL/kXcfyrrtM/Y58AWyg6p4g8Q6g3oskUCn/AL5Qn9a5ZYPHV/4n5mbpVZ/EfF/yYwNoFMbyh1kRfqwFfoDp37MvwasFAfws96R/FeXs0mfw3AfpXU6f8IPhdphBs/AHh1COjPYxyH82BNOGR1ftSQlhJdWfmmkkTNiOVHPorAn9K1rLQdf1EgafoOq3ZPTyLOV8/ktfp1Z6BoengCw0bT7UDoILZEx+Qq+FA6A/ga2jkXef4FLB92fmlb/Cv4n3ihrb4feJHB7mwkQf+PAVqWvwG+MlyR5fgDUkz/z2kjj/APQmr9G6MD0reOSUusmWsJHufn5D+zV8apQD/wAIlbxj/ppqMA/kxq5H+y18ZZCC+k6PF/v6mv8ARTX3vRVrJcP1uNYWB8Jx/sn/ABccfOvh6P8A3tQY/wAo6tL+yR8VCOb3wyv/AG+Sn/2lX3FRVf2NhvP7x/VaZ8RL+yF8TSPm1fwyv/bxMf8A2nSn9kH4mY/5DHhk/wDbxN/8br7cop/2Phuz+8Pq1PsfDr/sjfFNfuX/AIZb/t8lH/tKq0v7J3xbT7g8PS/7uoMP5x1910Uv7Gw3n94fVqZ8Dy/st/GaPO3R9Jlx/c1NOfzUVn3H7NvxqgUsfByS4/55ahA3/swr9CKKl5LQ6Ni+qwPzhuvgf8X7TmX4f6u3/XFVl/8AQSax7v4c/EOwH+m+BPEcA9W06bH5ha/TbA9KKzlkdLpJkvBx6M/Ku5s72xYrfWd1akdRPC0eP++gKqfaLcttFxCT6eYK/VuSCKZNk0aSKezgMP1rFvfBPg7Ugf7R8J6Hd56+fYRP/NaweRP7M/wJeE7M/MHEech1P0OaXcMcEV+it/8AAX4P6ixafwBpEbH+K2Rrc/8AkMiuT1L9k74R3pLWtpq+nMe9rqDsB+Em4VhLJKy2aZDwk+jPhMor9QD9asafc3ek3Yu9Lu57KdTkS2srRMPxUivrfUP2MtDfcdI8c6pb+i3drFMPzXZXH6n+x144gV20nxXoV4B0WeKSBj+jCsJZZiobR+5kOhUjsjgdC/aG+Lvh8KkPi6TUIV/5ZanCtyD7bjh//Hq9R8O/tlX0e2Pxf4LhmXjNxpNwVP18uTP5bq811b9mn4yaShkHhqLUkB66feRyH/vlip/SuL1LwD440P8A5DHgzxBZgdWk0+Ur/wB9AEfrTVfG0N7/ADGp1obn214c/aG+Dnim6gkfW4tMvlyI11iD7O6ZHOJDlfyauq8b+EtM+KPgUaXB4kuLezeQSibTpEkjmx0D9Qy55xkcgV+bUsqRMUfdG3Qq42n8jVnSde17QbwXnh7XdT0mUfx2Ny8OfqFOD+NduHz6pBpzjt2KddSi4Tjo9z6Y179mPxvp7s+g6jpmsQj7qMxtpfybK/8Aj1eca54A8b+GlZ9c8MajZxr/AMtvKMkX/fa5X9a0PCf7U3xS0BUh1e4sfEduuBi+hEc2P+ukeOfcqa9o8N/tdeA9UCweJ9J1PQZG4aTaLqD80+bH1Wvp8LxbGTtN/foeXPJ8JU/htxZt/s4+BJPD/gyXxTqUO2/1gKYlbrHbDlP++jlvptr26ua8M+PfBPi23U+F/E+lalxnyradS6j3T7w/EV0mR61NXEfWJuo3ue5haEaFKNOGyFoooqDoCiiigAooooAKKKKACiiilqAUUUUwCiiigCK5FwbWT7KYxNtOzzM7c9s45xXmniH4RX3jotH458f67dWDHP8AZOjldOtSPRtu6R/+BP8AhXqFFROEZq0hNJ7nmegfs+fB3w26Saf4E0yWVOkl6Guj/wCRCR+lehWWmadpsAg06xtrSMdEt4ljX8lAqwzqqlmYADqT0FUxrOkGXyhqlkX/ALonTP5ZohSjH4Yi92JdwO4paQMGAIIIPQ0tWUFFJuX1o3L60CuhaKTIoBz0oGLRRSbl9aBXFopN6+oo3L6igLoWik3DOAaNwHU0DuLRTdw9aXcOxoFdC0UmRS0DCikJpN6+ooFcdRSbl9aNy+tAXQtFIGB6GjcB1NAXFopAwPeloGFFITg8nFG4etAXFoooJxQAhAPYUYH0+lRzXEFvEZJ5o4kH8TsFH5mobfVNNupNltqFrM392OZWP6GiwnJIg1Lw7oGsoU1fRNOv1PUXVsko/wDHga4XVP2fPg7q7tJceBNNgdv4rLfbH/yGwH6V6Xn86U8Cs5Uac/iimJxT3R4FqX7IvwxuwzWF74g05j0EV4JVH4SKT+tcdqn7F0DIx0jx/Op7R3tiHH5o4/lX1ZvX1pdy+ormlluGlvAzdKm+h8S3P7HXxBs7kT6br3h25dDlH3zW8g+h2HH511Gh+CP2tPB4WDR9ds723QbVgvNSW6jx6ASruH4EV9Zbl9RQCCeuazjllKDvBtejJVCC2dvmeK6D4o/aStdsfiX4X+HdRUcGax1lLZj77W3j+VeraDqGr6jp5l1nQJNGuAQPIe5jnz7hkOPzxWrRXbCm4fab9TZK3UKKKK0KCiiigAooooAKKKKACiiigAooooAK8w+LXxfsPhzp8dnaRR32u3KloLVmwkS/89JCOduegHJx2AJr0q4nitrWS4mYLHGhdiewAyf0Ffn94o8RXXi3xlqfiO9kYvdzmRQ3/LOPoiD2C4H516GXYRYip72yPIzfHPC00ofEyfxL428W+NLwzeIdaurvccLbI5SFc9ljXA/mapf8Ij4gW1+1f8IpqogxnzvsUm3HrnbX1L8DfhZpvh3wpZ+KNVs459bv4xOjyqG+yxsMqqZ6MRgk9ecdBXsxHGK7auaRpS5KUFZHnUclqV4+0rzd2fBPhP4h+MfBV0kmg61cLErfNZ3DmWB/UFCTj6rg19kfDbxt/wAJ/wCA7fxA2mT2DuxieOQHYzL1aNv4k9D9R2rK8VfBfwR4s8UWeu3+nmG4ik3XK22I1vF/uygdecfMMEjIzzXf21tb2dpHa2sMcEMShEijUKqKOgAHQVxY3FUa6TjG0up6OXYKvhpSU53j0/rofAfi+acfEfxEFuLgAardAATMMfvW96pQ6b4guYFuLaw1iaJuVkiWZ1b6EcGrPi3P/CyPEX/YWuv/AEc1fYPwNOfgB4cyT/qZP/Rr17OJxP1ajCSine35Hz+DwrxlecHJq1/zPjZLvxFol0ki3es6bPnKsZpoW/DJGa9j+GX7QOt6drNto3je7+36ZMwjGoS4E1sT0LkD50z1zyOuTX0rr+gaR4k0G40nWrKK6tZlKssi52/7SnsR1BFfn3PCkF5PbK29I5HiDf3gGK5/HFY0KlLHxlGcbNG+JpVsrnGcJ3TP0VZg1szKQQVyCD14r86PtFwWc/abj77f8tn/ALx96+6fhje3OpfBPw5e3bF5pNOi3serYXGf0r4SPWX/AH3/APQjWOTwtKpF9Doz6o5QpSXX/gGtFo3iieFZoNK12SNwGV0jnKsD0II6in/2D4t/6AviD/vzPX2J4G8aeD7P4ZeHrW78V6NDPHptujxyX0asjCNQQQW4I9K3x488EHp4x0I/9v8AF/8AFUSzOabXswp5RSlFN1v6+8+f/wBm/TdcsvihqMmp6fqlvCdMZQ13HIqlvNTgbuM9a1/2rJJY7Pwp5UsiZnuc7HK5+RPQ19A6dqem6vZC80q/tr63LFRNbSiRCR1GQSMivn39q7/jw8Kf9d7n/wBASuahW9vjIzat5fI68Vh1h8vlCMr+fzPne2TU7yQx2Y1C5kA3FIXkcgeuATxVoab4kRS/2HW0A5LbJxivUv2Zc/8AC4LkZP8AyC5f/RkdfXGPr+dd+MzD6vU9moJnm5flf1qiqrm0fBWg/Ebxz4YuA2keJtQjVG+a3nlM0f0KPkfyNfSvwk+OFr45uE0DXYIbDXthZBGf3V0B1KZ5DAclfTkE841Pip8JdE8ceH7q7s7GC28Qxxlra8jQKZCBkRyY+8p6ZPI6ivjayvL7StThv7OR7a9tJRJG44aORTx+RFTGFHMKbcY2kipzxGV1UpS5oP8Ar7z9FH5Q1+dVxcXBu5ibq4/1j/8ALZ/7x96+/PC2uxeJvAml6/EABe2qTlR/CxHzD8DkV+ft2CZrkAc7nH6mssmhaU1JbWN8/neNNxe9/wBDUj0XxRLAk0Wk67JG4DK6RzsrA9CCOCKd/YXiztoviD/vzPX2P4J8beDbb4a+H7a68WaLDPHptukkUl9GrIwjUEEFuCPSt7/hPPBH/Q46F/4Hxf8AxVOWZzTa9mTDJ6Uoput/X3nz/wDs16brll8S9Vl1TT9Tt4jphVWu45FUt5qcAtxmtX9qiWSNPCwjlkTLXOdjlc8R+hr6B03VNN1ey+2aVqFtfW+4r51tKsiZHUZBIyK+e/2rPueFf965/lHXNh63t8ZGTVvL5HXisOsNl8oRlfz+Z5R8NPiTqnw98XLqAknu9Nn2x3tq0hbemfvLk8OuSR68jvx9s6Rqthrmi22raXdJdWdzGJYpkOQyn/PTtX54BWIYhSQuMkDgZ4Ga9c+CPxWbwVrQ0DW7hm0C9l4dufsch/j/ANw/xen3vWu/MsApr2lNao8zKMydKXsar917eR7n+0AzJ8A9YZWZT5lvyrFT/rk7ivlr4b3FwfjD4WBuJyDqkAwZWI+97mvqH9oF0f8AZ91do2DBpLYgg5BHnJXyz8NR/wAXh8K/9hWD/wBCrPLknhZ38/yNs2k1jadn2/M+9+1fPXxb+P0+k6rceGfA0kLXMJMd1qTgOInHBSIdCwPVjkA8AGvTPi94rn8HfCHVdXsnKXhVba2YdVkkYIG/AEn8K+J9I0u71vxFY6PZDzLq9uEgj3n+Jmxk+w5J+hrmyzCQqJ1auyOzOcfUpNUKPxMW8vNe8Uarv1G71HWr6U5Akkedz9F5wPoKLvw5rekRrc3+galp6dRLNbSQgf8AAiBX3P4H8AeHfAfh+PT9FtIxKVH2i8ZR5tw3dmbrj0HQV000MVxA8M0ayxuCrI43Kw9CD1raWcKLtCHunPDIJSjzVKnvHxh4E+N3jHwddQ291dy61pIID2l3IXdV/wCmchyQfQHIPpX1D4yvxqXwM13UYori3E+jTSiOdDHKgaInDDqrD0rK034JeA9K+IreLbTTNsigPBZHBt7eXPMiL2PTA6A5IArofiIMfCTxNj/oF3H/AKLauPEV6VarF0427nfhMNXoUZxrSvo7HwRHNcZCi5uSScACZ+f1rW/sHxcOuieIP+/M/wDhWZZkLfWzMQAJVJJ7DdX3uvj3wOVGPGOhdP8An/i/+Kr2sbinh2lGF7nzuX4OOK5uepy2Phv+wfFv/QF8Qf8Afmf/AAr6I/ZisNWsbHxKNVs7+2LyW5QXaSLu+V843/h0r13/AITzwP8A9DjoX/gfF/8AFVt2d5aahYxXthdQ3VtKu6OaFw6OPUEcEV5OKx8qtNwcLHvYLLIUaqqRqc1ieiiivLPbCiiigAooooAKKKKACiiigAooooAKKKKAMjxVDLc+B9Yt4ATLJYzogHqY2Ar881DNZcDnYOPwr9IiARgjOa+Gvir4JuPAfxGvbLyGXTruRrjT5ezRk5K/VScEemD3r2smqxUpU31PnOIKMnGFWOyPtHw1d2194N0q8s2VreazieMr02lBitWvlD4OfHGHwjpaeF/Fizy6WjH7JdwoXa2BOSjKOSmSSCOR0wRjHuR+NHwvFn9o/wCEy07bjOzLb/8AvnbnP4VwYjB1aU2uVs9PCZhRrU1LmSfU72ivmnxj+0vOvieyj8E2YfTIJN91Lex7TdjpsUdY177uuccYzn3LwT410Xx54Vj1zRJWMZby5YXGHhkABKMPUZHI4IINRVwtWlFTmrJmtDHUa83CnK7R8PeL/wDkpPiLH/QVuv8A0c1em+Cf2gb7wX4C07w1H4UgvEs0ZBO14UL5ct93Ycfe9a8z8YfL8SPEX/YVuv8A0a1e0/Dn4CeFfGnwx0rxLqOp6tDc3iOzxwPGEGJGXjKk9F9a+ixLoKhD26utPyPksHHESxE/qzs9fzMHxR+0j4u13R5dM0vSLLRUmUxyXEczTShSMEKSAFPvgn0xXmPhbwzqni/xPa+HtFt2e4mIBYAlYE7yOeygfn06mup+LPwym+HHiqGG2ee50e8Tda3M2N24D542wAMjqPUH2Nej/s1eM9MtZrrwVeW9vBd3DtcWt0qBWnxy0Tt1JHVc9sjtUuUKGGdXDR3/AK/AtQqYjFqji5ar+vxPoXR9JttC8LWei2efs9nbJbx56lVXAJ9+K/PM/wCsk/66P/6Ea/RuTiF/oa/OPPzSY7u//oRrjyVtubO7iFJKml5/odzZ/Bn4j6nplvqNj4Wknt7mJZopBPCNyMMg8vnkGp1+BfxSB58ISj/t5h/+Lr6I8G/Fj4c6b8O9CsL7xdp0Nzb6fBFLGzNlHWMAg8dQc1t/8Ln+F3/Q6ab+bf4USzHFJtcn4MIZTgnFN1PxRU+B/hvWvCnwmh0jX7BrK9W6nkMLOrEKz5BypI5FedftXf8AHj4U/wCu9z/6AlewaH8S/AniPWo9I0PxNY3t7IrMkERO5goyTyOwrx/9q3/jw8KH/pvc/wDoCVyYRyli4ymrNs7sdGEcBKFN3SSX4o5L9mb/AJLFc/8AYLl/9GR19c18XfAnxToPhL4l3GqeItRisLRtPkhWWQMQXLoQOAewP5V9HN8cvhYsZf8A4S+1YDssUpP5bK1zSlOWIbjFvYyyWvThhUpySd31PQXZUTcxAUcknsK/PPXLiG78U6pd2u3yJbyaSPHQqZGI/QivdPij+0NZ6zoN14b8DpdItyhin1OZDGQhGCsSnnJHG44xzgd68X8FeFNQ8Z+MLTw3piHdMwMsoHEMQ+859AB098DvXXllCWHjKrV0OHOMTDFVIUaOtv1PsL4LwS2/wB8NpMCGNqXAPozsw/QiviS4IF1OT0Ejn/x41+h9jY22l6JbabZx+Xb20Kwxp/dVRgD8hX54XQLTXIHUu+PzNTlEuepUl3KzyHJSpQfTT8jtbH4M/EnUdNg1Gy8Kyy21zGs0MguIRuRhkHBfPINTn4GfFIg58Izf+BMP/wAXX0R4O+LXw4034eaDp994u06K6t9PgiljYtlHWNQQeOoIrb/4XR8Lv+hz0382/wDiaiWY4pNpQ/BmkMqwUopup+KKfwQ8Naz4T+FMeka7YGxu1u5pDEWVvlYjBypI5rzf9qzp4V9N1z/KOvZdD+JPgbxJrCaTofiWyvbx1ZlgiJ3EKMk8jsK8b/aq/wBX4W/3rn+Udc2EcpYyMpqzbOzHxhHAShTd0kkcZ+z7oem+IvHGu6Lq9stxZXWjPHJG3/XaPkHsR1B7EVx/xG8Cah8PfGcmjXu+a1kzLZ3hXCzxZ/8AQhkBh+PQivQP2YmH/C3NRX/qEv8A+jY6+hfiH4D0v4geDZ9F1EeXKP3lrdBctbygcMPbsR3Ga7K+LeGxbv8AC7XPOwuAWLwKa+JN2/yPlS1+JVxf/ATVfh9rkrSPGIX024Y5JRZVJhY+wBKn047CsL4bgD4w+Ff+wrB/6HWTr+g6n4X8RXeg6zb+ReWrbHXsw6hlPdSOQa1Phuf+LxeFf+wrb/8Aodek6cIUZuns9fwPKVSpOvBVN4tL8T6R/abilk+CCvGDtj1O2d8dhlh/Mivnn4TXVvZ/HLwxNdECL7aI8noGZWVf/HiK+y/HHhe38Z/D/VPDdw4QXcJVJCP9XIDuRvwYA18I6jYan4e8QT6ffxSWeo2U+116NG6nIIP5EH6GvMytqpRnR6/5nr51GVLEwxFtNPwP0OX7opa8J+H/AO0b4dv9JhsPG0zaZqcahGu/LZoJ8fxZUHYT3BGM9DXaal8b/hlptk1wfFNrdsFysNmGmdvYAD+ZFeTPCVoS5XF3Pep4/DzhzqasehVzPxE/5JJ4n/7Bdz/6LavDLD9p25f4jvLqOl+T4XlAiSJVDXEGD/riR97OeUHQAYyc59q8a39pqnwQ17UbCdLi1uNHnlilQ5V0MRII/CqnhqlCcfaK1zOGMpYmnP2bvZM+EIkaR0jQZZ2CqPUk8V3y/Az4o5/5FCb8LmH/AOLrg7RxHf27scKsqsSewDDNfcA+M3wuCj/is9M/Nv8ACvoMfiq1Fx9lG9/I+VyzB0MQpOtK1vOx8vD4GfFE9fCM2P8Ar5h/+Lr6w+GOj6hoHwj0DRtVtjbXtraLHNCWDbGyeMgkH8Kof8Ln+Fv/AEOmmfm3/wATWx4d8e+EPFd7NaeHNetNRnhQSSRwk5VScZOQO9eLi8TXrxSqRsl5M+iwODw2Gm3SndvzR0lFFFeeeuFFFFABRRRQAUUUUAFFFFABRRRQAUUUUAFc/wCMPBmg+OPDr6Nr9p50RO+ORTtkhfs6N2P6Hociugopxk4u63JlFTTjJaM+S/En7NfjHS52fw5eWut2v8Kuwt5x9QflP4EfSuPHwb+KTTmP/hDb8HOMmSLb+e+vuSkwPQV6cM3rxVnZnjTyHDSlzK6PlXwn+zR4l1C6jn8W30Gk2gILQWziadx6Aj5V+uT9K+mNA8P6T4X8PW2iaHZR2llbrhI0/Uk9SSeSTya06K5MRi6uIf7xnfhMBRwq/drXufI/iH4DfEnUfGms6jbaXZNb3N/PPExvUBKPIWUkduCK+h/hZ4f1Pwt8JNH0HWIkivbWN1lSNw4BMjMMEcHgiuxop18ZUrQUJbIjDZdSw9R1IXuzmvHfg2w8c+CLvQb0BGkXfbz4yYJR91x9D19QSK+XbT4DfF3TtShvbKxsobq3kEkM8V+gKupyGH5V9j0UUMZUoRcY7PuGKy6liZKc73XYyPD02uXXhKzk8R2MVnqpiAuYYZRIgccEqw7HqPTOK+Sz+z18T9740mxwXYj/AE6PuSa+y6KMNjKmHbdO2o8Vl9LFKKqN6Hxp/wAM9/FL/oFWX/gelH/DPfxR/wCgVY/+B6V9l0V1f2xiPL7ji/sDC+f3/wDAPmv4SfB/x34S+LWn67ren2kVjDFOkjx3ayEFkIHA966/4+fD7xR49tNAj8NWlvcGzlmaYSzrFgMqgYz16GvZaK5pY2pKqqz3R2Qy2lCg8Or8rPjJf2efiiBzpFj/AOB6U8fs9fFDoNLsR9b9K+yqK6f7YxHl9xx/2BhfP7/+AfKmifsweKLu5R9f1vT9Ogz8yW2biQj2yAo/WvoPwV8P/DfgPRvsOhWe13wZ7qU7ppyO7N/IDAHYV1FFctfGVq6tN6HbhsuoYZ3px17jXGUIHpXxzN+z58T2uJHTSrHazsw/05O5NfZFFLDYuphm3T6jxmApYuyqX07Hxn/wz38Uv+gVY/8AgfHQP2e/il30mx/8D0r7Morr/tjEeX3HD/YGF8/v/wCAfN3wh+EHjrwj8VrPXNcsLWGyihmR3julkOWTA4HvXVfHv4e+KvHiaCPDNpbzmzafzvOnWLG4Jtxnr9017PRXNLG1JVlWdro64ZbRjQeHV+Vnz/8AA74V+M/BHxDvNX8RWNtBbS6e1ujRXKykuZEbGB7A19AUUVlXryrz557nRhcNDDU/Z09jy/4yfCmL4heHVutMEUOv2YzbSv8AKsy9TE59D1B7H2Jrx/wV8DfiJo3xE0HV9Q0u0jtrO/inmZbxGIRWySAOtfWFFbUsdVp03SWzOevllGtVVaW4dq86+JXwg8P/ABFtxcTO2natEu2LUIVBJHZZF/jX8iOxr0WiuanUlTlzQdmdlWlCrFwmro+M9Z/Z6+JGj3DfZLC21iEdJbKdQT/wB9pH61l23wW+KN5cCKPwldQ/7dxLHGo/EtX3BgelJgegr0lnFdK2h47yDDN3TZ88+Av2bEs76HVPHV3BeGMh10y2yYmPbzHIG4f7IAHqSK9p8WaVcan8ONa0bS4Y/PudPmtoIyQi7mjKqM9AOlb1FcNXE1Ks+ebuz0aGCpUIOnTVkz4yX9nr4ohedIsM/wDX+lO/4Z8+KX/QJsf/AAPSvsuiu3+18R5fcef/AGBhfP7/APgHxp/wz38Uf+gVZD/t/SvU/gZ8MfGHgfxdqd/4is7eCC4s1hjMVyspLCQN0HTiveKKzrZlWrQcJWs/I2oZPQoVFUhe68wooorzz1QooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigD/2Q==';

function doGet(e) {
  // Mobile app JSON API (Android app, see MobileApi.gs) — checked first
  // and returns early, so it never interferes with the existing
  // page=manifest/page=sw routes or the default HTML page below.
  if (e && e.parameter && e.parameter.api === '1') {
    return handleMobileApiGet_(e);
  }
  const page = e && e.parameter ? e.parameter.page : null;
  if (page === 'manifest') {
    return ContentService.createTextOutput(getPwaManifest_()).setMimeType(ContentService.MimeType.JSON);
  }
  if (page === 'sw') {
    return ContentService.createTextOutput(getPwaServiceWorker_()).setMimeType(ContentService.MimeType.JAVASCRIPT);
  }
  return HtmlService.createHtmlOutputFromFile('Index')
    .setTitle('Credence Kurukshetra')
    .addMetaTag('viewport', 'width=device-width, initial-scale=1')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
}

// Served at <exec URL>?page=manifest — a real static manifest.json file
// isn't possible from Apps Script hosting, so it's generated on request
// instead. start_url/scope are relative to this URL's own path, which
// resolves back to the plain exec URL (no query params) — i.e. the app.
function getPwaManifest_() {
  const icon = PWA_ICON_DATA_URI_;
  return JSON.stringify({
    name: 'Credence Kurukshetra',
    short_name: 'Credence',
    description: 'Child, therapy fee, staff, and expense management for Credence Child Development and Learning Centre, Kurukshetra.',
    start_url: './',
    scope: './',
    display: 'standalone',
    background_color: '#faf8f3',
    theme_color: '#22484a',
    icons: [
      { src: icon, sizes: '192x192', type: 'image/jpeg' },
      { src: icon, sizes: '512x512', type: 'image/jpeg' }
    ]
  });
}

// Served at <exec URL>?page=sw — network-first with a cached-shell
// fallback for when the connection drops. This does NOT make the app's
// data (students, fees, sessions, etc.) available offline — those are
// live google.script.run calls, not cacheable fetches — it only lets the
// app shell reopen and show a "you're offline" state instead of a blank
// error page, and satisfies the installability requirement for
// "Add to Home Screen" / desktop install prompts.
function getPwaServiceWorker_() {
  return [
    "const CACHE_NAME = 'credence-shell-v2';",
    "self.addEventListener('install', function(event) {",
    "  self.skipWaiting();",
    "  event.waitUntil(caches.open(CACHE_NAME).then(function(cache) { return cache.add('./').catch(function(){}); }));",
    "});",
    "self.addEventListener('activate', function(event) {",
    "  event.waitUntil(",
    "    caches.keys().then(function(names) {",
    "      return Promise.all(names.filter(function(n) { return n !== CACHE_NAME; }).map(function(n) { return caches.delete(n); }));",
    "    }).then(function() { return self.clients.claim(); })",
    "  );",
    "});",
    "self.addEventListener('fetch', function(event) {",
    "  if (event.request.method !== 'GET') return;",
    "  event.respondWith(",
    "    fetch(event.request).then(function(response) {",
    "      if (response && response.ok) {",
    "        const copy = response.clone();",
    "        caches.open(CACHE_NAME).then(function(cache) { cache.put(event.request, copy); });",
    "      }",
    "      return response;",
    "    }).catch(function() {",
    "      return caches.match(event.request).then(function(cached) { return cached || caches.match('./'); });",
    "    })",
    "  );",
    "});"
  ].join('\n');
}

// ─────────────────────────────────────────────────────────────────────────
// SETUP — run once manually from the Apps Script editor (select `setup`
// in the function dropdown and click Run) before using the app.
// ─────────────────────────────────────────────────────────────────────────

function setup() {
  // Creates a sheet if missing (in the correct spreadsheet per the
  // three-database config) and writes headers only if the sheet has no
  // header row yet — never overwrites or erases a sheet that already has
  // content, per the "don't touch existing data" requirement. If the
  // sheet already has headers but the code now expects additional columns
  // (e.g. a new field added in a later version), those missing columns
  // are appended at the end — existing columns and all row data are left
  // exactly as they are.
  function ensureSheet_(name, headers, headerColor) {
    const ss = getSpreadsheet_(DB_FOR_SHEET_[name] || 'studentFee');
    let sheet = ss.getSheetByName(name);
    if (!sheet) sheet = ss.insertSheet(name);
    const lastCol = Math.max(sheet.getLastColumn(), 1);
    const firstRow = sheet.getRange(1, 1, 1, lastCol).getValues()[0];
    const hasHeaders = firstRow.some(function (c) { return c !== '' && c !== null; });
    if (!hasHeaders) {
      sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
      sheet.setFrozenRows(1);
      sheet.getRange(1, 1, 1, headers.length).setFontWeight('bold').setBackground(headerColor).setFontColor('#ffffff');
      sheet.autoResizeColumns(1, headers.length);
    } else {
      const missing = headers.filter(function (h) { return firstRow.indexOf(h) === -1; });
      if (missing.length) {
        const startCol = firstRow.length + 1;
        sheet.getRange(1, startCol, 1, missing.length).setValues([missing]);
        sheet.getRange(1, startCol, 1, missing.length).setFontWeight('bold').setBackground(headerColor).setFontColor('#ffffff');
        sheet.autoResizeColumns(startCol, missing.length);
      }
    }
    return sheet;
  }

  Object.keys(HEADERS).forEach(function (key) {
    ensureSheet_(SHEETS[key], HEADERS[key], '#2f4f4f');
  });

  // Deleted-record archives — same columns as their source sheet plus
  // who deleted it and when (Enquiries also gets a Deletion Reason).
  [
    [SHEETS.DELETED_STUDENTS, HEADERS.STUDENTS.concat(['Deleted By', 'Deleted Date'])],
    [SHEETS.DELETED_FEES, HEADERS.FEES.concat(['Deleted By', 'Deleted Date'])],
    [SHEETS.DELETED_PAYMENTS, HEADERS.PAYMENTS.concat(['Deleted By', 'Deleted Date'])],
    [SHEETS.DELETED_USERS, HEADERS.USERS.concat(['Deleted By', 'Deleted Date'])],
    [SHEETS.DELETED_ENQUIRIES, HEADERS.ENQUIRIES.concat(['Deleted By', 'Deleted Date', 'Deletion Reason'])],
    [SHEETS.DELETED_THERAPISTS, HEADERS.THERAPISTS.concat(['Deleted By', 'Deleted Date', 'Deletion Reason'])],
    [SHEETS.DELETED_EXPENSES, HEADERS.EXPENSES.concat(['Deleted By', 'Deleted Date', 'Deletion Reason'])]
  ].forEach(function (def) {
    ensureSheet_(def[0], def[1], '#7a3b30');
  });

  // Seed a default admin user only if USERS has no data rows yet.
  const usersSheet = getSpreadsheet_('studentFee').getSheetByName(SHEETS.USERS);
  if (usersSheet.getLastRow() < 2) {
    usersSheet.appendRow(['admin', 'admin123', 'Administrator', ROLES.ADMIN, 'YES']);
  }

  // Seed the TherapyFees rate card only if it's empty.
  const ratesSheet = getSpreadsheet_('studentFee').getSheetByName(SHEETS.RATES);
  if (ratesSheet.getLastRow() < 2) {
    const rateRows = [];
    THERAPIES.forEach(function (t) {
      SESSION_TYPES.forEach(function (st) { rateRows.push([t, st, '']); });
    });
    ratesSheet.getRange(2, 1, rateRows.length, 3).setValues(rateRows);
  }

  // Remove default "Sheet1" only if it's still completely empty and unused
  // (only relevant when running in single-spreadsheet / bound mode).
  const boundSs = SpreadsheetApp.getActiveSpreadsheet();
  const def = boundSs.getSheetByName('Sheet1');
  if (def && def.getLastRow() === 0) boundSs.deleteSheet(def);

  SpreadsheetApp.flush();
  return 'Setup complete. Existing data was left untouched — only missing sheets/headers were added. Default login (created only if USERS was empty) → username: admin, password: admin123.';
}

// ─────────────────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────────────────

function getSheet_(name) {
  const ss = getSpreadsheet_(DB_FOR_SHEET_[name] || 'studentFee');
  const sheet = ss.getSheetByName(name);
  if (!sheet) throw new Error('Sheet not found: ' + name + '. Run setup() first.');
  return sheet;
}

function sheetToObjects_(sheet) {
  const values = sheet.getDataRange().getValues();
  if (values.length < 2) return [];
  const headers = values[0];
  const rows = values.slice(1);
  return rows
    .filter(function (row) { return row.some(function (c) { return c !== '' && c !== null; }); })
    .map(function (row) {
      const obj = {};
      headers.forEach(function (h, i) { obj[h] = row[i]; });
      return obj;
    });
}

function findRowIndexById_(sheet, idColName, idValue) {
  const values = sheet.getDataRange().getValues();
  const headers = values[0];
  const idCol = headers.indexOf(idColName);
  for (let i = 1; i < values.length; i++) {
    if (String(values[i][idCol]) === String(idValue)) return i + 1; // 1-indexed sheet row
  }
  return -1;
}

function nextId_(sheet, idColName, prefix) {
  const values = sheet.getDataRange().getValues();
  const headers = values[0];
  const idCol = headers.indexOf(idColName);
  let max = 0;
  for (let i = 1; i < values.length; i++) {
    const raw = String(values[i][idCol] || '');
    const num = parseInt(raw.replace(prefix, ''), 10);
    if (!isNaN(num) && num > max) max = num;
  }
  const next = max + 1;
  return prefix + String(next).padStart(4, '0');
}

function formatDate_(d) {
  if (!d) return '';
  if (typeof d === 'string') return d;
  return Utilities.formatDate(new Date(d), Session.getScriptTimeZone(), 'yyyy-MM-dd');
}

function calcAge_(dob) {
  if (!dob) return '';
  const birth = new Date(dob);
  if (isNaN(birth.getTime())) return '';
  const today = new Date();
  let age = today.getFullYear() - birth.getFullYear();
  const m = today.getMonth() - birth.getMonth();
  if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) age--;
  return age;
}

// A Fees/Payments Therapy value may be a single code or a combined
// "A, B" / "A; B" record (multiple therapies billed together on one
// fee). Splits it back into individual codes for per-therapy reporting.
function splitTherapyList_(therapyStr) {
  return String(therapyStr || '').split(/[,;]+/).map(function (s) { return s.trim(); }).filter(Boolean);
}

// Copies a row into its archive sheet (with who/when deleted appended,
// plus any extra trailing values such as a deletion reason), then removes
// it from the source sheet. Used by every delete function so nothing is
// ever permanently lost.
function archiveRow_(sourceSheet, sourceHeaders, rowIdx, archiveSheetName, currentUser, extraValues) {
  const rowValues = sourceSheet.getRange(rowIdx, 1, 1, sourceHeaders.length).getValues()[0];
  getSheet_(archiveSheetName).appendRow(rowValues.concat([currentUser || '', new Date()]).concat(extraValues || []));
  sourceSheet.deleteRow(rowIdx);
}

// Appends one row to the Audit_log sheet. Never throws — a logging
// failure must never block the action it's trying to record.
function audit_(username, action, entityType, entityId, description) {
  try {
    getSheet_(SHEETS.AUDIT_LOG).appendRow([new Date(), username || '', action, entityType || '', entityId || '', description || '']);
  } catch (e) {
    // Swallow — audit logging is best-effort.
  }
}

// Staff Management's own audit function — writes to STAFF_AUDIT_LOG
// (routed to the 'staff' database bucket) instead of the shared
// Audit_log sheet, so Staff Management actions never depend on a
// second, unrelated spreadsheet. Used by StaffManagement.gs and
// Leaves.gs (the therapist leave workflow lives under the same Staff
// Management sidebar item) instead of audit_().
function auditStaff_(username, action, entityType, entityId, description) {
  try {
    getSheet_(SHEETS.STAFF_AUDIT_LOG).appendRow([new Date(), username || '', action, entityType || '', entityId || '', description || '']);
  } catch (e) {
    // Swallow — audit logging is best-effort.
  }
}

// ─────────────────────────────────────────────────────────────────────────
// AUTH & ROLES
// ─────────────────────────────────────────────────────────────────────────

function login(username, password) {
  const sheet = getSheet_(SHEETS.USERS);
  const users = sheetToObjects_(sheet);
  const match = users.find(function (u) {
    return String(u.Username).toLowerCase() === String(username).toLowerCase() &&
      String(u.Password) === String(password) &&
      String(u.Active).toUpperCase() === 'YES';
  });
  if (!match) return { success: false, message: 'Invalid username or password.' };
  return {
    success: true,
    user: { username: match.Username, fullName: match['Full Name'], role: match.Role }
  };
}

// Re-validates a username the browser remembered (via localStorage)
// against the USERS sheet, with no password required — lets a page
// refresh silently restore the session instead of forcing a fresh
// login every time. Still re-checks Active status and returns the
// CURRENT role fresh from the sheet (not whatever the browser cached),
// so a deactivated account or a role change since the last login takes
// effect immediately rather than trusting stale client-side state.
function restoreSession(username) {
  if (!username) return { success: false };
  const sheet = getSheet_(SHEETS.USERS);
  const users = sheetToObjects_(sheet);
  const match = users.find(function (u) {
    return String(u.Username).toLowerCase() === String(username).toLowerCase() &&
      String(u.Active).toUpperCase() === 'YES';
  });
  if (!match) return { success: false };
  return {
    success: true,
    user: { username: match.Username, fullName: match['Full Name'], role: match.Role }
  };
}

// Any logged-in user can change their OWN password — no role requirement,
// unlike saveUser (which changes someone else's password/role and stays
// Admin-only). Still requires the correct current password, verified
// server-side, so a stolen session token alone isn't enough to lock the
// real owner out.
function changeOwnPassword(username, currentPassword, newPassword) {
  if (!newPassword || newPassword.length < 4) {
    throw new Error('New password must be at least 4 characters.');
  }
  const sheet = getSheet_(SHEETS.USERS);
  const rowIdx = findRowIndexById_(sheet, 'Username', username);
  if (rowIdx === -1) throw new Error('User not found.');
  const headers = HEADERS.USERS;
  const storedPassword = sheet.getRange(rowIdx, headers.indexOf('Password') + 1).getValue();
  if (String(storedPassword) !== String(currentPassword)) {
    throw new Error('Current password is incorrect.');
  }
  sheet.getRange(rowIdx, headers.indexOf('Password') + 1).setValue(newPassword);
  audit_(username, 'Password Changed', 'User', username, '');
  return { success: true, message: 'Password updated.' };
}

// Re-reads the USERS sheet server-side rather than trusting a role value
// passed in from the client, so a Coordinator can't spoof "Admin" from the browser.
function getUserRole_(username) {
  const sheet = getSheet_(SHEETS.USERS);
  const users = sheetToObjects_(sheet);
  const match = users.find(function (u) {
    return String(u.Username).toLowerCase() === String(username || '').toLowerCase();
  });
  return match ? String(match.Role || '').trim() : '';
}

function requireAdmin_(username) {
  if (getUserRole_(username).toLowerCase() !== ROLES.ADMIN.toLowerCase()) {
    throw new Error('Only Admin users can delete records.');
  }
}

// Manager sits alongside Coordinator for everything else in the app —
// its one distinguishing power is approving/rejecting staff leave
// requests, which Coordinators cannot do.
// CenterHead is a full replica of Manager — same permissions everywhere,
// defined once here so every permission check (leave approval, Staff
// Management, salary visibility, dashboard tier) only needs to ask "is
// this a Manager-tier role?" rather than repeating both role names.
function isManagerTierRole_(role) {
  const r = String(role || '').toLowerCase();
  return r === ROLES.MANAGER.toLowerCase() || r === ROLES.CENTER_HEAD.toLowerCase();
}

function requireManagerOrAdmin_(username) {
  const role = getUserRole_(username).toLowerCase();
  if (role !== ROLES.ADMIN.toLowerCase() && !isManagerTierRole_(role)) {
    throw new Error('Only a Manager, Center Head, or Admin can approve or reject leave requests.');
  }
}

function getUsers(currentUser) {
  requireAdmin_(currentUser);
  return sheetToObjects_(getSheet_(SHEETS.USERS)).map(function (u) {
    return { Username: u.Username, 'Full Name': u['Full Name'], Role: u.Role, Active: u.Active };
  });
}

// Unlike getUsers(), this is available to any logged-in user — needed so
// Coordinators can pick who an enquiry is "Assigned To". No passwords or
// roles exposed, just enough to populate a picker.
function getStaffList() {
  return sheetToObjects_(getSheet_(SHEETS.USERS))
    .filter(function (u) { return String(u.Active).toUpperCase() === 'YES'; })
    .map(function (u) { return { username: u.Username, fullName: u['Full Name'] || u.Username }; });
}

function saveUser(data, currentUser) {
  requireAdmin_(currentUser);
  if (!data.username || !data.password) throw new Error('Username and password are required.');
  const sheet = getSheet_(SHEETS.USERS);
  if (findRowIndexById_(sheet, 'Username', data.username) !== -1) {
    throw new Error('That username already exists.');
  }
  sheet.appendRow([data.username, data.password, data.fullName || '', data.role || ROLES.COORDINATOR, data.active === false ? 'NO' : 'YES']);
  return { success: true, message: 'User added.' };
}

function deleteUser(username, currentUser) {
  requireAdmin_(currentUser);
  if (String(username).toLowerCase() === String(currentUser).toLowerCase()) {
    throw new Error('You cannot delete the account you are currently logged in as.');
  }
  const sheet = getSheet_(SHEETS.USERS);
  const rowIdx = findRowIndexById_(sheet, 'Username', username);
  if (rowIdx === -1) throw new Error('User not found: ' + username);
  archiveRow_(sheet, HEADERS.USERS, rowIdx, SHEETS.DELETED_USERS, currentUser);
  return { success: true, message: 'User deleted and archived to Deleted_users.' };
}

// ─────────────────────────────────────────────────────────────────────────
// STUDENTS
// ─────────────────────────────────────────────────────────────────────────

// Changes Active students to Inactive after (not on) their recorded exit
// date. It runs before student data is read, so all dashboards and lists
// stay current without manual staff updates.
function syncExpiredStudentStatuses_(sheet) {
  const values = sheet.getDataRange().getValues();
  if (values.length < 2) return;
  const headers = values[0];
  const statusIndex = headers.indexOf('Student Status');
  const exitDateIndex = headers.indexOf('Exit Date');
  if (statusIndex === -1 || exitDateIndex === -1) return;

  const today = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'yyyy-MM-dd');
  let changed = false;
  const statuses = values.slice(1).map(function (row) {
    const status = row[statusIndex];
    const exitDate = formatDate_(row[exitDateIndex]);
    if (String(status).toLowerCase() === 'active' && exitDate && exitDate < today) {
      changed = true;
      return ['Inactive'];
    }
    return [status];
  });
  if (changed) sheet.getRange(2, statusIndex + 1, statuses.length, 1).setValues(statuses);
}

function getStudents() {
  const sheet = getSheet_(SHEETS.STUDENTS);
  syncExpiredStudentStatuses_(sheet);
  return sheetToObjects_(sheet).map(function (s) {
    s['Registration Date'] = formatDate_(s['Registration Date']);
    s['Date of Birth'] = formatDate_(s['Date of Birth']);
    s['Joining Date'] = formatDate_(s['Joining Date']);
    s['Exit Date'] = formatDate_(s['Exit Date']);
    s['Created Date'] = formatDate_(s['Created Date']);
    s['Last Updated'] = formatDate_(s['Last Updated']);
    return s;
  });
}

function getStudentById(studentId) {
  return getStudents().find(function (s) { return s['Student ID'] === studentId; }) || null;
}

// Prevents accidentally creating two child records with the exact same
// name — a real mix-up risk at any center that runs long enough (two
// "Rahul Sharma"s enrolled a year apart, etc.). Compares names
// case-insensitively and trimmed, since "Rahul Sharma" and "rahul sharma"
// are the same collision in practice. Only blocks an exact full-name
// match; anything even slightly different (a middle name, initial, or
// surname added) is treated as a different child and goes through fine —
// that's exactly what the error message asks staff to do about it. Not
// scoped to Active students only — an existing Exited/Inactive record
// with the same name is just as confusing to have two of, so every
// current student counts. Excludes the row being edited itself, so
// saving an update to a student's own record (even with its name
// unchanged) never trips over itself.
function checkDuplicateStudentName_(data) {
  const name = String(data.name || '').trim();
  if (!name) return;
  const students = getStudents();
  const dup = students.find(function (s) {
    if (data.studentId && s['Student ID'] === data.studentId) return false;
    return String(s['Student Name'] || '').trim().toLowerCase() === name.toLowerCase();
  });
  if (dup) {
    throw new Error('A child named "' + dup['Student Name'] + '" already exists (Student ID ' + dup['Student ID'] +
      '). Please add a surname, middle name, or other differentiator to tell them apart.');
  }
}

function saveStudent(data, currentUser) {
  checkDuplicateStudentName_(data);
  const sheet = getSheet_(SHEETS.STUDENTS);
  const now = new Date();
  const therapies = Array.isArray(data.therapies) ? data.therapies.join(', ') : (data.therapies || '');
  const age = data.dob ? calcAge_(data.dob) : '';

  if (data.studentId) {
    // UPDATE
    const rowIdx = findRowIndexById_(sheet, 'Student ID', data.studentId);
    if (rowIdx === -1) throw new Error('Student not found: ' + data.studentId);
    const headers = HEADERS.STUDENTS;
    const rowValues = headers.map(function (h) {
      switch (h) {
        case 'Student ID': return data.studentId;
        case 'Student Name': return data.name;
        case 'Date of Birth': return data.dob || '';
        case 'Age': return age;
        case 'Gender': return data.gender || '';
        case 'Father Name': return data.fatherName || '';
        case 'Mother Name': return data.motherName || '';
        case 'Parent/Guardian Name': return data.guardianName || '';
        case 'Parent Mobile': return data.parentMobile || '';
        case 'Alternate Mobile': return data.altMobile || '';
        case 'Parent Email': return data.parentEmail || '';
        case 'Parents Occupation': return data.parentsOccupation || '';
        case 'Address': return data.address || '';
        case 'City': return data.city || '';
        case 'Joining Date': return data.joiningDate || '';
        case 'Exit Date': return data.exitDate || '';
        case 'Student Status': return data.status || 'Active';
        case 'Therapies Taking': return therapies;
        case 'Notes': return data.notes || '';
        case 'Last Updated': return now;
        case 'Updated By': return currentUser || '';
        default: return sheet.getRange(rowIdx, headers.indexOf(h) + 1).getValue(); // preserve Registration Date, Created By, Created Date
      }
    });
    sheet.getRange(rowIdx, 1, 1, rowValues.length).setValues([rowValues]);
    return { success: true, studentId: data.studentId, message: 'Student updated.' };
  } else {
    // CREATE
    const studentId = nextId_(sheet, 'Student ID', 'STU');
    const row = [
      studentId, now, data.name, data.dob || '', age, data.gender || '',
      data.fatherName || '', data.motherName || '', data.guardianName || '',
      data.parentMobile || '', data.altMobile || '', data.parentEmail || '',
      data.parentsOccupation || '', data.address || '', data.city || '',
      data.joiningDate || '', data.exitDate || '', data.status || 'Active',
      therapies, data.notes || '', currentUser || '', now, now, currentUser || ''
    ];
    sheet.appendRow(row);
    return { success: true, studentId: studentId, message: 'Student added.' };
  }
}

function getTherapyOptions() {
  return THERAPIES.map(function (code) { return { code: code, label: THERAPY_LABELS[code] }; });
}

function deleteStudent(studentId, currentUser) {
  requireAdmin_(currentUser);
  const sheet = getSheet_(SHEETS.STUDENTS);
  const rowIdx = findRowIndexById_(sheet, 'Student ID', studentId);
  if (rowIdx === -1) throw new Error('Student not found: ' + studentId);
  archiveRow_(sheet, HEADERS.STUDENTS, rowIdx, SHEETS.DELETED_STUDENTS, currentUser);
  return { success: true, message: 'Student deleted and archived to Deleted_students.' };
}

function getStudentTherapies(studentId) {
  const student = getStudentById(studentId);
  if (!student || !student['Therapies Taking']) return [];
  return String(student['Therapies Taking']).split(',').map(function (t) { return t.trim(); }).filter(Boolean);
}

// ─────────────────────────────────────────────────────────────────────────
// FEES
// ─────────────────────────────────────────────────────────────────────────

function getFees() {
  const sheet = getSheet_(SHEETS.FEES);
  return sheetToObjects_(sheet).map(function (f) {
    f['Session Start Date'] = formatDate_(f['Session Start Date']);
    f['Created Date'] = formatDate_(f['Created Date']);
    f['Updated Date'] = formatDate_(f['Updated Date']);
    return f;
  });
}

// TherapyFees sheet: Therapy, Session Type, Fee Amount — the master rate card.
function getFeeRates() {
  return sheetToObjects_(getSheet_(SHEETS.RATES));
}

// Looks up the rate for a Therapy + Session Type combo. If more than one
// row matches (a misconfigured TherapyFees sheet), this deliberately does
// NOT guess — it reports ambiguous:true so the caller can warn instead of
// silently picking one.
function getRateInfo_(therapy, sessionType) {
  const matches = getFeeRates().filter(function (r) {
    return r.Therapy === therapy && r['Session Type'] === sessionType;
  });
  if (matches.length === 0) return { amount: null, ambiguous: false };
  if (matches.length > 1) return { amount: null, ambiguous: true };
  const amt = Number(matches[0]['Fee Amount']);
  return { amount: amt > 0 ? amt : null, ambiguous: false };
}

// Returns every Therapy + Session Type combo that has more than one row in
// TherapyFees, for the Admin-only dashboard warning banner.
// Admin or Manager only — Coordinators can view rates (getFeeRates is
// unrestricted) but not change them. Upserts by Therapy + Session Type
// since the rate card has no separate ID column.
function saveFeeRate(data, currentUser) {
  requireManagerOrAdmin_(currentUser);
  if (!data.therapy || !data.sessionType) throw new Error('Therapy and Session Type are required.');
  if (!(Number(data.amount) >= 0)) throw new Error('Enter a valid fee amount.');
  const sheet = getSheet_(SHEETS.RATES);
  const values = sheet.getDataRange().getValues();
  const headers = values[0];
  const therapyCol = headers.indexOf('Therapy');
  const typeCol = headers.indexOf('Session Type');
  const amountCol = headers.indexOf('Fee Amount');
  for (let i = 1; i < values.length; i++) {
    if (values[i][therapyCol] === data.therapy && values[i][typeCol] === data.sessionType) {
      sheet.getRange(i + 1, amountCol + 1).setValue(Number(data.amount));
      audit_(currentUser, 'Therapy Fee Rate Updated', 'TherapyFees', data.therapy + ' / ' + data.sessionType, 'New amount: ' + data.amount);
      return { success: true, message: 'Rate updated.' };
    }
  }
  sheet.appendRow([data.therapy, data.sessionType, Number(data.amount)]);
  audit_(currentUser, 'Therapy Fee Rate Added', 'TherapyFees', data.therapy + ' / ' + data.sessionType, 'Amount: ' + data.amount);
  return { success: true, message: 'Rate added.' };
}

function getRateWarnings() {
  const counts = {};
  getFeeRates().forEach(function (r) {
    if (!r.Therapy || !r['Session Type']) return;
    const key = r.Therapy + '|' + r['Session Type'];
    counts[key] = (counts[key] || 0) + 1;
  });
  return Object.keys(counts)
    .filter(function (k) { return counts[k] > 1; })
    .map(function (k) {
      const parts = k.split('|');
      return { therapy: parts[0], sessionType: parts[1], count: counts[k] };
    });
}

function deleteFee(feeId, currentUser) {
  requireAdmin_(currentUser);
  const sheet = getSheet_(SHEETS.FEES);
  const rowIdx = findRowIndexById_(sheet, 'Fee ID', feeId);
  if (rowIdx === -1) throw new Error('Fee record not found: ' + feeId);
  archiveRow_(sheet, HEADERS.FEES, rowIdx, SHEETS.DELETED_FEES, currentUser);
  return { success: true, message: 'Fee record deleted and archived to Deleted_fees.' };
}

function getFeesByStudent(studentId) {
  return getFees().filter(function (f) { return f['Student ID'] === studentId; });
}

function computeStatus_(netAmount, amountPaid) {
  if (amountPaid <= 0) return 'Unpaid';
  if (amountPaid >= netAmount) return 'Paid';
  return 'Partial';
}

// Prevents accidentally billing the same therapy/session twice for the
// same child — e.g. two separate "SP" fee records both starting
// 2026-08-01. Only blocks an exact match on Student + Therapy + Session
// Start Date; a different date (a genuinely new session) or a different
// therapy is a legitimate, separate record and is left alone. A combined
// record's Therapy ("SP, OT") is split back into individual codes on
// both sides, so a new single "SP" record still collides with an
// existing "SP, OT" one for the same date, and vice versa. Excludes the
// row being edited itself, so saving an update to a record's own Amount
// Paid/Discount/etc. never trips over itself.
function checkDuplicateFee_(data) {
  const studentId = data.studentId;
  const requestedTherapies = splitTherapyList_(data.therapy);
  const requestedDate = formatDate_(data.sessionStartDate);
  if (!studentId || !requestedTherapies.length || !requestedDate) return;
  const existing = getFeesByStudent(studentId);
  const dup = existing.find(function (f) {
    if (data.feeId && f['Fee ID'] === data.feeId) return false; // editing this same record — not a duplicate of itself
    if (formatDate_(f['Session Start Date']) !== requestedDate) return false;
    const existingTherapies = splitTherapyList_(f['Therapy']);
    return existingTherapies.some(function (t) { return requestedTherapies.indexOf(t) !== -1; });
  });
  if (dup) {
    throw new Error('Fee record already exists for ' + (data.studentName || studentId) + ' — ' + dup['Therapy'] +
      ', session starting ' + requestedDate + ' (Fee ID ' + dup['Fee ID'] + '). Please check.');
  }
}

function saveFee(data, currentUser) {
  if (!data.sessionStartDate) throw new Error('Session start date is required.');
  const sessionStart = new Date(data.sessionStartDate + 'T00:00:00');
  if (isNaN(sessionStart.getTime())) throw new Error('Session start date is invalid.');
  checkDuplicateFee_(data);
  const billingMonths = ['January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'];
  const billingMonth = billingMonths[sessionStart.getMonth()];
  const billingYear = sessionStart.getFullYear();
  const sheet = getSheet_(SHEETS.FEES);
  const now = new Date();
  // A combined multi-therapy record (Therapy = "A, B") isn't a valid
  // TherapyFees key on its own — the frontend already looked up (or let
  // the user manually enter) each therapy's individual rate and summed
  // them, so trust the provided total rather than re-deriving it here.
  // Single-therapy records keep the full rate-card lookup + ambiguity
  // protection as before.
  let feeAmount;
  if (splitTherapyList_(data.therapy).length > 1) {
    feeAmount = Number(data.feeAmount) || 0;
  } else {
    const rateInfo = getRateInfo_(data.therapy, data.sessionType);
    if (rateInfo.ambiguous) {
      if (!(Number(data.feeAmount) > 0)) {
        throw new Error('TherapyFees has duplicate entries for ' + data.therapy + ' / ' + data.sessionType +
          ' — fix the sheet, or enter the fee amount manually to proceed.');
      }
      feeAmount = Number(data.feeAmount);
    } else {
      feeAmount = rateInfo.amount !== null ? rateInfo.amount : (Number(data.feeAmount) || 0);
    }
  }
  const discount = Number(data.discount) || 0;
  const netAmount = feeAmount - discount;
  const amountPaid = Number(data.amountPaid) || 0;
  const balanceDue = netAmount - amountPaid;
  const status = computeStatus_(netAmount, amountPaid);

  if (data.feeId) {
    const rowIdx = findRowIndexById_(sheet, 'Fee ID', data.feeId);
    if (rowIdx === -1) throw new Error('Fee not found: ' + data.feeId);
    const headers = HEADERS.FEES;
    const rowValues = headers.map(function (h) {
      switch (h) {
        case 'Fee ID': return data.feeId;
        case 'Student ID': return data.studentId;
        case 'Student Name': return data.studentName;
        case 'Therapy': return data.therapy;
        case 'Session Type': return data.sessionType;
        case 'Billing Month': return billingMonth;
        case 'Billing Year': return billingYear;
        case 'Fee Amount': return feeAmount;
        case 'Discount': return discount;
        case 'Net Amount': return netAmount;
        case 'Amount Paid': return amountPaid;
        case 'Balance Due': return balanceDue;
        case 'Payment Status': return status;
        case 'Session Start Date': return data.sessionStartDate || '';
        case 'Updated Date': return now;
        default: return sheet.getRange(rowIdx, headers.indexOf(h) + 1).getValue(); // Created Date
      }
    });
    sheet.getRange(rowIdx, 1, 1, rowValues.length).setValues([rowValues]);
    return { success: true, feeId: data.feeId, message: 'Fee record updated.' };
  } else {
    const feeId = nextId_(sheet, 'Fee ID', 'FEE');
    const row = [
      feeId, data.studentId, data.studentName, data.therapy, data.sessionType,
      billingMonth, billingYear, feeAmount, discount, netAmount,
      amountPaid, balanceDue, status, data.sessionStartDate || '', now, now
    ];
    sheet.appendRow(row);
    return { success: true, feeId: feeId, message: 'Fee record created.' };
  }
}

// Creates one fee record per item — used when a manager selects multiple
// therapies at once on the New Fee Record form (each therapy still gets
// its own Fee row, since rates and balances are tracked per therapy).
// Each item failing (e.g. an ambiguous rate with no manual override) does
// not stop the others; the per-item result reports success/failure so the
// frontend can show exactly which therapies were and weren't created.
function saveFeesBatch(items, currentUser) {
  if (!items || !items.length) throw new Error('Select at least one therapy.');
  return items.map(function (item) {
    try {
      const r = saveFee(item, currentUser);
      return { success: true, therapy: item.therapy, feeId: r.feeId, message: r.message };
    } catch (err) {
      return { success: false, therapy: item.therapy, message: err.message };
    }
  });
}

// ─────────────────────────────────────────────────────────────────────────
// PAYMENTS
// ─────────────────────────────────────────────────────────────────────────

function getPayments() {
  const sheet = getSheet_(SHEETS.PAYMENTS);
  return sheetToObjects_(sheet).map(function (p) {
    p['Payment Date'] = formatDate_(p['Payment Date']);
    p['Created Date'] = formatDate_(p['Created Date']);
    return p;
  });
}

function getPaymentsByStudent(studentId) {
  return getPayments().filter(function (p) { return p['Student ID'] === studentId; });
}

function recordPayment(data, currentUser) {
  const paymentsSheet = getSheet_(SHEETS.PAYMENTS);
  const feesSheet = getSheet_(SHEETS.FEES);
  const now = new Date();

  const rowIdx = findRowIndexById_(feesSheet, 'Fee ID', data.feeId);
  if (rowIdx === -1) throw new Error('Fee record not found: ' + data.feeId);

  const headers = HEADERS.FEES;
  const feeRowValues = feesSheet.getRange(rowIdx, 1, 1, headers.length).getValues()[0];
  const feeAmount = Number(feeRowValues[headers.indexOf('Fee Amount')]) || 0;
  const currentDiscount = Number(feeRowValues[headers.indexOf('Discount')]) || 0;
  const currentPaid = Number(feeRowValues[headers.indexOf('Amount Paid')]) || 0;
  const amountReceived = Number(data.amountReceived) || 0;
  // Discount given at payment time (e.g. a waiver applied while collecting)
  // adds to the fee's running Discount total and reduces its Net Amount —
  // on top of any discount already set when the fee record was created.
  const discountGiven = Number(data.discount) || 0;

  const newDiscount = currentDiscount + discountGiven;
  const newNetAmount = feeAmount - newDiscount;
  const newPaid = currentPaid + amountReceived;
  const newBalance = newNetAmount - newPaid;
  const newStatus = computeStatus_(newNetAmount, newPaid);

  feesSheet.getRange(rowIdx, headers.indexOf('Discount') + 1).setValue(newDiscount);
  feesSheet.getRange(rowIdx, headers.indexOf('Net Amount') + 1).setValue(newNetAmount);
  feesSheet.getRange(rowIdx, headers.indexOf('Amount Paid') + 1).setValue(newPaid);
  feesSheet.getRange(rowIdx, headers.indexOf('Balance Due') + 1).setValue(newBalance);
  feesSheet.getRange(rowIdx, headers.indexOf('Payment Status') + 1).setValue(newStatus);
  feesSheet.getRange(rowIdx, headers.indexOf('Updated Date') + 1).setValue(now);

  const paymentId = nextId_(paymentsSheet, 'Payment ID', 'PAY');
  const receiptNumber = data.receiptNumber || ('RCPT' + Utilities.formatDate(now, Session.getScriptTimeZone(), 'yyMMddHHmmss'));
  paymentsSheet.appendRow([
    paymentId, data.feeId, data.studentId, data.studentName, data.therapy,
    data.billingMonth, data.billingYear, amountReceived, discountGiven, data.paymentMode || '',
    data.paymentDate || now, receiptNumber, data.remarks || '', currentUser || '', now
  ]);

  return {
    success: true,
    paymentId: paymentId,
    receiptNumber: receiptNumber,
    newBalance: newBalance,
    newStatus: newStatus,
    message: 'Payment recorded.'
  };
}

// Admin-only correction tool for an already-recorded payment (e.g. a
// typo'd amount, wrong payment mode/date, or a remark that needs
// fixing) — everyone else only sees payments read-only. Fee ID is not
// editable here: a payment stays linked to the same fee it was recorded
// against, since reassigning it to a different fee is a bigger
// structural change than correcting a mistake on this one. The linked
// fee's Discount / Amount Paid / Net Amount / Balance Due / Payment
// Status are recalculated by backing out this payment's old effect and
// reapplying its corrected one — the same reverse-then-reapply approach
// deletePayment (reverse) and recordPayment (apply) already use
// separately, just combined here without touching the Payment ID or
// archiving anything.
function updatePayment(data, currentUser) {
  requireAdmin_(currentUser);
  const paymentsSheet = getSheet_(SHEETS.PAYMENTS);
  const feesSheet = getSheet_(SHEETS.FEES);
  const payHeaders = HEADERS.PAYMENTS;

  const payRowIdx = findRowIndexById_(paymentsSheet, 'Payment ID', data.paymentId);
  if (payRowIdx === -1) throw new Error('Payment not found: ' + data.paymentId);

  const payRow = paymentsSheet.getRange(payRowIdx, 1, 1, payHeaders.length).getValues()[0];
  const feeId = payRow[payHeaders.indexOf('Fee ID')];
  const oldAmount = Number(payRow[payHeaders.indexOf('Amount Received')]) || 0;
  const oldDiscount = Number(payRow[payHeaders.indexOf('Discount Given')]) || 0;

  const newAmount = Number(data.amountReceived) || 0;
  const newDiscount = Number(data.discount) || 0;
  if (newAmount <= 0) throw new Error('Amount received must be greater than zero.');

  const feeRowIdx = findRowIndexById_(feesSheet, 'Fee ID', feeId);
  if (feeRowIdx === -1) throw new Error('Linked fee record not found: ' + feeId + ' — cannot recalculate its balance.');

  const feeHeaders = HEADERS.FEES;
  const feeAmount = Number(feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Fee Amount') + 1).getValue()) || 0;
  const currentDiscount = Number(feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Discount') + 1).getValue()) || 0;
  const currentPaid = Number(feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Amount Paid') + 1).getValue()) || 0;

  const adjustedDiscount = Math.max(0, currentDiscount - oldDiscount + newDiscount);
  const adjustedPaid = Math.max(0, currentPaid - oldAmount + newAmount);
  const newNetAmount = feeAmount - adjustedDiscount;
  const newBalance = newNetAmount - adjustedPaid;
  const newStatus = computeStatus_(newNetAmount, adjustedPaid);

  feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Discount') + 1).setValue(adjustedDiscount);
  feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Net Amount') + 1).setValue(newNetAmount);
  feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Amount Paid') + 1).setValue(adjustedPaid);
  feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Balance Due') + 1).setValue(newBalance);
  feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Payment Status') + 1).setValue(newStatus);
  feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Updated Date') + 1).setValue(new Date());

  const rowValues = payHeaders.map(function (h) {
    switch (h) {
      case 'Amount Received': return newAmount;
      case 'Discount Given': return newDiscount;
      case 'Payment Mode': return data.paymentMode || '';
      case 'Payment Date': return data.paymentDate || payRow[payHeaders.indexOf('Payment Date')];
      case 'Receipt Number': return data.receiptNumber || payRow[payHeaders.indexOf('Receipt Number')];
      case 'Remarks': return data.remarks || '';
      default: return payRow[payHeaders.indexOf(h)]; // Payment ID, Fee ID, Student ID, Student Name, Therapy, Billing Month, Billing Year, Created By, Created Date stay as originally recorded
    }
  });
  paymentsSheet.getRange(payRowIdx, 1, 1, rowValues.length).setValues([rowValues]);

  audit_(currentUser, 'Payment Updated', 'Payment', data.paymentId,
    'Amount ' + oldAmount + ' -> ' + newAmount + (newDiscount !== oldDiscount ? ', Discount ' + oldDiscount + ' -> ' + newDiscount : ''));
  return { success: true, message: 'Payment updated and fee balance recalculated.', newBalance: newBalance, newStatus: newStatus };
}

function deletePayment(paymentId, currentUser) {
  requireAdmin_(currentUser);
  const paymentsSheet = getSheet_(SHEETS.PAYMENTS);
  const feesSheet = getSheet_(SHEETS.FEES);

  const payRowIdx = findRowIndexById_(paymentsSheet, 'Payment ID', paymentId);
  if (payRowIdx === -1) throw new Error('Payment not found: ' + paymentId);

  const payHeaders = HEADERS.PAYMENTS;
  const payRow = paymentsSheet.getRange(payRowIdx, 1, 1, payHeaders.length).getValues()[0];
  const feeId = payRow[payHeaders.indexOf('Fee ID')];
  const amount = Number(payRow[payHeaders.indexOf('Amount Received')]) || 0;
  const discountGiven = Number(payRow[payHeaders.indexOf('Discount Given')]) || 0;

  // Reverse this payment's effect on the linked fee record — both the
  // amount paid and any discount it applied — if the fee still exists.
  const feeRowIdx = findRowIndexById_(feesSheet, 'Fee ID', feeId);
  if (feeRowIdx !== -1) {
    const feeHeaders = HEADERS.FEES;
    const feeAmount = Number(feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Fee Amount') + 1).getValue()) || 0;
    const currentDiscount = Number(feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Discount') + 1).getValue()) || 0;
    const currentPaid = Number(feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Amount Paid') + 1).getValue()) || 0;
    const newDiscount = Math.max(0, currentDiscount - discountGiven);
    const newNetAmount = feeAmount - newDiscount;
    const newPaid = Math.max(0, currentPaid - amount);
    const newBalance = newNetAmount - newPaid;
    feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Discount') + 1).setValue(newDiscount);
    feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Net Amount') + 1).setValue(newNetAmount);
    feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Amount Paid') + 1).setValue(newPaid);
    feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Balance Due') + 1).setValue(newBalance);
    feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Payment Status') + 1).setValue(computeStatus_(newNetAmount, newPaid));
    feesSheet.getRange(feeRowIdx, feeHeaders.indexOf('Updated Date') + 1).setValue(new Date());
  }

  archiveRow_(paymentsSheet, payHeaders, payRowIdx, SHEETS.DELETED_PAYMENTS, currentUser);
  return { success: true, message: 'Payment deleted, archived to Deleted_payments, and fee balance restored.' };
}

// ─────────────────────────────────────────────────────────────────────────
// DASHBOARD & REPORTS
// ─────────────────────────────────────────────────────────────────────────

// Powers the admin Dashboard's "Last updated" tile — the most recent
// activity timestamp across Fees, Students, and Enquiries. Deliberately
// scoped to just these three sheets: they're already fully loaded by
// getDashboardStats() on every request, so this adds no new spreadsheet
// opens, only a few small extra column reads. This scope still captures
// Payments (recording/editing/deleting a payment always touches its
// linked fee's Updated Date) and Enquiry Follow-ups (addFollowup() rolls
// its timestamp forward onto the enquiry's Last Updated) without reading
// those two sheets separately. A pure delete with no other edit won't
// move this tile, since the deleted row moves to its Deleted_ archive
// sheet and stops counting — a deliberate trade-off to keep this cheap;
// say so if you'd rather it also scan the archive sheets.
//
// Reads the raw column values directly (not through getFees()/
// getStudents(), which reformat these same columns to date-only strings
// for display elsewhere) so the tile can show the actual time of day,
// not just the date.
function getLastActivityTimestamp_() {
  function maxInColumn_(sheetName, headerName) {
    const sheet = getSheet_(sheetName);
    const lastRow = sheet.getLastRow();
    if (lastRow < 2) return null;
    const headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
    const colIdx = headers.indexOf(headerName);
    if (colIdx === -1) return null;
    const values = sheet.getRange(2, colIdx + 1, lastRow - 1, 1).getValues();
    let max = null;
    for (let i = 0; i < values.length; i++) {
      const raw = values[i][0];
      if (!raw) continue;
      const d = raw instanceof Date ? raw : new Date(raw);
      if (isNaN(d.getTime())) continue;
      if (!max || d > max) max = d;
    }
    return max;
  }
  const candidates = [
    maxInColumn_(SHEETS.FEES, 'Updated Date'),
    maxInColumn_(SHEETS.STUDENTS, 'Last Updated'),
    maxInColumn_(SHEETS.ENQUIRIES, 'Last Updated')
  ].filter(function (d) { return d !== null; });
  if (!candidates.length) return null;
  return candidates.reduce(function (max, d) { return d > max ? d : max; });
}

function getDashboardStats() {
  const students = getStudents();
  const fees = getFees();
  const payments = getPayments();
  const enquiries = sheetToObjects_(getSheet_(SHEETS.ENQUIRIES));

  const activeStudents = students.filter(function (s) { return s['Student Status'] === 'Active'; });

  const now = new Date();
  const today = Utilities.formatDate(now, Session.getScriptTimeZone(), 'yyyy-MM-dd');
  // An upcoming exit is any child with an Exit Date strictly after today.
  // getStudents() normalizes these dates as yyyy-MM-dd strings, making
  // this comparison timezone-safe and inclusive of every future date.
  const upcomingExitStudents = students.filter(function (s) {
    return s['Exit Date'] && s['Exit Date'] > today;
  });
  const thisMonth = now.getMonth();
  const thisYear = now.getFullYear();
  const totalEnquiriesMonth = enquiries.filter(function (e) {
    const d = e['Enquiry Date'] ? new Date(e['Enquiry Date']) : null;
    return d && d.getMonth() === thisMonth && d.getFullYear() === thisYear;
  }).length;

  const monthPayments = payments.filter(function (p) {
    const d = p['Payment Date'] ? new Date(p['Payment Date']) : null;
    return d && d.getMonth() === thisMonth && d.getFullYear() === thisYear;
  });
  const totalCollectionMonth = monthPayments.reduce(function (sum, p) { return sum + (Number(p['Amount Received']) || 0); }, 0);
  const cashCollectionMonth = monthPayments
    .filter(function (p) { return String(p['Payment Mode']).toLowerCase() === 'cash'; })
    .reduce(function (sum, p) { return sum + (Number(p['Amount Received']) || 0); }, 0);
  const onlineCollectionMonth = totalCollectionMonth - cashCollectionMonth;

  // Current month, by therapy — feeds the "Collection by therapy & sports" panel.
  // A payment against a combined multi-therapy fee splits its amount
  // evenly across the therapies it covers, so these buckets still sum
  // to the true total collected.
  const therapyCollection = {};
  THERAPIES.forEach(function (t) { therapyCollection[t] = 0; });
  monthPayments.forEach(function (p) {
    const parts = splitTherapyList_(p.Therapy);
    if (!parts.length) return;
    const share = (Number(p['Amount Received']) || 0) / parts.length;
    parts.forEach(function (t) {
      if (therapyCollection.hasOwnProperty(t)) therapyCollection[t] += share;
    });
  });

  const totalFeeDue = fees.reduce(function (sum, f) { return sum + (Number(f['Balance Due']) || 0); }, 0);
  const dueChildrenCount = new Set(
    fees.filter(function (f) { return Number(f['Balance Due']) > 0; }).map(function (f) { return f['Student ID']; })
  ).size;

  const newStudents = students.filter(function (s) {
    const d = s['Joining Date'] ? new Date(s['Joining Date']) : null;
    return d && d.getMonth() === thisMonth && d.getFullYear() === thisYear;
  }).length;
  const exitedStudents = students.filter(function (s) {
    const d = s['Exit Date'] ? new Date(s['Exit Date']) : null;
    return d && d.getMonth() === thisMonth && d.getFullYear() === thisYear;
  }).length;

  const totalExpensesMonth = getExpensesThisMonth_();
  const upcomingLeave = getUpcomingLeaveSummary();
  const sessionGapStats = getSessionGapStats_();
  const lastActivity = getLastActivityTimestamp_();

  return {
    lastActivityAt: lastActivity
      ? Utilities.formatDate(lastActivity, Session.getScriptTimeZone(), 'dd MMM yyyy, hh:mm a')
      : '—',
    totalEnquiriesMonth: totalEnquiriesMonth,
    totalActiveStudents: activeStudents.length,
    upcomingExitStudents: upcomingExitStudents.length,
    totalCollectionMonth: totalCollectionMonth,
    cashCollectionMonth: cashCollectionMonth,
    onlineCollectionMonth: onlineCollectionMonth,
    therapyCollection: therapyCollection,
    totalFeeDue: totalFeeDue,
    dueChildrenCount: dueChildrenCount,
    newStudents: newStudents,
    exitedStudents: exitedStudents,
    totalExpensesMonth: totalExpensesMonth,
    netThisMonth: totalCollectionMonth - totalExpensesMonth,
    noSession40: sessionGapStats.noSession40,
    noSession60: sessionGapStats.noSession60,
    upcomingLeaveCount: upcomingLeave.count
  };
}

// Therapy-wise collection summary scoped to the current calendar month —
// used by the Reports tab. "Billed" = fees billed this month, "Collected"
// = payments received this month, "Outstanding" = balance remaining on
// this month's fees.
function getTherapyCollectionSummary() {
  const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'];
  const now = new Date();
  const monthName = MONTH_NAMES[now.getMonth()];
  const year = now.getFullYear();

  const fees = getFees();
  const payments = getPayments();

  const monthFees = fees.filter(function (f) {
    return f['Billing Month'] === monthName && Number(f['Billing Year']) === year;
  });
  const monthPayments = payments.filter(function (p) {
    const d = p['Payment Date'] ? new Date(p['Payment Date']) : null;
    return d && d.getMonth() === now.getMonth() && d.getFullYear() === year;
  });

  const byTherapy = {};
  THERAPIES.forEach(function (t) { byTherapy[t] = { count: 0, billed: 0, collected: 0, outstanding: 0 }; });

  // A combined multi-therapy fee/payment splits its amounts evenly
  // across the therapies it covers, and counts once toward each
  // therapy's "Fee records" count — matching the same rule the Enquiry
  // Dashboard's Service Summary uses for multi-select enquiries.
  monthFees.forEach(function (f) {
    const parts = splitTherapyList_(f['Therapy']);
    if (!parts.length) return;
    const billedShare = (Number(f['Net Amount']) || 0) / parts.length;
    const outstandingShare = (Number(f['Balance Due']) || 0) / parts.length;
    parts.forEach(function (t) {
      if (!byTherapy[t]) byTherapy[t] = { count: 0, billed: 0, collected: 0, outstanding: 0 };
      byTherapy[t].count++;
      byTherapy[t].billed += billedShare;
      byTherapy[t].outstanding += outstandingShare;
    });
  });
  monthPayments.forEach(function (p) {
    const parts = splitTherapyList_(p['Therapy']);
    if (!parts.length) return;
    const collectedShare = (Number(p['Amount Received']) || 0) / parts.length;
    parts.forEach(function (t) {
      if (!byTherapy[t]) byTherapy[t] = { count: 0, billed: 0, collected: 0, outstanding: 0 };
      byTherapy[t].collected += collectedShare;
    });
  });

  return { monthLabel: monthName + ' ' + year, byTherapy: byTherapy };
}

// Children currently carrying an outstanding balance, one row per student
// (balances across therapies summed together). Shown on both dashboards.
function getFeeDueSummary() {
  const fees = getFees().filter(function (f) { return Number(f['Balance Due']) > 0; });
  const students = getStudents();
  const byStudent = {};
  fees.forEach(function (f) {
    const sid = f['Student ID'];
    if (!byStudent[sid]) byStudent[sid] = { studentId: sid, studentName: f['Student Name'], totalDue: 0, therapies: [] };
    byStudent[sid].totalDue += Number(f['Balance Due']) || 0;
    splitTherapyList_(f['Therapy']).forEach(function (t) {
      if (byStudent[sid].therapies.indexOf(t) === -1) byStudent[sid].therapies.push(t);
    });
  });
  return Object.keys(byStudent).map(function (sid) {
    const row = byStudent[sid];
    const student = students.find(function (s) { return s['Student ID'] === sid; });
    return {
      studentId: sid,
      studentName: row.studentName,
      parentName: student ? student['Parent/Guardian Name'] : '',
      parentMobile: student ? student['Parent Mobile'] : '',
      therapies: row.therapies.join(', '),
      totalDue: row.totalDue,
      status: student ? student['Student Status'] : ''
    };
  }).sort(function (a, b) { return b.totalDue - a.totalDue; });
}

// Admin-only historical table. startYear/startMonth/endYear/endMonth are
// 1-indexed months (1=January), inclusive range, oldest→newest internally
// then reversed so the most recent month is listed first.
function getMonthlySummary(startYear, startMonth, endYear, endMonth, currentUser) {
  requireAdmin_(currentUser);
  const fees = getFees();
  const payments = getPayments();
  const students = getStudents();
  const expenses = getAllExpenses_();
  const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'];

  const months = [];
  let y = Number(startYear), m = Number(startMonth);
  const endY = Number(endYear), endM = Number(endMonth);
  let guard = 0;
  while ((y < endY || (y === endY && m <= endM)) && guard < 60) {
    months.push({ year: y, month: m });
    m++;
    if (m > 12) { m = 1; y++; }
    guard++;
  }

  const rows = months.map(function (ym) {
    const monthPayments = payments.filter(function (p) {
      const d = p['Payment Date'] ? new Date(p['Payment Date']) : null;
      return d && (d.getMonth() + 1) === ym.month && d.getFullYear() === ym.year;
    });
    const totalCollection = monthPayments.reduce(function (s, p) { return s + (Number(p['Amount Received']) || 0); }, 0);
    const cashCollection = monthPayments
      .filter(function (p) { return String(p['Payment Mode']).toLowerCase() === 'cash'; })
      .reduce(function (s, p) { return s + (Number(p['Amount Received']) || 0); }, 0);
    const onlineCollection = totalCollection - cashCollection;

    const monthFees = fees.filter(function (f) {
      return f['Billing Month'] === MONTH_NAMES[ym.month - 1] && Number(f['Billing Year']) === ym.year;
    });
    const feeDue = monthFees.reduce(function (s, f) { return s + (Number(f['Balance Due']) || 0); }, 0);

    const monthExpenses = expenses.filter(function (e) {
      const d = e['Date'] ? new Date(e['Date']) : null;
      return d && (d.getMonth() + 1) === ym.month && d.getFullYear() === ym.year;
    });
    const totalExpenses = monthExpenses.reduce(function (s, e) { return s + (Number(e['Amount']) || 0); }, 0);

    const newStudents = students.filter(function (s) {
      const d = s['Joining Date'] ? new Date(s['Joining Date']) : null;
      return d && (d.getMonth() + 1) === ym.month && d.getFullYear() === ym.year;
    }).length;
    const exitedStudents = students.filter(function (s) {
      const d = s['Exit Date'] ? new Date(s['Exit Date']) : null;
      return d && (d.getMonth() + 1) === ym.month && d.getFullYear() === ym.year;
    }).length;

    return {
      month: MONTH_NAMES[ym.month - 1] + ' ' + ym.year,
      totalCollection: totalCollection,
      cashCollection: cashCollection,
      onlineCollection: onlineCollection,
      feeDue: feeDue,
      totalExpenses: totalExpenses,
      net: totalCollection - totalExpenses,
      newStudents: newStudents,
      exitedStudents: exitedStudents
    };
  });

  return rows.reverse();
}

// Due Tracking list — every Fee record still carrying a balance, oldest
// Session Start Date first / newest last, so staff work the longest-
// outstanding sessions off the top of the table down. A record with a
// blank or unparseable Session Start Date has no meaningful position in
// that order, so it's deliberately sorted to the very end (Infinity)
// rather than left wherever a NaN comparator happens to land it — 'Session
// Start Date' is a required field on every new Fee record, so this only
// ever matters for older data that predates that requirement.
function getDueList() {
  const fees = getFees();
  function sortValue_(f) {
    const raw = f['Session Start Date'];
    if (!raw) return Infinity;
    const d = new Date(raw);
    return isNaN(d.getTime()) ? Infinity : d.getTime();
  }
  return fees
    .filter(function (f) { return Number(f['Balance Due']) > 0; })
    .sort(function (a, b) { return sortValue_(a) - sortValue_(b); });
}

// ─────────────────────────────────────────────────────────────────────────
// SESSION ACTIVITY
// There's no separate session/attendance entity in this data model yet
// (see README's "Future modules" note) — the only session-level date
// anywhere is FEES' 'Session Start Date', one per billing record. So "when
// did this child's sessions last start" is read as the newest Session
// Start Date across all of a student's Fee records, the same source
// getDueList() above already sorts by. This feeds the Dashboard's two
// "no new session" KPI tiles (visible to every role, same as the rest of
// the base Dashboard KPIs) and the Reports tab detail table below them.
// ─────────────────────────────────────────────────────────────────────────

// Newest Session Start Date per Student ID, as real Date objects (only
// over fees with a parseable date) — a child with no Fee record at all,
// or none with a usable date, simply has no entry here, and callers treat
// that as "never had a session logged" rather than skipping the child.
function getLastSessionDateByStudent_() {
  const fees = getFees();
  const lastByStudent = {};
  fees.forEach(function (f) {
    const raw = f['Session Start Date'];
    if (!raw) return;
    const d = new Date(raw);
    if (isNaN(d.getTime())) return;
    const sid = f['Student ID'];
    if (!lastByStudent[sid] || d > lastByStudent[sid]) lastByStudent[sid] = d;
  });
  return lastByStudent;
}

function daysBetween_(fromDate, toDate) {
  return Math.floor((toDate.getTime() - fromDate.getTime()) / (24 * 60 * 60 * 1000));
}

// Every Active child whose newest Session Start Date is at least minDays
// old — or who has no Fee record with a usable Session Start Date at all,
// treated as maximally stale ("Never") rather than excluded, since "no
// session ever logged" is at least as much a red flag as "no *recent*
// session". Shared by the Dashboard tiles (via getSessionGapStats_) and
// the Reports tab table (via getStaleSessionReport), so both always agree.
function getStaleSessionChildren_(minDays) {
  const students = getStudents().filter(function (s) { return s['Student Status'] === 'Active'; });
  const lastByStudent = getLastSessionDateByStudent_();
  const today = new Date(Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'yyyy-MM-dd'));

  return students
    .map(function (s) {
      const last = lastByStudent[s['Student ID']] || null;
      const daysSince = last ? daysBetween_(last, today) : null;
      return {
        studentId: s['Student ID'],
        studentName: s['Student Name'],
        parentName: s['Parent/Guardian Name'],
        parentMobile: s['Parent Mobile'],
        therapies: s['Therapies Taking'],
        joiningDate: s['Joining Date'],
        lastSessionDate: last ? formatDate_(last) : '',
        daysSinceLastSession: daysSince
      };
    })
    .filter(function (r) { return r.daysSinceLastSession === null || r.daysSinceLastSession >= minDays; })
    .sort(function (a, b) {
      const da = a.daysSinceLastSession === null ? Infinity : a.daysSinceLastSession;
      const db = b.daysSinceLastSession === null ? Infinity : b.daysSinceLastSession;
      return db - da;
    });
}

// Dashboard KPI counts — visible to every logged-in role.
function getSessionGapStats_() {
  return {
    noSession40: getStaleSessionChildren_(40).length,
    noSession60: getStaleSessionChildren_(60).length
  };
}

// Reports tab detail table: every Active child with no new session in 40+
// days. Because 40 is the lower of the two Dashboard thresholds, this list
// already contains every 60+ day child too — each row's
// daysSinceLastSession tells staff which bucket it's actually in, so
// nothing needs a second, near-duplicate report.
function getStaleSessionReport() {
  return getStaleSessionChildren_(40);
}

// ─────────────────────────────────────────────────────────────────────────
// ENQUIRY MANAGEMENT
// Flow: Enquiry → Follow-ups → Visit/Assessment → Convert → Student.
// The Enquiry record and its follow-up history are preserved permanently
// after conversion — they're never deleted, only the Status changes.
// ─────────────────────────────────────────────────────────────────────────

function getEnquiryOptions() {
  return {
    services: ENQUIRY_FOR,
    sources: ENQUIRY_SOURCES,
    statuses: ENQUIRY_STATUSES,
    lostReasons: LOST_REASONS,
    contactModes: CONTACT_MODES
  };
}

// 'None' = Converted/Lost (no longer actionable). 'Not Scheduled' = open
// enquiry with no Next Follow-up Date set yet.
function getEnquiryPriority_(e) {
  if (e.Status === 'Converted' || e.Status === 'Lost') return 'None';
  if (!e['Next Follow-up Date']) return 'Not Scheduled';
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const due = new Date(e['Next Follow-up Date']); due.setHours(0, 0, 0, 0);
  if (isNaN(due.getTime())) return 'Not Scheduled';
  if (due < today) return 'Overdue';
  if (due.getTime() === today.getTime()) return 'Due Today';
  return 'Upcoming';
}

// Admin sees every enquiry. Coordinators see only enquiries assigned to
// them or created by them (server-enforced, not just hidden in the UI).
function getEnquiries(currentUser) {
  const sheet = getSheet_(SHEETS.ENQUIRIES);
  const admin = getUserRole_(currentUser).toLowerCase() === ROLES.ADMIN.toLowerCase();
  let rows = sheetToObjects_(sheet).map(function (e) {
    e['Enquiry Date'] = formatDate_(e['Enquiry Date']);
    e['Last Follow-up Date'] = formatDate_(e['Last Follow-up Date']);
    e['Next Follow-up Date'] = formatDate_(e['Next Follow-up Date']);
    e['Converted Date'] = formatDate_(e['Converted Date']);
    e['Created Date'] = formatDate_(e['Created Date']);
    e['Last Updated'] = formatDate_(e['Last Updated']);
    e.Priority = getEnquiryPriority_(e);
    return e;
  });
  if (!admin) {
    rows = rows.filter(function (e) {
      return String(e['Assigned To'] || '').toLowerCase() === String(currentUser || '').toLowerCase() ||
        String(e['Created By'] || '').toLowerCase() === String(currentUser || '').toLowerCase();
    });
  }
  return rows.sort(function (a, b) { return new Date(b['Enquiry Date']) - new Date(a['Enquiry Date']); });
}

// Checked before creating a new enquiry so staff can see whether this
// mobile number already has an enquiry or student record, and decide for
// themselves rather than the system silently merging or duplicating.
function checkDuplicateContact(mobile) {
  const m = String(mobile || '').trim();
  if (!m) return { enquiry: null, student: null };
  const enquiry = sheetToObjects_(getSheet_(SHEETS.ENQUIRIES)).find(function (e) {
    return String(e['Mobile Number'] || '').trim() === m;
  });
  const student = sheetToObjects_(getSheet_(SHEETS.STUDENTS)).find(function (s) {
    return String(s['Parent Mobile'] || '').trim() === m || String(s['Alternate Mobile'] || '').trim() === m;
  });
  return {
    enquiry: enquiry ? { enquiryId: enquiry['Enquiry ID'], childName: enquiry['Child Name'], status: enquiry.Status } : null,
    student: student ? { studentId: student['Student ID'], studentName: student['Student Name'], status: student['Student Status'] } : null
  };
}

// Create or update the enquiry's basic details. Status, follow-up dates,
// Lost Reason, and conversion fields are intentionally NOT editable here
// — those are only ever changed via addFollowup() / markEnquiryConverted()
// so there's always a follow-up trail or conversion record behind a
// status change.
function saveEnquiry(data, currentUser) {
  const sheet = getSheet_(SHEETS.ENQUIRIES);
  const now = new Date();
  const enquiryFor = Array.isArray(data.enquiryFor) ? data.enquiryFor.join(', ') : (data.enquiryFor || '');
  if (!String(data.mobile || '').trim()) throw new Error('Mobile number is required.');

  if (data.enquiryId) {
    const rowIdx = findRowIndexById_(sheet, 'Enquiry ID', data.enquiryId);
    if (rowIdx === -1) throw new Error('Enquiry not found: ' + data.enquiryId);
    const headers = HEADERS.ENQUIRIES;
    const rowValues = headers.map(function (h) {
      switch (h) {
        case 'Child Name': return data.childName || '';
        case 'Parent/Guardian Name': return data.parentName || '';
        case 'Mobile Number': return data.mobile;
        case 'Age': return data.age || '';
        case 'City/Area': return data.city || '';
        case 'Enquiry For': return enquiryFor;
        case 'Source': return data.source || '';
        case 'Source Detail': return data.sourceDetail || '';
        case 'Assigned To': return data.assignedTo || '';
        case 'Remarks': return data.remarks || '';
        case 'Last Updated': return now;
        case 'Updated By': return currentUser || '';
        default: return sheet.getRange(rowIdx, headers.indexOf(h) + 1).getValue();
      }
    });
    sheet.getRange(rowIdx, 1, 1, rowValues.length).setValues([rowValues]);
    audit_(currentUser, 'Enquiry Updated', 'Enquiry', data.enquiryId, 'Updated enquiry details.');
    return { success: true, enquiryId: data.enquiryId, message: 'Enquiry updated.' };
  } else {
    const enquiryId = nextId_(sheet, 'Enquiry ID', 'ENQ');
    sheet.appendRow([
      enquiryId, now, data.childName || '', data.parentName || '', data.mobile,
      data.age || '', data.city || '', enquiryFor, data.source || '', data.sourceDetail || '',
      'New', data.assignedTo || currentUser || '', '', data.nextFollowUpDate || '',
      data.remarks || '', '', '', '', currentUser || '', now, now, currentUser || ''
    ]);
    audit_(currentUser, 'Enquiry Created', 'Enquiry', enquiryId, 'New enquiry for ' + (data.childName || data.parentName || data.mobile));
    return { success: true, enquiryId: enquiryId, message: 'Enquiry created.' };
  }
}

// Logs a follow-up as a new, permanent row in EnquiryFollowups (never
// overwritten), then rolls Last/Next Follow-up Date and Status up onto
// the Enquiries row.
function addFollowup(data, currentUser) {
  const enquiriesSheet = getSheet_(SHEETS.ENQUIRIES);
  const followupsSheet = getSheet_(SHEETS.ENQUIRY_FOLLOWUPS);
  const now = new Date();

  const rowIdx = findRowIndexById_(enquiriesSheet, 'Enquiry ID', data.enquiryId);
  if (rowIdx === -1) throw new Error('Enquiry not found: ' + data.enquiryId);

  const eHeaders = HEADERS.ENQUIRIES;
  const prevStatus = enquiriesSheet.getRange(rowIdx, eHeaders.indexOf('Status') + 1).getValue();
  const newStatus = data.status || prevStatus;

  if (newStatus === 'Lost' && !String(data.lostReason || '').trim()) {
    throw new Error('A Lost Reason is required when marking an enquiry Lost.');
  }

  const followupId = nextId_(followupsSheet, 'Follow-up ID', 'FU');
  followupsSheet.appendRow([
    followupId, data.enquiryId, data.followUpDate || now, data.contactMode || '',
    data.remarks || '', data.nextFollowUpDate || '', newStatus, currentUser || '', now
  ]);

  enquiriesSheet.getRange(rowIdx, eHeaders.indexOf('Last Follow-up Date') + 1).setValue(data.followUpDate || now);
  enquiriesSheet.getRange(rowIdx, eHeaders.indexOf('Next Follow-up Date') + 1).setValue(data.nextFollowUpDate || '');
  enquiriesSheet.getRange(rowIdx, eHeaders.indexOf('Status') + 1).setValue(newStatus);
  if (newStatus === 'Lost') {
    enquiriesSheet.getRange(rowIdx, eHeaders.indexOf('Lost Reason') + 1).setValue(data.lostReason || '');
  }
  enquiriesSheet.getRange(rowIdx, eHeaders.indexOf('Last Updated') + 1).setValue(now);
  enquiriesSheet.getRange(rowIdx, eHeaders.indexOf('Updated By') + 1).setValue(currentUser || '');

  audit_(currentUser, 'Follow-up Added', 'Enquiry', data.enquiryId, 'Via ' + (data.contactMode || '—') + (data.remarks ? ': ' + data.remarks : ''));
  if (newStatus !== prevStatus) {
    audit_(currentUser, 'Status Changed', 'Enquiry', data.enquiryId, prevStatus + ' → ' + newStatus);
    if (newStatus === 'Lost') audit_(currentUser, 'Enquiry Marked Lost', 'Enquiry', data.enquiryId, data.lostReason || '');
    if (newStatus === 'On Hold') audit_(currentUser, 'Enquiry Put On Hold', 'Enquiry', data.enquiryId, data.remarks || '');
    if ((prevStatus === 'Lost' || prevStatus === 'On Hold') && newStatus !== 'Lost' && newStatus !== 'On Hold' && newStatus !== 'Converted') {
      audit_(currentUser, 'Enquiry Reopened', 'Enquiry', data.enquiryId, prevStatus + ' → ' + newStatus);
    }
  }

  return { success: true, message: 'Follow-up saved.' };
}

function getFollowupsByEnquiry(enquiryId) {
  return sheetToObjects_(getSheet_(SHEETS.ENQUIRY_FOLLOWUPS))
    .filter(function (f) { return f['Enquiry ID'] === enquiryId; })
    .map(function (f) {
      f['Follow-up Date'] = formatDate_(f['Follow-up Date']);
      f['Next Follow-up Date'] = formatDate_(f['Next Follow-up Date']);
      f['Created Date'] = formatDate_(f['Created Date']);
      return f;
    })
    .sort(function (a, b) { return new Date(b['Created Date']) - new Date(a['Created Date']); });
}

// Called after the existing Student Management flow (saveStudent) has
// already created the new student record — this just links the two
// records together and flips the enquiry's status. The enquiry row and
// all its follow-up history stay exactly where they are.
function markEnquiryConverted(enquiryId, studentId, currentUser) {
  const sheet = getSheet_(SHEETS.ENQUIRIES);
  const rowIdx = findRowIndexById_(sheet, 'Enquiry ID', enquiryId);
  if (rowIdx === -1) throw new Error('Enquiry not found: ' + enquiryId);
  const headers = HEADERS.ENQUIRIES;
  const now = new Date();
  sheet.getRange(rowIdx, headers.indexOf('Status') + 1).setValue('Converted');
  sheet.getRange(rowIdx, headers.indexOf('Converted Date') + 1).setValue(now);
  sheet.getRange(rowIdx, headers.indexOf('Student ID') + 1).setValue(studentId);
  sheet.getRange(rowIdx, headers.indexOf('Last Updated') + 1).setValue(now);
  sheet.getRange(rowIdx, headers.indexOf('Updated By') + 1).setValue(currentUser || '');
  audit_(currentUser, 'Enquiry Converted', 'Enquiry', enquiryId, 'Converted to Student ID ' + studentId);
  return { success: true, message: 'Enquiry marked as converted.' };
}

function deleteEnquiry(enquiryId, currentUser, reason) {
  requireAdmin_(currentUser);
  const sheet = getSheet_(SHEETS.ENQUIRIES);
  const rowIdx = findRowIndexById_(sheet, 'Enquiry ID', enquiryId);
  if (rowIdx === -1) throw new Error('Enquiry not found: ' + enquiryId);
  archiveRow_(sheet, HEADERS.ENQUIRIES, rowIdx, SHEETS.DELETED_ENQUIRIES, currentUser, [reason || '']);
  audit_(currentUser, 'Enquiry Deleted', 'Enquiry', enquiryId, reason || '');
  return { success: true, message: 'Enquiry deleted and archived to Deleted_enquiries. Follow-up history preserved.' };
}

// ── Enquiry dashboard ───────────────────────────────────────────────────

function buildFollowUpActionTable_(enquiries) {
  const order = { 'Overdue': 0, 'Due Today': 1, 'Upcoming': 2 };
  return enquiries
    .filter(function (e) { return e.Priority === 'Overdue' || e.Priority === 'Due Today' || e.Priority === 'Upcoming'; })
    .sort(function (a, b) {
      return order[a.Priority] - order[b.Priority] || new Date(a['Next Follow-up Date']) - new Date(b['Next Follow-up Date']);
    });
}

function buildSourceSummary_(enquiries) {
  const map = {};
  enquiries.forEach(function (e) {
    const s = e.Source || 'Other';
    if (!map[s]) map[s] = { source: s, enquiries: 0, converted: 0 };
    map[s].enquiries++;
    if (e.Status === 'Converted') map[s].converted++;
  });
  return Object.keys(map).map(function (k) {
    const r = map[k];
    r.conversionPct = r.enquiries ? Math.round((r.converted / r.enquiries) * 1000) / 10 : 0;
    return r;
  }).sort(function (a, b) { return b.enquiries - a.enquiries; });
}

// One enquiry may select multiple services — counted under EACH selected
// service here, unlike Total Enquiries (which counts unique Enquiry IDs).
function buildServiceSummary_(enquiries) {
  const map = {};
  enquiries.forEach(function (e) {
    const services = String(e['Enquiry For'] || '').split(',').map(function (s) { return s.trim(); }).filter(Boolean);
    services.forEach(function (svc) {
      if (!map[svc]) map[svc] = { service: svc, enquiries: 0, converted: 0 };
      map[svc].enquiries++;
      if (e.Status === 'Converted') map[svc].converted++;
    });
  });
  return Object.keys(map).map(function (k) {
    const r = map[k];
    r.conversionPct = r.enquiries ? Math.round((r.converted / r.enquiries) * 1000) / 10 : 0;
    return r;
  }).sort(function (a, b) { return b.enquiries - a.enquiries; });
}

function buildStaffPerformance_(enquiries) {
  const map = {};
  enquiries.forEach(function (e) {
    const staff = e['Assigned To'] || 'Unassigned';
    if (!map[staff]) map[staff] = { staff: staff, enquiries: 0, converted: 0, pendingFollowUps: 0 };
    map[staff].enquiries++;
    if (e.Status === 'Converted') map[staff].converted++;
    if (e.Priority === 'Overdue' || e.Priority === 'Due Today' || e.Priority === 'Upcoming') map[staff].pendingFollowUps++;
  });
  return Object.keys(map).map(function (k) {
    const r = map[k];
    r.conversionPct = r.enquiries ? Math.round((r.converted / r.enquiries) * 1000) / 10 : 0;
    return r;
  }).sort(function (a, b) { return b.enquiries - a.enquiries; });
}

// startDateStr/endDateStr are optional 'yyyy-MM-dd' strings. Total/New/
// Converted/Lost/Conversion Rate are scoped to that date range (by
// Enquiry Date); Follow-ups Due Today/Overdue are always "as of today"
// regardless of the range, since they're about upcoming action, not
// historical volume. Source/Service/Staff breakdowns are Admin-only.
function getEnquiryDashboardStats(startDateStr, endDateStr, currentUser) {
  const admin = getUserRole_(currentUser).toLowerCase() === ROLES.ADMIN.toLowerCase();
  const all = getEnquiries(currentUser);
  const start = startDateStr ? new Date(startDateStr) : null;
  const end = endDateStr ? new Date(endDateStr) : null;
  if (end) end.setHours(23, 59, 59, 999);

  const inRange = all.filter(function (e) {
    const d = e['Enquiry Date'] ? new Date(e['Enquiry Date']) : null;
    if (!d) return false;
    if (start && d < start) return false;
    if (end && d > end) return false;
    return true;
  });

  const totalEnquiries = inRange.length;
  const converted = inRange.filter(function (e) { return e.Status === 'Converted'; }).length;
  const lost = inRange.filter(function (e) { return e.Status === 'Lost'; }).length;
  const conversionRate = totalEnquiries ? Math.round((converted / totalEnquiries) * 1000) / 10 : 0;

  const result = {
    totalEnquiries: totalEnquiries,
    // Every enquiry starts life as "New" on creation, so "New Enquiries
    // received this period" and "Total Enquiries this period" are the
    // same count in this simple status model — see README.
    newEnquiries: totalEnquiries,
    followUpsDueToday: all.filter(function (e) { return e.Priority === 'Due Today'; }).length,
    overdueFollowUps: all.filter(function (e) { return e.Priority === 'Overdue'; }).length,
    converted: converted,
    conversionRate: conversionRate,
    lost: lost,
    followUpActionTable: buildFollowUpActionTable_(all)
  };

  if (admin) {
    result.sourceSummary = buildSourceSummary_(inRange);
    result.serviceSummary = buildServiceSummary_(inRange);
    result.staffPerformance = buildStaffPerformance_(inRange);
  }
  return result;
}

// Admin-only historical table, same month-range pattern as getMonthlySummary.
function getEnquiryMonthlySummary(startYear, startMonth, endYear, endMonth, currentUser) {
  requireAdmin_(currentUser);
  const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'];
  const enquiries = getEnquiries(currentUser);

  const months = [];
  let y = Number(startYear), m = Number(startMonth);
  const endY = Number(endYear), endM = Number(endMonth);
  let guard = 0;
  while ((y < endY || (y === endY && m <= endM)) && guard < 60) {
    months.push({ year: y, month: m });
    m++;
    if (m > 12) { m = 1; y++; }
    guard++;
  }

  const rows = months.map(function (ym) {
    const monthEnquiries = enquiries.filter(function (e) {
      const d = e['Enquiry Date'] ? new Date(e['Enquiry Date']) : null;
      return d && (d.getMonth() + 1) === ym.month && d.getFullYear() === ym.year;
    });
    const converted = monthEnquiries.filter(function (e) { return e.Status === 'Converted'; }).length;
    const lost = monthEnquiries.filter(function (e) { return e.Status === 'Lost'; }).length;
    return {
      month: MONTH_NAMES[ym.month - 1] + ' ' + ym.year,
      enquiries: monthEnquiries.length,
      converted: converted,
      lost: lost,
      conversionPct: monthEnquiries.length ? Math.round((converted / monthEnquiries.length) * 1000) / 10 : 0
    };
  });

  return rows.reverse();
}

// The Staff Management module (Therapists directory and their leave
// requests) lives in StaffManagement.gs.
