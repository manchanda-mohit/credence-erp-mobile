/**
 * CREDENCE — Therapist Leave Management module.
 *
 * Tracks leave for THERAPISTS (from the Therapists sheet in Staff
 * Management), not app user accounts — therapists typically don't log
 * into this app themselves, so every request is filed on their behalf by
 * whichever logged-in staff member (Admin, Manager, or Coordinator) is
 * recording it. A straightforward HR-style workflow: apply → approve/
 * reject → history. Lives alongside the Therapists directory in the
 * Staff Management sidebar item.
 *
 * Shares the global scope with Code.gs / StaffManagement.gs (Apps
 * Script merges every .gs file in a project into one runtime) —
 * SHEETS, HEADERS, getSheet_, sheetToObjects_, findRowIndexById_,
 * nextId_, formatDate_, requireManagerOrAdmin_, getUserRole_, ROLES,
 * auditStaff_, and getTherapists are defined elsewhere and used freely here.
 */

// ─────────────────────────────────────────────────────────────────────────
// CONFIG
// ─────────────────────────────────────────────────────────────────────────

const LEAVE_TYPES = ['Sick Leave', 'Casual Leave', 'Earned Leave', 'Unpaid Leave', 'Emergency Leave', 'Other'];
const LEAVE_STATUSES = ['Pending', 'Approved', 'Rejected', 'Cancelled'];
// Independent of Leave Type (which is about the reason) — this tracks
// which bucket the request draws from for pay purposes. Every therapist
// accrues 1 paid leave per calendar month, resetting every January (12
// paid days/year, accrued as the months pass — see getAccruedPaidLeaves_).
const PAID_STATUS_OPTIONS = ['Paid', 'Unpaid'];

function getLeaveOptions() {
  const therapists = getTherapists()
    .filter(function (t) { return String(t.Status).toLowerCase() === 'active'; })
    .map(function (t) { return { therapistId: t['Therapist ID'], therapistName: t['Therapist Name'], therapy: t['Therapy/Service'] }; });
  return { leaveTypes: LEAVE_TYPES, statuses: LEAVE_STATUSES, paidStatusOptions: PAID_STATUS_OPTIONS,
    therapists: therapists, accrualRate: getAccrualRate() };
}

function countLeaveDays_(startDate, endDate) {
  const s = new Date(startDate + 'T12:00:00');
  const e = new Date(endDate + 'T12:00:00');
  const days = Math.round((e - s) / (24 * 60 * 60 * 1000)) + 1;
  return days > 0 ? days : 0;
}

function todayDateStr_() {
  return formatDate_(new Date());
}
function addDaysToDateStr_(dateStr, days) {
  const d = new Date(dateStr + 'T12:00:00');
  d.setDate(d.getDate() + days);
  return formatDate_(d);
}

// ─────────────────────────────────────────────────────────────────────────
// CONSOLE — visible to every logged-in role, with full history
// ─────────────────────────────────────────────────────────────────────────

// Deliberately NOT scoped by role or by requesting user — the leave
// console is meant to be visible to everyone, unlike Enquiries which
// Coordinators only see their own slice of.
function getTherapistLeaveRequests() {
  return sheetToObjects_(getSheet_(SHEETS.THERAPIST_LEAVE_REQUESTS)).map(function (l) {
    l['Start Date'] = formatDate_(l['Start Date']);
    l['End Date'] = formatDate_(l['End Date']);
    l['Applied Date'] = formatDate_(l['Applied Date']);
    l['Approved Date'] = formatDate_(l['Approved Date']);
    l['Created Date'] = formatDate_(l['Created Date']);
    l['Last Updated'] = formatDate_(l['Last Updated']);
    return l;
  }).sort(function (a, b) { return b['Start Date'] < a['Start Date'] ? -1 : 1; });
}

// ─────────────────────────────────────────────────────────────────────────
// APPLY / CANCEL — any logged-in staff member files on a therapist's behalf
// ─────────────────────────────────────────────────────────────────────────

