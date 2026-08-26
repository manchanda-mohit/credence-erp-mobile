/**
 * CREDENCE — Mobile app JSON API.
 *
 * Everything in this file is purely additive — it does not change how
 * the existing web app (Index.html, via google.script.run) works at
 * all. google.script.run only works inside an Apps Script-served HTML
 * page, so a native Android app can't call it; this exposes the same
 * underlying functions (unchanged — Code.gs, StaffManagement.gs,
 * Expenses.gs, Leaves.gs, ToDo.gs) over plain HTTPS JSON instead, so the
 * Android app reads/writes the exact same Google Sheets, live, as the
 * web app.
 *
 * Covers sign-in, Dashboard, Students, Fee Management (Records/
 * Payments/Due Tracking), Enquiries, Staff Management (Therapists +
 * Leaves), Expenses, To-Do, Reports, and Users — the full module set
 * the Android app supports as of this version. Every action is still
 * just a `case` line in routeMobileApiAction_ below calling an existing,
 * unchanged function — nothing here duplicates business logic that
 * already lives in Code.gs / StaffManagement.gs / Expenses.gs /
 * Leaves.gs / ToDo.gs.
 *
 * Contract:
 *   Reads  → GET  <exec URL>?api=1&action=<name>&username=<u>&...params
 *   Writes → POST <exec URL>?api=1  with a JSON body:
 *              { "action": "<name>", "username": "<u>", ...params }
 * Every response is JSON: { ok: true, data: <result> } on success, or
 * { ok: false, error: "<message>" } on failure — one consistent shape
 * for the Android app to parse regardless of which action it called.
 *
 * Auth model deliberately mirrors the web app's own (see login() /
 * restoreSession() in Code.gs): a one-time username+password check at
 * login, then every later call just carries the username along — the
 * same trust model google.script.run already uses today, not a weaker
 * one, and every existing role check (requireAdmin_,
 * requireManagerOrAdmin_ inside saveTherapist/deletePayment/
 * updatePayment/etc.) keeps working exactly as it does for the web app,
 * since these actions just call the same functions.
 *
 * The one thing added specifically for this new HTTP surface is
 * requireApiUser_ below: reads like getStudents()/getFees() take no
 * username at all today and aren't gated, which is fine when the only
 * way to reach them is from inside the loaded web app, but this API is
 * a plain public HTTPS endpoint anyone with the URL could otherwise
 * query directly — so every action except "login" now requires that
 * username to belong to a currently Active user.
 */

// ─────────────────────────────────────────────────────────────────────────
// AUTH GUARD
// ─────────────────────────────────────────────────────────────────────────

function requireApiUser_(username) {
  if (!username) throw new Error('Not signed in.');
  const sheet = getSheet_(SHEETS.USERS);
  const users = sheetToObjects_(sheet);
  const match = users.find(function (u) {
    return String(u.Username).toLowerCase() === String(username).toLowerCase() &&
      String(u.Active).toUpperCase() === 'YES';
  });
  if (!match) throw new Error('Session expired or account deactivated — please sign in again.');
  return match;
}

// ─────────────────────────────────────────────────────────────────────────
// ACTION ROUTER — one switch shared by GET (reads) and POST (writes)
// ─────────────────────────────────────────────────────────────────────────

