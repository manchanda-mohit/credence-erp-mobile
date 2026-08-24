# Credence — Setup & Deployment

Version 1 scope: Login → Student Information → Therapy Selection → Fee
Management → Payment → Due Tracking → Dashboard → Reports. No separate
Therapy Management module — therapy data lives on the Student and Fee
records (see "Future modules" below).

## 1. Create the Google Sheet

1. Go to [sheets.google.com](https://sheets.google.com) → create a new
   blank spreadsheet. Name it "Credence".

## 2. Add the Apps Script project

**If you're upgrading an existing project that already has a
`SessionScheduling.gs` file** (from a version before Session Scheduling
was replaced by Staff Management): delete that file first — right-click
it in the file list → **Delete**. It declares some of the same names
(`THERAPIST_SERVICES` and others) as `StaffManagement.gs` below, and Apps
Script will throw `Identifier '...' has already been declared` at
runtime if both are present. Everything it did has already been
reimplemented in `StaffManagement.gs`/`Leaves.gs`.

1. In the Sheet, go to **Extensions → Apps Script**. This opens a script
   project already bound to your sheet.
2. Delete the default empty `Code.gs` content and paste in the contents of
   **Code.gs** from this package.
3. Click the **+** next to "Files" → **Script** → name it exactly
   `StaffManagement` (Apps Script adds the `.gs` extension itself). Paste
   in the contents of **StaffManagement.gs** from this package. This is
   required, not optional — the Therapists directory (everything under
   the Staff Management sidebar item's "Therapists" tab) is defined
   entirely in this file, and other parts of the app (Leaves.gs, Fee
   Management's therapy pickers) call into it. Apps Script merges every
   `.gs` file in a project into one shared runtime, so this doesn't need
   any special linking — just make sure the file exists in the project
   alongside `Code.gs`.
4. Same again for **Expenses.gs** — **+** → **Script** → name it exactly
   `Expenses`, paste in its contents. This holds the whole Expenses
   Management module.
5. Same again for **Leaves.gs** — **+** → **Script** → name it exactly
   `Leaves`, paste in its contents. This holds the whole Therapist Leave
   Management module (the "Leaves" tab inside Staff Management).
6. Same again for **ToDo.gs** — **+** → **Script** → name it exactly
   `ToDo`, paste in its contents. This holds the whole To-Do Dashboard
   module.
7. Same again for **MobileApi.gs** — **+** → **Script** → name it exactly
   `MobileApi`, paste in its contents. This holds the JSON API the
   Android app talks to (see **Mobile app (Android)** below) — the
   existing web app doesn't use it at all, so skip this file if you have
   no plans to use the Android app, though there's no harm in adding it
   either way.
8. Click the **+** next to "Files" → **HTML** → name it exactly `Index`
   (Apps Script adds the `.html` extension itself). Paste in the contents
   of **Index.html** from this package.
9. Click the gear icon (Project Settings) → check "Show appsscript.json in
   editor". Open `appsscript.json` and replace its contents with the
   **appsscript.json** file from this package.
10. Save the project (⌘S / Ctrl+S).

## 3. Run setup once

1. In the Apps Script editor, select the function dropdown near the Run
   button and choose **setup**.
2. Click **Run**. The first run will ask you to authorize the script —
   accept the permissions (it only touches this one spreadsheet).
3. `setup()` is safe to re-run any time — it only creates sheets/headers
   that are missing and never touches a sheet that already has content.
   If a later version of this app adds a new column to an existing sheet
   (e.g. the `Discount Given` column added to `PAYMENTS`), re-running
   `setup()` appends just that missing column at the end of the header row
   — existing columns and every row of data are left exactly as they are.
   By default everything lives in the one spreadsheet you bound the script
   to — you'll see twenty-one tabs after running `setup()`: `STUDENTS`,
   `FEES`, `PAYMENTS`, `USERS`, `TherapyFees`, `Enquiries`,
   `EnquiryFollowups`, `Audit_log`, `Deleted_students`, `Deleted_fees`,
   `Deleted_payments`, `Deleted_users`, `Deleted_enquiries`, `Therapists`,
   `Deleted_therapists`, `Expenses`, `Deleted_expenses`,
   `TherapistLeaveRequests`, `LeaveAdjustments`, `StaffAuditLog`, `ToDos`
   — plus whatever earlier tabs you already had. See **Four-database
   architecture** below if you want to split these across separate
   spreadsheets instead.
4. `USERS` will have one seeded row (only created if USERS was empty):
   username `admin`, password `admin123`, role `Admin`. **Open the USERS
   sheet and change this password**, and add other staff either by editing
   the sheet directly or via the in-app **Users** screen (Admin-only —
   see Roles below).
5. `TherapyFees` will be pre-populated with one blank row per Therapy ×
   Session Type combination (OT/PT/Reflexes, SP, SpEd, ABA, Sports × 20
   Sessions / Monthly [M-F] / Monthly [M-S]). **Fill in the Fee Amount
   column** before staff start creating fee records — the app reads its
   fee amounts from this sheet. If you're upgrading from an earlier
   version with different therapy codes, `setup()` won't touch your
   existing rows (it never overwrites existing sheet data) — add the new
   codes' rows to `TherapyFees` yourself and remove any codes you no
   longer use.

## Four-database architecture (optional)

By default the whole app — Students/Fees, Enquiries (+ To-Do), Staff
(Therapists + Leave), and Expenses — lives in the one spreadsheet the
script is bound to, and `setup()` above is all you need. If you'd rather
keep them as physically separate Google Sheets (e.g. for separate
sharing/access control per team), you can split any or all of them out
**without any code change**. Here's the full playbook:

### What goes where

| Script Property | Spreadsheet holds |
|---|---|
| `STUDENT_FEE_SPREADSHEET_ID` | Students, Fees, Payments, Users, TherapyFees, Audit log, their `Deleted_*` archives |
| `ENQUIRY_SPREADSHEET_ID` | Enquiries, EnquiryFollowups, **ToDos**, `Deleted_enquiries` |
| `STAFF_SPREADSHEET_ID` | Therapists, TherapistLeaveRequests, LeaveAdjustments, StaffAuditLog, `Deleted_therapists` |
| `EXPENSE_SPREADSHEET_ID` | Expenses, `Deleted_expenses` |

### Steps

1. **Create four blank Google Sheets** — one per row above. File → New →
   Blank spreadsheet, four times. Name them however's clear to you (e.g.
   "Credence — Students & Fees," "Credence — Enquiries," "Credence —
   Staff," "Credence — Expenses"). Don't add any tabs or headers
   yourself — `setup()` builds those automatically on the next run.
2. **Copy each spreadsheet's ID** from its URL — the long string between
   `/d/` and `/edit`:
   ```
   https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit
                                           └──────── this part ────────┘
   ```
3. **Open the Apps Script editor** from your main bound Sheet
   (Extensions → Apps Script) → **Project Settings** (gear icon) →
   scroll to **Script Properties** → **Add script property**, four
   times, one per row in the table above (property name exactly as
   shown, value = that spreadsheet's ID).
4. **Run `setup()` again** — function dropdown at the top of the Apps
   Script editor → select `setup` → **Run**. It reads the properties you
   just set and creates the correct sheets in the correct spreadsheet.
   Safe to run even on a live sheet — it only ever creates what's
   missing and never touches existing data.
5. **Verify** — open each of the four new spreadsheets and confirm the
   right tabs appeared (per the table above). Your original bound
   spreadsheet keeps whatever it already had; nothing is deleted or
   moved automatically.
6. **No redeploy needed** — the web app URL your staff already use
   doesn't change. The app talks to whichever spreadsheet each property
   points to behind the scenes; nobody using the app will notice
   anything moved.

**⚠️ The one real trap here**: `setup()` only *creates missing sheets*
— it never moves *existing* data between spreadsheets. If you add data
(a student, a therapist, an expense...) *before* setting a property and
re-running `setup()`, that data stays behind in the old spreadsheet
untouched, while `setup()` creates a **brand-new empty** sheet of the
same name in the new one — and the app now reads from the new (emptier)
location. Symptom: something you added earlier seems to have
"disappeared," even though it's really just sitting in the old
spreadsheet, unread. **Do the split before entering real data if you
can** — or if you've already got data, manually copy the relevant rows
from the old spreadsheet's tab into the new one after running `setup()`,
matching the column order exactly.

**⚠️ A second trap worth knowing about**: a typo, stray space, or wrong
value in one of these four Script Properties used to be able to leave a
page stuck indefinitely on "Loading…" with no error at all, since
`SpreadsheetApp.openById()` doesn't always fail cleanly or quickly for a
bad ID. `getSpreadsheet_()` in `Code.gs` now catches this and fails
immediately with one specific message naming exactly which property is
wrong and what value it currently has — if you ever see an error like
*"Could not open the spreadsheet configured for STAFF_SPREADSHEET_ID…"*,
that error is telling you precisely where to look: double-check that
property's value against the spreadsheet's actual URL. A property left
blank or set to only whitespace is treated as unset (falls back to the
bound spreadsheet) rather than erroring.

Any property you leave unset falls back to the script's bound
spreadsheet, so you can do all four, just one or two, or none — mix and
match. These IDs are never sent to the frontend — only sheet data is, via
`google.script.run` calls.

**Performance on a split setup**: `getSpreadsheet_()` now caches each
bucket's opened spreadsheet for the lifetime of a single request, so
opening (say) 5 different sheets that all live in your Students/Fees
spreadsheet costs one real `SpreadsheetApp.openById()`, not 5. This
matters specifically because you're running split spreadsheets — before
this cache existed, every `getSheet_()` call re-opened its spreadsheet
from scratch, which was free in the default single-spreadsheet setup
(`SpreadsheetApp.getActiveSpreadsheet()` has no real cost) but a genuine
redundant network round-trip on a split one. The Dashboard's **Last
updated** tile (see below) is the clearest example: without this cache,
loading the dashboard would have opened your Students/Fees spreadsheet
and your Enquiries spreadsheet twice each in one request. With it,
each opens once, same as everything else the dashboard reads.

## 4. Deploy as a web app

1. Click **Deploy → New deployment**.
2. Click the gear next to "Select type" → **Web app**.
3. Set "Execute as" → **Me**, "Who has access" → **Anyone** (or "Anyone
   within [your org]" if you're on Google Workspace and want it restricted
   to your domain).
4. Click **Deploy**, authorize again if prompted, and copy the **Web app
   URL**. That URL is the Credence app — bookmark it for daily use.
5. Whenever you edit `Code.gs` or `Index.html` later, go to **Deploy →
   Manage deployments → edit (pencil) → New version → Deploy** to push the
   changes live; the URL stays the same.

## Roles

- **Admin** — full access, plus can delete student, fee, payment, and user
  records, sees the full dashboard (including the per-therapy panel and
  the Monthly Summary table), and can manage users from the **Users**
  screen. Set a user's `Role` column to `Admin`.
- **Manager** — has two things beyond Coordinator-level access: full
  permission over **Staff Management** (add/edit/delete therapists,
  including full visibility of Monthly Salary — same as Admin there),
  and the ability to **approve or reject therapist leave requests**.
  Everywhere else in the app, Manager has the same access as a
  Coordinator (below). Any role can file a leave request on a
  therapist's behalf, since therapists aren't logged-in users
  themselves. Set `Role` to `Manager`.
- **CenterHead** — a full replica of Manager. Every permission check in
  this app treats Manager and CenterHead identically — they're defined
  once in a shared `isManagerTierRole_()` helper (`Code.gs`) and
  `isManager()` (`Index.html`), rather than being duplicated across every
  Manager-gated feature, so the two roles can never drift out of sync by
  accident. If Manager's permissions ever change, CenterHead changes with
  it automatically. Set `Role` to `CenterHead`.
- **Coordinator** — front-desk staff. Can add/edit students, create/edit
  fee records, and record payments. Delete buttons are hidden for them,
  and the server independently re-checks their role against the USERS
  sheet on every delete call — a Coordinator can't grant themselves delete
  access from the browser. Their dashboard is the simplified KPI view (see
  below). Set `Role` to `Coordinator`.

Role text is matched case-insensitively but must read exactly "Admin",
"Manager", or "CenterHead" to grant that level of access — anything else
(including a blank cell) is treated as Coordinator-level.

## Changing your own password

Every logged-in user — any role — can change their own password from
**Change password** in the sidebar footer, next to Log Out. It requires
the current password (verified server-side via `changeOwnPassword()` in
`Code.gs`) and a new password of at least 4 characters. This only ever
changes the *current user's own* password — changing anyone else's
password still goes through the Admin-only **Users** screen
(`saveUser()`), which is a deliberate separation: self-service password
changes need no special permission, but changing someone else's
credentials still does.

## Staying signed in across page refreshes

The browser remembers who's logged in — reloading the page (or closing
and reopening the tab) restores the session automatically instead of
showing the login form again every time. This works by storing the
username only (never the password) in the browser's `localStorage`, and
silently re-validating it against the Users sheet on every page load via
`restoreSession()` in `Code.gs`. If the account was deactivated, or an
Admin changed its role, since the last real login, that's picked up
immediately — `restoreSession()` always reads the *current* Active
status and Role from the sheet, never trusting a stale cached value.
**Log Out** clears this cached username, so the next page load shows the
login form again as expected.

This adds no meaningful overhead: it's one lightweight sheet lookup on
page load (about the same cost as a normal login, just without the
password check), and it replaces having to type a username and password
back in on every refresh — a net win for perceived speed, not a cost.

Because every privileged action in this app is already re-checked
server-side against the Users sheet (see Roles above) rather than
trusting whatever the browser claims about the signed-in user, caching
the username client-side introduces no new security exposure — a
tampered client-side role value was already just as easy to fake before
this existed, and would fail the same server-side checks either way.

## Deleted-record archive

Nothing is ever hard-deleted. Every delete (student, fee, payment, or
user) first copies the full row — plus who deleted it and when — into the
matching archive sheet, then removes it from the live sheet:

- `Deleted_students`, `Deleted_fees`, `Deleted_payments`, `Deleted_users`

Deleting a payment also reverses its effect on the linked fee's Amount
Paid / Balance Due / Payment Status, so the numbers stay correct. Every
delete button shows a confirmation dialog first, naming which archive
sheet the record will land in.

## Dashboard

The dashboard differs by role:

- **Admin** sees: Active students, Total/Cash/Online Collection (this
  month), Total Fee Due, Children With Dues, New/Exited Students (this
  month) — plus the all-time "Collection by therapy" panel, a **Fee Due
  Details** table (every student currently carrying a balance, with
  parent contact info), and an admin-only **Monthly Summary** table with
  a Last 3/6/12 Months or Custom Months selector.
- **Coordinator** sees a simplified view: Cash/Online Collection (this
  month), Total Fee Due, Children With Dues, New/Exited Students (this
  month), plus the same Fee Due Details table. No per-therapy breakdown
  and no Monthly Summary.

If `TherapyFees` has duplicate rows for the same Therapy + Session Type,
the Admin dashboard shows a warning banner instead of silently picking one
— fix the sheet, or enter that fee's amount manually until it's fixed.

**Last updated** (Admin/Manager only, first tile on the dashboard): the
most recent activity timestamp — date and time — across Fees, Students,
and Enquiries. Deliberately scoped to just these three sheets: they're
already fully loaded for the rest of the dashboard's tiles on every
request, so this tile costs no additional spreadsheet reads worth
mentioning — just one more small pass over data already in memory (this
was checked and confirmed before building it, since a broader "every
sheet in the system" version would have meant several new reads per
dashboard load, and — for anyone running a split-database setup —
possibly opening spreadsheets the dashboard doesn't currently touch). It
still captures more than those three sheets' own edits: recording,
editing, or deleting a Payment always touches its linked Fee's Updated
Date, and adding an Enquiry Follow-up always rolls its timestamp onto
that enquiry's Last Updated, so Payments and Follow-up activity show up
here too without a separate read. One gap worth knowing: a pure delete
with nothing else changed won't move this tile, since the deleted row
moves to its Deleted_ archive sheet and stops being scanned.
`getLastActivityTimestamp_()` in `Code.gs` is what to change if you want
this to cover more sheets (Expenses, Staff, Leaves, To-Dos) or reach into
the archive sheets too.

**No new session (40+ / 60+ days)**: two KPI tiles, visible to every role
like the rest of the base Dashboard KPIs, counting Active children whose
most recent billed session — the newest `Session Start Date` across their
Fee records, the same field `getDueList()` already sorts by — is at least
40 (or 60) days old. There's no separate session/attendance entity in this
data model (see "Future modules" below), so `Session Start Date` on the
Fees sheet is the only session-level date available; a child with no Fee
record at all counts toward both tiles too, since "no session ever
logged" is at least as much a red flag as "no *recent* session." The
60+ count is always ≤ the 40+ count, since every 60+ day child is also a
40+ day child. Full detail — parent contact, therapies, joining date, last
session date, and exact days since — is available in three places, all
reading the same `getStaleSessionReport()` data so they never disagree:
the **Reports** tab (see below), and two dedicated tabs inside **Fee
Management** — **No new session (40+ days)** and **No new session (60+
days)** — for staff who live in Fee Management day-to-day and don't want
to jump to Reports just to see who's overdue for a session. The 60+ tab
is a client-side filter of the same 40+ response, not a second
server call. `getSessionGapStats_()` and `getStaleSessionChildren_()` in
`Code.gs` are what to adjust if you want different thresholds or to scope
this by therapy.

## Enquiry Management

A lightweight lead-tracking module: **Capture → Follow-up → Convert**.
Three new sidebar items — **Enquiry**, **Follow-ups**, **Enquiry
Dashboard** — sit alongside the existing ones and reuse the same login,
roles, and Student creation flow.

**Enquiries sheet** — one row per enquiry. Beyond the base fields the spec
listed (Enquiry ID, Enquiry Date, Child Name, Parent/Guardian Name, Mobile
Number, Age, City/Area, Enquiry For, Source, Status, Next Follow-up Date,
Remarks, Lost Reason, Created By/Date, Last Updated/By), a few columns
were added because other parts of the same spec needed them:
- `Source Detail` — free text captured when Source is "Existing Parent" or
  "Doctor Referral" (spec section 5).
- `Assigned To` — which staff member owns the enquiry; defaults to
  whoever created it. Drives the Coordinator's "assigned to them" view
  restriction and the Staff Performance table (spec sections 27–28).
- `Last Follow-up Date`, `Converted Date`, `Student ID` — needed to show
  follow-up history and to link a converted enquiry back to its Student
  record (spec sections 14 and 16).

**EnquiryFollowups sheet** — every follow-up is a new row here, never an
overwrite. The Enquiries row's Last/Next Follow-up Date and Status are
rolled up from the latest follow-up automatically.

**The Add Enquiry form is deliberately short**: Child Name, Parent Name,
Mobile (required), Age, City/Area, Enquiry For (multi-select), Source (+
detail field when relevant), Assigned To, Next Follow-up Date, Remarks —
fillable in well under a minute. Status, Lost Reason, and conversion
fields are never edited directly on this form; they only change through
the Follow-up form or Convert action, so there's always a follow-up
record or conversion behind every status change.

**Enquiry For options**: OT/PT/Reflexes, SP, SPED, Sports, ABA,
BrainGym/BodyGym — this is a separate list from the Student module's
Therapies Taking list (OT/PT/Reflexes, SP, **SpEd**, ABA, Sports; no
BrainGym/BodyGym) because the enquiry spec defined its own options
verbatim. **Convert to Student** matches them case-insensitively (SPED ↔
SpEd) when pre-checking therapy boxes on the student form, but
BrainGym/BodyGym has no Student-side equivalent and won't carry over —
add it manually on the student record after conversion if needed.

**Duplicate check**: typing a mobile number on the Add Enquiry form checks
both Enquiries and Students live and shows a warning with the existing
record's name/status if found — staff decide whether to proceed, nothing
is blocked automatically.

**Convert to Student** opens the existing Add Student modal, pre-filled
with only what the enquiry already collected (Child Name, Parent Name,
Mobile, City, an approximate DOB derived from Age, and matching
therapies). Staff fill in the rest and save normally — once the student
is created, the enquiry is marked Converted, stamped with the Student ID
and conversion date, and both the enquiry and its full follow-up history
stay in place as historical lead data.

**Follow-up priority** (Overdue / Due Today / Upcoming / Not Scheduled) is
computed live from Next Follow-up Date vs. today, for any enquiry that
isn't Converted or Lost. The Enquiry page's quick filters and the
Follow-ups sidebar page both use this. "Overdue" and "Due Today" counts on
the dashboard are always as-of-today regardless of the selected date
range — the date filter only scopes the volume/conversion numbers.

**Role visibility**: Admin sees every enquiry and the full dashboard
(Source Summary, Service Summary, Staff Performance, Monthly Summary).
Coordinators only see enquiries assigned to or created by them — enforced
server-side in `getEnquiries()`, not just hidden in the UI — and their
dashboard shows only the KPI cards plus their own Follow-ups Requiring
Attention table.

**Service Summary counting rule**: Total Enquiries always counts unique
Enquiry IDs. The Service Summary table counts an enquiry once under
*each* service it selected, so an OT+SP enquiry adds one to both rows —
the two totals won't match by design, and this is called out on-screen
next to the table.

**Deleting an enquiry** (Admin only) prompts for an optional reason,
archives the full row plus Deleted By/Date/Reason to `Deleted_enquiries`,
and leaves `EnquiryFollowups` untouched so history survives the deletion.

**Audit_log sheet** records every enquiry lifecycle event — Created,
Updated, Follow-up Added, Status Changed, Converted, Marked Lost, Put On
Hold, Reopened, Deleted — with timestamp, user, and a short description.
It's a general-purpose log (Timestamp, User, Action, Entity Type, Entity
ID, Description), so it's ready to record Student/Fee/Payment events too
if you want that later; only enquiry actions write to it today.

## Staff Management

Replaces the old Session Scheduling module (booking, availability,
calendar — all removed) with a straightforward staff record: a
**Therapists** directory and their **Leave** history, in one sidebar item
with two tabs.

**Therapists tab**: name, Therapy/Service (OT/PT/Reflexes, SP, SPED,
Sports, or ABA), Mobile, Shift (free text, e.g. "Morning (9am–1pm)"),
**Joining Date**, Status (Active/Inactive), Notes, and **Monthly
Salary**. Admin and Manager both have full permission here — add/edit/
delete (delete archives to `Deleted_therapists` with a reason, same
pattern as every other delete in this app) and full visibility including
salary; Coordinator can view but not change anything. Own `Therapists`
sheet, own code file (`StaffManagement.gs`). Joining Date was added after
the original build — same as Shift and Monthly Salary before it — so
re-running `setup()` on an existing deployment appends just that one
missing column to the `Therapists` sheet; every existing therapist row is
left untouched with the new column simply blank until edited.

**How the Therapists list loads**: a single combined `getStaffPageData()`
call fetches the therapy/service list and every therapist row together
in one round-trip, rather than two separate parallel requests — simpler
to reason about, and removes any chance of one resolving while the other
doesn't. While loading it shows an explicit "Loading…" state; if the
request is still pending after 25 seconds (Apps Script's client library
has no built-in timeout, so a genuinely stuck request would otherwise
hang forever with zero feedback), it shows a clear message with a
one-click retry instead. Any real failure shows the actual error text
directly in the table, not just a toast that's easy to miss. Add/Edit/
Delete get the same treatment (`runStaffCall()` in `Index.html`) — no
Staff Management action leaves you staring at an indefinite spinner with
no information.

**Staff Management has its own audit log, decoupled from the shared
one**: every write here (therapist CRUD, leave requests, bonus grants)
used to call the same `audit_()` used everywhere else in the app, which
writes to `Audit_log` — a sheet that lives in the Student/Fee database
bucket. That meant a Staff Management action could depend on a totally
unrelated spreadsheet's health even when the Staff spreadsheet itself
was completely fine. `auditStaff_()` (`Code.gs`) writes to its own
`StaffAuditLog` sheet, routed to the same `staff` bucket as everything
else Staff Management touches — so a healthy Staff spreadsheet is now
genuinely enough on its own for every core Staff Management write.

**One honest limit worth knowing**: permission checks (Manager/Admin
gating) still need the `Users` sheet, which legitimately lives in the
Student/Fee bucket — every protected action *anywhere* in the app shares
this, not just Staff Management, so it isn't something a Staff
Management-specific fix can remove without moving `Users` itself
elsewhere — a bigger, riskier change that would break anyone who's
already split their Student/Fee database differently. The practical
effect: if that specific spreadsheet is ever unreachable, Staff
Management actions will still fail — but fast, with one clear error
naming `STUDENT_FEE_SPREADSHEET_ID`, not a hang.

**Leaves tab**: tracks leave for **therapists**, not app user accounts —
therapists typically aren't logged-in users of this app themselves, so
every request is filed on their behalf by whichever logged-in staff
member (Admin, Manager, or Coordinator) is recording it. There's no
"self-service, own leave" concept, since the therapist isn't the one
logging in. Own `TherapistLeaveRequests` sheet, own code file
(`Leaves.gs`).

- **File a request**: pick a therapist, leave type, Paid or Unpaid, and
  a date range — creates a `Pending` request.
- **Approve/Reject**: only a Manager or Admin can decide a pending
  request — enforced server-side (`requireManagerOrAdmin_`), not just
  hidden in the UI, the same pattern used for every other role check in
  this app. Deciding stamps Approved By/Date and an optional remark.
- **Console visible to all roles, with full history**:
  `getTherapistLeaveRequests()` is deliberately not scoped by who's
  asking — unlike Enquiries, where a Coordinator only sees their own
  slice, every logged-in user sees every leave record, filterable by
  status and by therapist. This was an explicit requirement, not an
  oversight — leave visibility is meant to be transparent across the
  team.
- **Cancel**: whoever filed a request can cancel it while it's still
  `Pending`; a Manager or Admin can cancel any request at any time,
  including one already Approved or Rejected (e.g. plans changed after
  the fact).
- **Leave Dashboard**: KPI cards (Active Therapists, On Leave Today,
  Pending Requests, Approved This Month, Leave Days This Month), a
  Pending Approvals panel (Manager/Admin only, with inline
  Approve/Reject), a Paid Leave Balance table, and a per-therapist
  summary (total/Paid/Unpaid days plus a leave-type breakdown) for the
  current calendar year — all above the console on the same tab.

**Paid leave accrual**: every active therapist accrues a configurable
number of paid leaves per calendar month — 1/month (12/year) by default,
resetting every January. Accrual is progressive, not a lump sum: by
March only 3 have accrued, not all 12 (`getAccruedPaidLeaves_` in
`Leaves.gs`). Every leave request is tagged **Paid** or **Unpaid**
(independent of Leave Type, which is about the reason — sick, casual,
earned, etc.) — only *approved* **Paid** requests count against the
accrued balance; Unpaid leave never touches it.

**Accrual rate is editable** — Manager or Admin sees an "Accrual rate
(paid leave/month)" control at the top of the Paid Leave Balance table;
changing it (`setAccrualRate()` in `Leaves.gs`, stored as a Script
Property so it doesn't need its own sheet) immediately changes accrual
math for every therapist going forward. Coordinators can view the
balance table but not this control.

**Bonus / ad-hoc leave grants** — Manager or Admin can grant extra paid
days to a specific therapist on top of the standard monthly accrual (a
**+ Grant** button per row in the balance table) — e.g. covering another
therapist's shift, or a one-off goodwill grant. Every grant
(`grantBonusLeave()`) is added into that therapist's Accrued figure
immediately and shown separately as a **Bonus** column, with a full
history log (**Bonus leave grants — history**, visible to everyone —
same transparency pattern as the rest of this module) recording who
granted what, how many days, and why.

The Paid Leave Balance table shows Accrued (base + bonus) / Bonus / Used
/ Remaining per therapist; `getLeaveBalances()` is the function to adjust
further if your centre's policy needs a cap, carry-over rules, or
anything more elaborate.

Leave Type is a fixed list (Sick Leave, Casual Leave, Earned Leave,
Unpaid Leave, Emergency Leave, Other) — edit `LEAVE_TYPES` in `Leaves.gs`
if you need different categories. Number of days is a simple inclusive
calendar-day count (end date − start date + 1) — it does not exclude
weekends or count half-days; adjust `countLeaveDays_` in `Leaves.gs` if
your centre needs that distinction.

**Upcoming Leaves tile**: the main Dashboard (visible to every role, not
just Admin/Manager) shows a count of approved leave that's either
happening today or starting within the next 7 days
(`getUpcomingLeaveSummary()` in `Leaves.gs` — adjust the 7-day window
there if you want more or less lead time).

**Note on approved leave and scheduling**: since the booking/scheduling
calendar was removed, an approved leave request no longer blocks
anything else in the app (there's nothing left to block) — it's purely
an HR record plus the paid-leave balance tracking above. If you
reintroduce any kind of booking calendar later, wiring approved leave to
block it again is a natural next step.

## To-Do Dashboard

A simple shared team task list — its own dedicated sheet (`ToDos`) and
schema, never mixed into the actual Enquiries rows/columns. That was a
real design decision, not an arbitrary one: Enquiries' schema, KPIs,
duplicate-checks, and Coordinator-scoped visibility are all built around
a child inquiry, not a generic task — reusing its *columns* would have
meant either leaving most Enquiry fields blank on every task row, or
bolting on special-case logic everywhere to keep task rows from
polluting Enquiry Dashboard's counts. `ToDos` stays its own tab with its
own columns — the only thing it shares with Enquiries is which physical
spreadsheet it lives in when you split databases (see **Four-database
architecture** above): it's routed into the `enquiry` bucket alongside
`Enquiries` and `EnquiryFollowups`, purely for organizational
convenience, not data sharing.

**Any logged-in role can add a task** — no permission gate — from the
**+ Record a to-do** button. Fields are deliberately minimal: just a
title (Task ID, Created By, and Created Date are filled in
automatically).

**Visibility is shared across the whole team**, the same "transparent to
everyone" model the Leave console uses — not scoped per-creator the way
Enquiries scopes Coordinators to their own records. Every task is visible
to every role, and **anyone can mark any task complete or reopen it**,
regardless of who created it. If you'd rather tasks be private to
whoever created them, that's a different, easy-to-make change — say so
and I'll adjust `getToDos()` in `ToDo.gs` to scope by `Created By`.

**Two tabs**: **Open** and **Completed**, both driven by the task's
`Status` column (`Open` or `Completed`) — no separate sheets or
duplicated data, just a filter on the same list. Completing a task
records who completed it and when (`Completed By` / `Completed Date`);
reopening clears both.

**Every task can carry one comment**, addable or editable at any time
after creation, by anyone — same shared-visibility model as the rest of
this dashboard, not scoped to whoever created the task. It's a single
overwritable note (`Comment`, `Comment By`, `Comment Date`), not a
threaded log: adding a new comment replaces the old one rather than
appending to a history. Each row shows the current comment (if any) plus
who left it and when, with an **Add comment** / **Edit comment** link
that opens a simple prompt — clearing the text and saving removes the
comment and its attribution entirely rather than leaving a stale
"commented by X" behind. This mirrors the lightweight `prompt()`-based
pattern already used elsewhere in the app (expense/therapist delete
reasons, leave-approval remarks) rather than introducing a new UI
pattern. `updateToDoComment()` in `ToDo.gs` is what to change if you
ever want this to become a full threaded log instead.

## Expenses Management

A straightforward business-expense tracker, independent from Fees/
Payments (which record money coming *in* from parents) — this records
money going *out* (rent, salaries, supplies, vendor bills). Its own
`Expenses` sheet, own optional spreadsheet (see above), own code file
(`Expenses.gs`), and its own sidebar item ("Expenses Management").

Fields: Date, Category, Description, Amount, Payment Mode, Paid To,
Reference Number, Remarks. Category is a fixed dropdown (Rent, Salaries &
Wages, Utilities, Therapy Supplies & Equipment, Maintenance & Repairs,
Marketing & Outreach, Travel & Transport, Office Supplies, Professional
Fees, Taxes & Licenses, Miscellaneous, Other) — edit `EXPENSE_CATEGORIES`
in `Expenses.gs` if you need different ones. Payment Mode is intentionally
broader than the parent-facing Cash/UPI-only restriction on Payment Entry
— Cash, UPI, Bank Transfer, Cheque, or Card — since business expenses
commonly go through channels a parent payment wouldn't.

**Salary vs Non-Salary split**: the top of the Expenses Management screen
shows three KPI cards — Total, Salary, and Non-Salary Expenses (this
month). This is derived from Category rather than a separate field:
anything filed under "Salaries & Wages" counts as Salary, everything else
is Non-Salary (`isSalaryCategory_` in `Expenses.gs`). No extra data entry
needed — just pick the right category when recording the expense.

**Salary data is hidden from Coordinators**: "Salaries & Wages" expense
records — and that category itself, in the Category dropdown and filter —
are only visible to Admin and Manager. Coordinators can use every other
part of Expenses Management, but salary records are filtered out
server-side (`canViewSalaryExpenses_` in `Expenses.gs`), not just hidden
in the UI, matching the role-enforcement pattern used everywhere else in
this app.

Coordinators can add and edit expenses (same permission level as Fees);
deleting is Admin-only and archives to `Deleted_expenses` with a
confirmation naming the archive destination, matching every other delete
flow in the app. Every expense action is logged to the shared `Audit_log`
sheet.

**Where it shows up elsewhere**: the Admin dashboard has "Total
Expenses (this month)" and "Net (this month)" (collection minus expenses)
KPI cards; the Monthly Summary table has Expenses and Net columns; the
Reports tab has an Admin-only "Expense summary (monthly)" category
breakdown, next to the existing Therapy-wise collection summary.

## Progressive Web App (installable)

The app can be installed to a phone's home screen or as a desktop app,
with best-effort offline resilience. This comes with real constraints
worth understanding, since it's hosted on Apps Script rather than a
normal web server:

- There's no way to serve a literal static `manifest.json` or `sw.js`
  file from Apps Script, so `doGet` routes on a `?page=` query param
  instead: `<exec URL>?page=manifest` returns the manifest JSON,
  `<exec URL>?page=sw` returns the service worker script. `Index.html`
  links to both (`<link rel="manifest" href="?page=manifest">` and a
  `navigator.serviceWorker.register('?page=sw')` call), so this is
  automatic — nothing to configure.
- The app icon reuses the same logo already embedded on the login page
  (as a base64 data URI, for the same reason — no separate static file
  hosting) rather than a second image asset to keep in sync.
- The service worker is network-first with a cached-shell fallback: it
  does **not** make your data (students, fees, sessions, expenses…)
  available offline — those are live `google.script.run` calls, which
  aren't ordinary fetches a service worker can intercept or cache. What
  it does do is let the app shell reopen with a graceful degraded state
  instead of a blank error page when the connection drops, and satisfies
  the technical requirement (manifest + active service worker) for
  browsers to offer an install prompt.
- Real-world install reliability varies by browser and platform, since
  the exec URL is a long `script.google.com` address rather than a
  custom domain — this is a best-effort PWA wrapper, not a guarantee
  every browser will offer the same install experience a normal web app
  would get.

## Mobile app (Android)

Alongside the installable PWA above, there's also a genuine native
Android app (`android-app/` in this package) with its own screens for
every module the web app has: Login, Dashboard, Students, Fee
Management (Records/Payments/Due Tracking), Enquiries, Staff Management
(Therapists + Leaves), Expenses, To-Do, Reports, and Users
(Admin-only, matching the web sidebar's own `isAdmin()` gate on that
item). Navigation is a slide-out drawer rather than a bottom bar — nine
sections doesn't fit Material Design's bottom-navigation guidance, which
tops out around five. It talks to the **same Google Sheets** as the web
app, live, through a small JSON API added in `MobileApi.gs` (see step 7
above) — nothing about the web app or its data model changed to support
this.

As of this round, the app also covers Convert Enquiry → Student (a
"Convert to child" action on any non-Converted enquiry row, opening a
pre-filled Add Student form the same way `openConvertModal()` does on
the web — name, parent/guardian name, mobile, city, and an approximate
date of birth from the enquiry's Age all carry over, plus whichever
enquiry services match a known therapy code), paid-leave accrual and
balances (the Leaves tab now has a live stat grid, a per-therapist paid
leave balance table with an editable accrual rate and a "Grant bonus
leave" action, a bonus-grant history, and a leave summary by therapist —
all Manager/Admin-gated the same way the web version is), and a compact
"mini-analytics" strip on the main Dashboard (this month's enquiry
pipeline and today's leave status) surfacing numbers that previously
only lived inside the Enquiries and Staff Management screens. The
Dashboard strip is deliberately compact — full breakdowns (enquiry
source/service/staff performance, monthly enquiry trends) stay inside
the Enquiries screen's own future "Dashboard" sub-view, not duplicated
here; that richer breakdown, along with per-therapist leave summaries
beyond what's now shown, remains a natural next addition using the same
pattern.

### Why a separate API file

The web app's `google.script.run` calls only work from inside a page
Apps Script itself served — that bridge doesn't exist for a native app
making plain HTTP requests. `MobileApi.gs` exposes the same underlying
functions (`getStudents`, `saveFee`, `recordPayment`, etc. — all
unchanged) over HTTPS JSON instead, so both the web app and the Android
app end up calling the identical server-side logic, just via two
different front doors. Every response is one consistent shape:
`{ ok: true, data: ... }` on success, `{ ok: false, error: "..." }` on
failure.

### Pointing the Android app at your deployment

1. Deploy this Apps Script project as a web app (see **4. Deploy as a
   web app** below) and copy its exec URL — the same URL the PWA install
   flow uses.
2. Open `android-app/` in Android Studio, and set that exec URL in
   `app/src/main/java/.../data/ApiConfig.kt` (one constant — see the
   comment right above it in that file).
3. Build and run. Android Studio downloads the Android SDK and every
   Gradle/Compose dependency itself on first build — that step could
   not be done inside the sandbox this project was authored in, so
   treat your first build as the real syntax check; if anything doesn't
   compile, it's most likely a small, mechanical fix (the code was
   written carefully but never run through a compiler).

### Getting an installable APK without Android Studio

There's a GitHub Actions workflow at `.github/workflows/build-apk.yml`
that builds a debug APK entirely in GitHub's cloud and hands it back to
you as a download — useful if you just want the app on your phone and
don't want to install Android Studio at all.

1. Push this whole repo (Apps Script files and `android-app/` together)
   to a GitHub repository. Set the exec URL in `ApiConfig.kt` (previous
   section) **before** you push, or the APK you get back will still be
   pointing at the placeholder URL.
2. Open the repo's **Actions** tab on GitHub. The workflow runs
   automatically on every push to `main` that touches `android-app/`;
   you can also trigger it by hand any time with the **Run workflow**
   button, on any branch.
3. Once the run finishes (a few minutes — no caching on the first run),
   open it and scroll to **Artifacts** at the bottom of the summary
   page. Download `credence-debug-apk` — it's a zip containing one file,
   `app-debug.apk`.
4. Get that APK onto your phone however's easiest — email it to
   yourself, drop it in Google Drive/Dropbox and open it on the phone,
   or plug the phone into a computer via USB and copy it over.
5. On the phone, tap the APK file to install it. Android will prompt
   you to allow installs from whichever app you opened it with (Files,
   Gmail, Drive, etc.) — approve that once, then installation continues
   normally. This is a debug build (signed with Android's standard
   auto-generated debug key, not a Play Store release key), which is
   exactly what you want for installing directly on your own device —
   it just isn't the right artifact for publishing to the Play Store.

If you'd rather build the APK yourself locally instead of using the
workflow, Android Studio's **Build → Build App Bundle(s) / APK(s) →
Build APK(s)** menu produces the same kind of file at
`android-app/app/build/outputs/apk/debug/app-debug.apk`, which you can
transfer to your phone the same way.

### Auth model

Deliberately the same trust model the web app already uses (see
`login()` / `restoreSession()` in `Code.gs`): a one-time username +
password check at sign-in, then every later call just carries the
username along — not a weaker model than the web app's, the same one.
Every existing role check (`requireAdmin_`, `requireManagerOrAdmin_`
inside `saveTherapist`/`deletePayment`/`updatePayment`/etc.) keeps
working exactly as it does today, since the API actions just call those
same functions. The one thing added specifically for this new HTTP
surface: reads like `getStudents()`/`getFees()` take no username at all
today and aren't gated, which was fine when the only way to reach them
was from inside the loaded web page — but this API is a plain public
HTTPS endpoint anyone with the URL could otherwise query directly, so
`requireApiUser_()` in `MobileApi.gs` now requires every action except
`login` to carry a username belonging to a currently Active user.

### Extending the API further

Every module's `case` lines in `routeMobileApiAction_()` inside
`MobileApi.gs` just call a function that already existed before the
Android app did — no new backend logic, only wiring. The same recipe
applies to anything still missing (the Enquiry Dashboard's fuller
source/service/staff breakdown, the Enquiry Dashboard's own monthly
trend table): add a `case` line for the existing `Code.gs`/`Leaves.gs`
function, a matching DTO in `Models.kt`, and a `CredenceRepository`
function, then wire it into whichever screen makes sense — nothing
architectural stands in the way.

## How the pieces fit together

- **STUDENTS** sheet is the source of truth for student records, including
  the `Therapies Taking` column (comma-separated, e.g. `OT, SP`).
  **Duplicate-name protection**: creating (or renaming, via edit) a child
  to exactly match an existing child's name — case-insensitive, so "Rahul
  Sharma" collides with "rahul sharma" — is blocked with 'A child named
  "[name]" already exists (Student ID ...). Please add a surname, middle
  name, or other differentiator to tell them apart.' Every current
  student counts, not just Active ones, since an Exited/Inactive
  namesake is just as confusing to have two of. Editing a student never
  collides with itself. `checkDuplicateStudentName_()` in `Code.gs` is
  what to adjust if you'd rather scope this to Active students only, or
  loosen it to a fuzzier match.
- **TherapyFees** sheet is the master rate card: Therapy, Session Type,
  Fee Amount. When staff create a fee record and pick a Therapy + Session
  Type, the Fee Amount auto-fills from this sheet and locks (read-only) —
  update rates here, not on individual fee records. If a combo has no rate
  set yet, the amount field unlocks for manual entry instead. Editable
  directly from the **Fee Management → Therapy Fees** tab — Admin and
  Manager can add/edit rates there; Coordinators can view the rate card
  but not change it (`saveFeeRate` enforces this server-side).
- **FEES** sheet holds one row per billing period. `Session Type` and
  `Session Start Date` (required — renamed from the earlier "Due Date")
  are set per fee record. **Therapy** can be a single code or a combined
  "A, B" value when a fee covers multiple therapies at once — the "New
  fee record" form's Therapy checkboxes let you select more than one, and
  when you do, their individual TherapyFees amounts are summed into one
  fee record rather than creating a separate record per therapy. Every
  report that breaks totals down by therapy (Dashboard's per-therapy
  panel, Reports' Therapy-wise summary, Fee Due Details) splits a combined
  value back into its parts and divides the amount evenly across them, so
  those totals still add up correctly. The New Fee Record form defaults
  Billing Month to the current month. Fee records have both **Edit** and
  **Delete** actions (Edit is available to Coordinators too, matching
  their existing create/edit permission; Delete stays Admin-only) —
  editing preserves the fee's existing Amount Paid so it doesn't disturb
  payments already recorded against it. **Duplicate protection**: creating
  a fee record for a Student + Therapy + Session Start Date combination
  that already exists is blocked with "Fee record already exists for
  [child] — [therapy], session starting [date] (Fee ID ...). Please
  check." — e.g. once an SP record starting 2026-08-01 exists for a
  child, a second SP record starting the same date can't be created; a
  different Session Start Date (a genuinely new session) or a different
  Therapy is unaffected. Combined records are checked therapy-by-therapy
  ("SP, OT" collides with an existing lone "SP" on the same date, and
  vice versa), and editing a record never collides with itself.
  `checkDuplicateFee_()` in `Code.gs` is what to adjust if you want the
  check to also consider Session Type, or to be relaxed/tightened.
- **PAYMENTS** sheet logs every payment received against a fee row; each
  payment updates that fee's Amount Paid / Balance Due / Payment Status
  automatically. **Record a payment** opens in a popup, same as **New fee
  record** — the Payment Entry tab itself just shows the Recent Payments
  table; the **+ Record a payment** button above it opens the entry form
  as a modal, keeping the two "create a record" flows in the app
  consistent. Payment Mode is a mandatory dropdown (Cash, UPI, or Bank
  Transfer) with no default pre-selected — there's no Receipt Number
  field to fill in, since a receipt number is always auto-generated on
  save. When a student has more than one outstanding fee record, Payment Entry's fee dropdown
  auto-selects the current billing month's record if one exists. Payment
  Entry also has an optional **Discount** field for waiving part of the
  balance while collecting a payment — it adds to that fee's running
  Discount total (on top of any discount set when the fee was created),
  permanently reduces the fee's Net Amount, and is recorded per-payment in
  the `Discount Given` column so it's auditable. Deleting a payment
  reverses both the amount and any discount it applied. **Admin-only
  Edit**: the Recent Payments table has an **Edit** action (next to
  Delete, both Admin-only) for correcting an already-recorded payment —
  amount, discount, mode, date, or remarks. Saving recalculates the
  linked fee's Amount Paid / Discount / Net Amount / Balance Due /
  Payment Status by backing out the payment's old amount/discount and
  reapplying the corrected ones, the same reverse-then-reapply approach
  Delete already uses, just without archiving the row or changing its
  Payment ID. The Fee ID a payment is linked to isn't editable — fixing a
  mistaken amount is one thing, reassigning a payment to a different fee
  record entirely is a bigger structural change and isn't exposed here.
  `updatePayment()` in `Code.gs` is what to change if that's ever needed.
- **Enquiries / EnquiryFollowups** — see Enquiry Management above.
- **Therapists / TherapistLeaveRequests** — see Staff Management above.
- **Dashboard** and **Reports** read live from all sheets — nothing needs
  to be recalculated manually.

## Future modules (not built in V1, per spec)

The data model intentionally keeps `Therapies Taking` as a simple list on
the student record and `Therapy` as a plain text field on fees/payments —
no therapist, session, or enrollment entities exist yet. When you're ready
to add Therapist Management, Therapy Enrollment, Session Attendance,
Session Plans, Progress Notes, or Therapist Reports, those become new
sheets + new `Code.gs` functions + new nav items in `Index.html`, without
needing to touch the Student/Fee/Payment structure above.

## Notes / limitations of this build

- **Branding**: "Credence Kurukshetra" appears at the top of every page
  after login (the sidebar header) and on the login screen itself (as
  text below the logo, since the logo image has "Credence" baked into it
  as a raster wordmark — not something a text edit can extend). Also set
  as the browser tab title and the PWA install prompt's full name. The
  PWA `short_name` and the iOS home-screen title (`apple-mobile-web-app-
  title` meta tag) deliberately stay as plain "Credence" — those labels
  render in a small fixed space below a home-screen icon and get
  truncated or wrap awkwardly if too long, so the full "Credence
  Kurukshetra" name is used everywhere it has room, and the short form
  everywhere it doesn't. If you'd rather have a real "Credence
  Kurukshetra" wordmark baked into the logo images themselves (login
  page and sidebar badge), that needs an actual new image asset, not a
  code change — happy to help if you have one to embed.

- **"Student" vs "Child" terminology**: every user-visible label,
  button, heading, modal title, toast message, and dashboard KPI now
  says "Child" instead of "Student" (Add Child, Child roster, Active
  children, etc.). The underlying data model is deliberately unchanged —
  the `STUDENTS` sheet, its `Student ID`/`Student Name`/`Student Status`
  columns, and every internal function/variable name (`saveStudent`,
  `renderStudents`, `STUDENTS`, `studentId`, and so on) still say
  "Student" throughout `Code.gs` and `Index.html`'s JavaScript. This is
  intentional, not an oversight: renaming the sheet columns or internal
  identifiers would touch dozens of places for zero user-facing benefit,
  while carrying real risk of an incomplete rename breaking something. If
  you ever do want the sheet columns themselves renamed too, that's a
  more involved, riskier change — ask explicitly if you want it.
- **Upgrading from a version with Session Scheduling?** Delete the old
  `SessionScheduling.gs` file from your Apps Script project before
  adding `StaffManagement.gs` — see the callout in step 2 above. Leaving
  both in place causes a duplicate-declaration error at runtime.
- **File structure**: this app is now six code files, not two —
  `Code.gs` (Students/Fees/Payments/Enquiries/shared infrastructure),
  `StaffManagement.gs` (the Therapists directory), `Expenses.gs`,
  `Leaves.gs`, `ToDo.gs`, plus `Index.html`. All six need to exist in the
  Apps Script project — see setup step 2 above.
- **Refresh buttons**: a manual "↻ Refresh" button sits in the top bar
  of Students, Fee Management, Expenses Management, Enquiry Dashboard,
  Reports, Users, and Staff Management — re-fetches that view's (and its
  subnav tabs') data from the sheet without a full page reload.
- **Consolidated modules**: Fee Management, Enquiry Dashboard, and Staff
  Management are each one sidebar item with subnav tabs inside them
  (Fee Records/Payment Entry/Due Tracking; Dashboard/Enquiries/
  Follow-ups; Therapists/Leaves) rather than separate top-level sidebar
  items — this keeps the sidebar shorter as the app has grown.

- The Credence logo is embedded directly in `Index.html` as base64 data
  URIs (the login page uses the full icon + wordmark version; the sidebar
  uses a cropped icon-only version on a small white badge, since the
  sidebar background is dark and the logo has a solid white background in
  the source file). This keeps the app self-contained — no image hosting
  needed — but means replacing the logo later requires re-encoding a new
  image to base64 and swapping the `data:image/jpeg;base64,...` string,
  rather than just replacing a file.

- Passwords are stored in plain text in the USERS sheet for simplicity —
  fine for a small internal team with a private sheet, but don't reuse a
  sensitive password here. Restrict edit access to the Sheet itself to
  keep the USERS tab private.
- A logged-in Admin can't delete their own account from the Users screen
  (prevents accidental lockout) — have a second Admin do it, or edit the
  sheet directly.