function applyForTherapistLeave(data, currentUser) {
  if (!data.therapistId || !data.leaveType || !data.startDate || !data.endDate) {
    throw new Error('Therapist, leave type, start date, and end date are required.');
  }
  if (data.endDate < data.startDate) throw new Error('End date cannot be before start date.');
  if (PAID_STATUS_OPTIONS.indexOf(data.paidStatus) === -1) {
    throw new Error('Select whether this leave is Paid or Unpaid.');
  }

  const therapist = getTherapists().find(function (t) { return t['Therapist ID'] === data.therapistId; });
  if (!therapist) throw new Error('Therapist not found: ' + data.therapistId);

  const sheet = getSheet_(SHEETS.THERAPIST_LEAVE_REQUESTS);
  const now = new Date();
  const leaveId = nextId_(sheet, 'Leave ID', 'TLR-');
  const numDays = countLeaveDays_(data.startDate, data.endDate);
  sheet.appendRow([leaveId, data.therapistId, therapist['Therapist Name'], therapist['Therapy/Service'],
    data.leaveType, data.startDate, data.endDate, numDays, data.reason || '', 'Pending',
    now, '', '', data.remarks || '', currentUser || '', now, now, currentUser || '', data.paidStatus]);
  auditStaff_(currentUser, 'Therapist Leave Requested', 'TherapistLeaveRequest', leaveId,
    therapist['Therapist Name'] + ' — ' + data.leaveType + ' (' + numDays + ' day(s), ' + data.paidStatus + ')');
  return { success: true, leaveId: leaveId, message: 'Leave request submitted for ' + therapist['Therapist Name'] + '.' };
}

// Whoever filed the request can cancel it while it's still Pending;
// Manager/Admin can cancel any request at any time.
function cancelTherapistLeaveRequest(leaveId, currentUser) {
  const sheet = getSheet_(SHEETS.THERAPIST_LEAVE_REQUESTS);
  const rowIdx = findRowIndexById_(sheet, 'Leave ID', leaveId);
  if (rowIdx === -1) throw new Error('Leave request not found: ' + leaveId);
  const headers = HEADERS.THERAPIST_LEAVE_REQUESTS;
  const row = sheet.getRange(rowIdx, 1, 1, headers.length).getValues()[0];
  const filedBy = row[headers.indexOf('Created By')];
  const status = row[headers.indexOf('Status')];

  const role = getUserRole_(currentUser).toLowerCase();
  const isManagerOrAdmin = role === ROLES.ADMIN.toLowerCase() || isManagerTierRole_(role);
  if (filedBy !== currentUser && !isManagerOrAdmin) {
    throw new Error('You can only cancel leave requests you filed yourself.');
  }
  if (status !== 'Pending' && !isManagerOrAdmin) {
    throw new Error('Only a pending request can be cancelled — ask a Manager or Admin for an already-decided one.');
  }

  sheet.getRange(rowIdx, headers.indexOf('Status') + 1).setValue('Cancelled');
  sheet.getRange(rowIdx, headers.indexOf('Last Updated') + 1).setValue(new Date());
  sheet.getRange(rowIdx, headers.indexOf('Updated By') + 1).setValue(currentUser || '');
  auditStaff_(currentUser, 'Therapist Leave Cancelled', 'TherapistLeaveRequest', leaveId, '');
  return { success: true, message: 'Leave request cancelled.' };
}

// ─────────────────────────────────────────────────────────────────────────
// APPROVAL — Manager or Admin only
// ─────────────────────────────────────────────────────────────────────────

function decideTherapistLeave(leaveId, decision, remarks, currentUser) {
  requireManagerOrAdmin_(currentUser);
  if (decision !== 'Approved' && decision !== 'Rejected') throw new Error('Decision must be Approved or Rejected.');
  const sheet = getSheet_(SHEETS.THERAPIST_LEAVE_REQUESTS);
  const rowIdx = findRowIndexById_(sheet, 'Leave ID', leaveId);
  if (rowIdx === -1) throw new Error('Leave request not found: ' + leaveId);
  const headers = HEADERS.THERAPIST_LEAVE_REQUESTS;
  const now = new Date();
  sheet.getRange(rowIdx, headers.indexOf('Status') + 1).setValue(decision);
  sheet.getRange(rowIdx, headers.indexOf('Approved By') + 1).setValue(currentUser || '');
  sheet.getRange(rowIdx, headers.indexOf('Approved Date') + 1).setValue(now);
  if (remarks) sheet.getRange(rowIdx, headers.indexOf('Remarks') + 1).setValue(remarks);
  sheet.getRange(rowIdx, headers.indexOf('Last Updated') + 1).setValue(now);
  sheet.getRange(rowIdx, headers.indexOf('Updated By') + 1).setValue(currentUser || '');
  auditStaff_(currentUser, 'Therapist Leave ' + decision, 'TherapistLeaveRequest', leaveId, remarks || '');
  return { success: true, message: 'Leave request ' + decision.toLowerCase() + '.' };
}