function routeMobileApiAction_(action, params) {
  switch (action) {
    // ── Auth ──
    case 'login':
      return login(params.username, params.password);
    case 'restoreSession':
      return restoreSession(params.username);

    // ── Dashboard ──
    case 'getDashboardStats':
      requireApiUser_(params.username);
      return getDashboardStats();

    // ── Students ──
    case 'getStudents':
      requireApiUser_(params.username);
      return getStudents();
    case 'getTherapyOptions':
      requireApiUser_(params.username);
      return getTherapyOptions();
    case 'saveStudent':
      requireApiUser_(params.username);
      return saveStudent(params.data, params.username);

    // ── Fee Management: Records / Due Tracking ──
    case 'getFees':
      requireApiUser_(params.username);
      return getFees();
    case 'getDueList':
      requireApiUser_(params.username);
      return getDueList();
    case 'getFeeRates':
      requireApiUser_(params.username);
      return getFeeRates();
    case 'saveFee':
      requireApiUser_(params.username);
      return saveFee(params.data, params.username);
    case 'deleteFee':
      requireApiUser_(params.username);
      return deleteFee(params.feeId, params.username);

    // ── Fee Management: Payments ──
    case 'getPayments':
      requireApiUser_(params.username);
      return getPayments();
    case 'recordPayment':
      requireApiUser_(params.username);
      return recordPayment(params.data, params.username);
    case 'updatePayment':
      requireApiUser_(params.username);
      return updatePayment(params.data, params.username);
    case 'deletePayment':
      requireApiUser_(params.username);
      return deletePayment(params.paymentId, params.username);

    // ── Enquiries ── (Enquiries.gs functions live in Code.gs)
    case 'getEnquiryOptions':
      requireApiUser_(params.username);
      return getEnquiryOptions();
    case 'getEnquiries':
      requireApiUser_(params.username);
      return getEnquiries(params.username);
    case 'checkDuplicateContact':
      requireApiUser_(params.username);
      return checkDuplicateContact(params.mobile);
    case 'saveEnquiry':
      requireApiUser_(params.username);
      return saveEnquiry(params.data, params.username);
    case 'addFollowup':
      requireApiUser_(params.username);
      return addFollowup(params.data, params.username);
    case 'getFollowupsByEnquiry':
      requireApiUser_(params.username);
      return getFollowupsByEnquiry(params.enquiryId);
    case 'deleteEnquiry':
      requireApiUser_(params.username);
      return deleteEnquiry(params.enquiryId, params.username, params.reason);
    case 'markEnquiryConverted':
      requireApiUser_(params.username);
      return markEnquiryConverted(params.enquiryId, params.studentId, params.username);
    case 'getEnquiryDashboardStats':
      requireApiUser_(params.username);
      return getEnquiryDashboardStats(params.startDate, params.endDate, params.username);
    case 'getEnquiryMonthlySummary':
      requireApiUser_(params.username);
      return getEnquiryMonthlySummary(params.startYear, params.startMonth, params.endYear, params.endMonth, params.username);

    // ── Staff Management: Therapists ──
    case 'getStaffPageData':
      requireApiUser_(params.username);
      return getStaffPageData(params.username);
    case 'saveTherapist':
      requireApiUser_(params.username);
      return saveTherapist(params.data, params.username);
    case 'deleteTherapist':
      requireApiUser_(params.username);
      return deleteTherapist(params.therapistId, params.reason, params.username);

    // ── Staff Management: Leaves ──
    case 'getLeaveOptions':
      requireApiUser_(params.username);
      return getLeaveOptions();
    case 'getTherapistLeaveRequests':
      requireApiUser_(params.username);
      return getTherapistLeaveRequests();
    case 'applyForTherapistLeave':
      requireApiUser_(params.username);
      return applyForTherapistLeave(params.data, params.username);
    case 'cancelTherapistLeaveRequest':
      requireApiUser_(params.username);
      return cancelTherapistLeaveRequest(params.leaveId, params.username);
    case 'decideTherapistLeave':
      requireApiUser_(params.username);
      return decideTherapistLeave(params.leaveId, params.decision, params.remarks, params.username);
    case 'getLeaveDashboardStats':
      requireApiUser_(params.username);
      return getLeaveDashboardStats();
    case 'getLeaveSummaryByTherapist':
      requireApiUser_(params.username);
      return getLeaveSummaryByTherapist();
    case 'setAccrualRate':
      requireApiUser_(params.username);
      return setAccrualRate(params.rate, params.username);
    case 'grantBonusLeave':
      requireApiUser_(params.username);
      return grantBonusLeave(params.therapistId, params.days, params.reason, params.username);
    case 'getLeaveAdjustments':
      requireApiUser_(params.username);
      return getLeaveAdjustments();
    case 'getLeaveBalances':
      requireApiUser_(params.username);
      return getLeaveBalances();
    case 'getLeaveAccrualLedger':
      requireApiUser_(params.username);
      return getLeaveAccrualLedger(params.therapistId, params.year);

    // ── Expenses ──
    case 'getExpenseOptions':
      requireApiUser_(params.username);
      return getExpenseOptions(params.username);
    case 'getExpenses':
      requireApiUser_(params.username);
      return getExpenses(params.username);
    case 'saveExpense':
      requireApiUser_(params.username);
      return saveExpense(params.data, params.username);
    case 'deleteExpense':
      requireApiUser_(params.username);
      return deleteExpense(params.expenseId, params.reason, params.username);

    // ── To-Do ──
    case 'getToDos':
      requireApiUser_(params.username);
      return getToDos();
    case 'addToDo':
      requireApiUser_(params.username);
      return addToDo(params.title, params.username);
    case 'completeToDo':
      requireApiUser_(params.username);
      return completeToDo(params.taskId, params.username);
    case 'reopenToDo':
      requireApiUser_(params.username);
      return reopenToDo(params.taskId, params.username);
    case 'updateToDoComment':
      requireApiUser_(params.username);
      return updateToDoComment(params.taskId, params.comment, params.username);

    // ── Reports ── (read-only; getMonthlySummary itself requires Admin,
    // same as it does for the web app — MobileApi doesn't loosen that)
    case 'getStaleSessionReport':
      requireApiUser_(params.username);
      return getStaleSessionReport();
    case 'getExpenseCategorySummary':
      requireApiUser_(params.username);
      return getExpenseCategorySummary();
    case 'getMonthlySummary':
      requireApiUser_(params.username);
      return getMonthlySummary(params.startYear, params.startMonth, params.endYear, params.endMonth, params.username);

    // ── Users (Admin-only — enforced inside each function itself) ──
    case 'getUsers':
      requireApiUser_(params.username);
      return getUsers(params.username);
    case 'saveUser':
      requireApiUser_(params.username);
      return saveUser(params.data, params.username);
    case 'deleteUser':
      requireApiUser_(params.username);
      return deleteUser(params.targetUsername, params.username);

    default:
      throw new Error('Unknown API action: ' + action);
  }
}