// ─────────────────────────────────────────────────────────────────────────
// DASHBOARD
// ─────────────────────────────────────────────────────────────────────────

function getLeaveDashboardStats() {
  const today = todayDateStr_();
  const now = new Date();
  const allLeave = getTherapistLeaveRequests();
  const therapistCount = getTherapists().filter(function (t) { return String(t.Status).toLowerCase() === 'active'; }).length;

  const onLeaveToday = allLeave.filter(function (l) {
    return l.Status === 'Approved' && l['Start Date'] <= today && l['End Date'] >= today;
  });
  const pending = allLeave.filter(function (l) { return l.Status === 'Pending'; });
  const approvedThisMonth = allLeave.filter(function (l) {
    if (l.Status !== 'Approved' || !l['Approved Date']) return false;
    const d = new Date(l['Approved Date']);
    return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
  });
  const leaveDaysThisMonth = allLeave
    .filter(function (l) {
      if (l.Status !== 'Approved') return false;
      const d = new Date(l['Start Date']);
      return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
    })
    .reduce(function (sum, l) { return sum + (Number(l['Number Of Days']) || 0); }, 0);

  return {
    totalTherapists: therapistCount,
    onLeaveToday: onLeaveToday.length,
    onLeaveTodayNames: onLeaveToday.map(function (l) { return l['Therapist Name']; }),
    pendingCount: pending.length,
    approvedThisMonth: approvedThisMonth.length,
    leaveDaysThisMonth: leaveDaysThisMonth
  };
}

// Per-therapist leave-type breakdown for the current calendar year.
function getLeaveSummaryByTherapist() {
  const now = new Date();
  const year = now.getFullYear();
  const approved = getTherapistLeaveRequests().filter(function (l) {
    return l.Status === 'Approved' && new Date(l['Start Date']).getFullYear() === year;
  });
  const byTherapist = {};
  approved.forEach(function (l) {
    const key = l['Therapist ID'];
    if (!byTherapist[key]) {
      byTherapist[key] = { therapistId: l['Therapist ID'], therapistName: l['Therapist Name'], therapy: l['Therapy'],
        totalDays: 0, paidDays: 0, unpaidDays: 0, byType: {} };
    }
    const days = Number(l['Number Of Days']) || 0;
    byTherapist[key].totalDays += days;
    if (l['Paid Or Unpaid'] === 'Paid') byTherapist[key].paidDays += days; else byTherapist[key].unpaidDays += days;
    byTherapist[key].byType[l['Leave Type']] = (byTherapist[key].byType[l['Leave Type']] || 0) + days;
  });
  return Object.keys(byTherapist).map(function (k) { return byTherapist[k]; }).sort(function (a, b) { return b.totalDays - a.totalDays; });
}

// ─────────────────────────────────────────────────────────────────────────
// PAID LEAVE ACCRUAL
// Every active therapist accrues 1 paid leave per calendar month,
// resetting every January — 12 paid days/year, but not all available at
// once: by March, only 3 have accrued, not 12. Balance = accrued minus
// Paid days actually taken (Approved, this year) — Unpaid leave never
// touches this balance.
// ─────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────
// ACCRUAL RATE — configurable by Manager/Admin, stored as a Script
// Property so it doesn't need its own sheet for a single number. Default
// matches the original spec (1 paid leave per month, 12/year) until
// someone changes it.
// ─────────────────────────────────────────────────────────────────────────

function getAccrualRate() {
  const stored = PropertiesService.getScriptProperties().getProperty('LEAVE_ACCRUAL_RATE_PER_MONTH');
  const rate = Number(stored);
  return (stored && rate > 0) ? rate : 1;
}

function setAccrualRate(rate, currentUser) {
  requireManagerOrAdmin_(currentUser);
  const n = Number(rate);
  if (!(n > 0)) throw new Error('Accrual rate must be a positive number.');
  PropertiesService.getScriptProperties().setProperty('LEAVE_ACCRUAL_RATE_PER_MONTH', String(n));
  auditStaff_(currentUser, 'Leave Accrual Rate Changed', 'LeaveSettings', '', 'New rate: ' + n + ' paid leave(s)/month');
  return { success: true, message: 'Accrual rate updated to ' + n + ' paid leave(s) per month.' };
}

function getAccruedPaidLeaves_(asOfDate) {
  const d = asOfDate || new Date();
  const monthsElapsed = d.getMonth() + 1; // January = month 1, December = month 12
  return monthsElapsed * getAccrualRate();
}

// ─────────────────────────────────────────────────────────────────────────
// BONUS / AD-HOC LEAVE GRANTS — Manager/Admin only. Adds extra paid days
// on top of the standard monthly accrual for one specific therapist
// (e.g. covering someone else's shift, a goodwill grant). Every grant is
// visible to everyone via getLeaveAdjustments(), same transparency
// pattern as the rest of this module.
// ─────────────────────────────────────────────────────────────────────────

function grantBonusLeave(therapistId, days, reason, currentUser) {
  requireManagerOrAdmin_(currentUser);
  const n = Number(days);
  if (!(n > 0)) throw new Error('Enter a positive number of days to grant.');
  const therapist = getTherapists().find(function (t) { return t['Therapist ID'] === therapistId; });
  if (!therapist) throw new Error('Therapist not found: ' + therapistId);

  const sheet = getSheet_(SHEETS.LEAVE_ADJUSTMENTS);
  const now = new Date();
  const adjustmentId = nextId_(sheet, 'Adjustment ID', 'LA-');
  sheet.appendRow([adjustmentId, therapistId, therapist['Therapist Name'], n, reason || '', currentUser || '', now]);
  auditStaff_(currentUser, 'Bonus Leave Granted', 'LeaveAdjustment', adjustmentId,
    therapist['Therapist Name'] + ' — +' + n + ' day(s)' + (reason ? ' (' + reason + ')' : ''));
  return { success: true, message: n + ' extra paid leave day(s) granted to ' + therapist['Therapist Name'] + '.' };
}

function getLeaveAdjustments() {
  return sheetToObjects_(getSheet_(SHEETS.LEAVE_ADJUSTMENTS)).map(function (a) {
    a['Granted Date'] = formatDate_(a['Granted Date']);
    return a;
  }).sort(function (a, b) { return b['Granted Date'] < a['Granted Date'] ? -1 : 1; });
}

function getLeaveBalances() {
  const now = new Date();
  const year = now.getFullYear();
  const baseAccrued = getAccruedPaidLeaves_(now);
  const therapists = getTherapists().filter(function (t) { return String(t.Status).toLowerCase() === 'active'; });
  const paidApproved = getTherapistLeaveRequests().filter(function (l) {
    return l.Status === 'Approved' && l['Paid Or Unpaid'] === 'Paid' && new Date(l['Start Date']).getFullYear() === year;
  });
  const adjustments = getLeaveAdjustments().filter(function (a) {
    return a['Granted Date'] && new Date(a['Granted Date']).getFullYear() === year;
  });
  return therapists.map(function (t) {
    const used = paidApproved
      .filter(function (l) { return l['Therapist ID'] === t['Therapist ID']; })
      .reduce(function (sum, l) { return sum + (Number(l['Number Of Days']) || 0); }, 0);
    const bonus = adjustments
      .filter(function (a) { return a['Therapist ID'] === t['Therapist ID']; })
      .reduce(function (sum, a) { return sum + (Number(a['Days']) || 0); }, 0);
    const accrued = baseAccrued + bonus;
    return {
      therapistId: t['Therapist ID'], therapistName: t['Therapist Name'], therapy: t['Therapy/Service'],
      accrued: accrued, bonusDays: bonus, used: used, remaining: accrued - used
    };
  });
}

// ─────────────────────────────────────────────────────────────────────────
// Feeds the main Dashboard's "Upcoming Leaves" tile, shown to every role —
// approved leave that's either happening today or starts within the next
// 7 days.
// ─────────────────────────────────────────────────────────────────────────

function getUpcomingLeaveSummary() {
  const today = todayDateStr_();
  const soon = addDaysToDateStr_(today, 7);
  const upcoming = getTherapistLeaveRequests().filter(function (l) {
    return l.Status === 'Approved' && l['End Date'] >= today && l['Start Date'] <= soon;
  });
  return {
    count: upcoming.length,
    entries: upcoming.map(function (l) {
      return { therapistName: l['Therapist Name'], startDate: l['Start Date'], endDate: l['End Date'] };
    })
  };
}