// ─────────────────────────────────────────────────────────────────────────
// REQUEST / RESPONSE PLUMBING
// ─────────────────────────────────────────────────────────────────────────

function mobileApiResponse_(fn) {
  let result;
  try {
    result = { ok: true, data: fn() };
  } catch (e) {
    result = { ok: false, error: e.message };
  }
  return ContentService.createTextOutput(JSON.stringify(result)).setMimeType(ContentService.MimeType.JSON);
}

// Called from doGet(e) in Code.gs when e.parameter.api === '1'. GET is
// used for every read action — params arrive as plain query-string
// values (all strings; fine, since none of the read actions above take
// non-string params).
function handleMobileApiGet_(e) {
  const params = e && e.parameter ? e.parameter : {};
  return mobileApiResponse_(function () {
    return routeMobileApiAction_(params.action, params);
  });
}

// Called from doPost(e) below when e.parameter.api === '1'. POST is used
// for every write action — the request body is JSON so nested objects
// (e.g. saveFee's "data") survive intact, unlike GET's flat query string.
function handleMobileApiPost_(e) {
  let params = {};
  try {
    params = e && e.postData && e.postData.contents ? JSON.parse(e.postData.contents) : {};
  } catch (parseErr) {
    return ContentService.createTextOutput(JSON.stringify({ ok: false, error: 'Malformed JSON request body.' }))
      .setMimeType(ContentService.MimeType.JSON);
  }
  return mobileApiResponse_(function () {
    return routeMobileApiAction_(params.action, params);
  });
}

// doPost doesn't exist anywhere else in this project — Apps Script only
// allows one per project, so this is it. The web app never POSTs to
// this script (it only ever uses google.script.run and doGet's ?page=
// routes), so this whole entry point is new surface that exists only
// for the mobile API's write actions.
function doPost(e) {
  const isApi = e && e.parameter && e.parameter.api === '1';
  if (isApi) return handleMobileApiPost_(e);
  return ContentService.createTextOutput(JSON.stringify({ ok: false, error: 'Unknown endpoint.' }))
    .setMimeType(ContentService.MimeType.JSON);
}
