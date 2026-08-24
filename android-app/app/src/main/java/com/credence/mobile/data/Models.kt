package com.credence.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response DTOs mirror the exact JSON shape MobileApi.gs returns, which
 * for anything read straight off a Sheet (Student, Fee, Payment,
 * FeeRate) is the sheet's own header text as the JSON key — e.g.
 * "Student ID", with the space — because that's what sheetToObjects_()
 * in Code.gs produces and MobileApi.gs passes straight through
 * unchanged. @SerialName maps each of those to a normal Kotlin property
 * name. Dashboard stats and the logged-in user object are different:
 * those are hand-built JS objects with camelCase keys already (see
 * getDashboardStats() / login() in Code.gs), so no @SerialName is
 * needed there.
 *
 * Every field the server might legitimately send as blank/empty is
 * typed as a String default rather than nullable, since Sheets returns
 * "" for an empty cell, not null/absent — keeps call sites simple
 * (no "?:" everywhere) at the cost of empty-string checks instead of
 * null checks, which matches how Index.html's own JS already treats
 * these same fields.
 */

@Serializable
data class LoginUser(
    val username: String = "",
    val fullName: String = "",
    val role: String = ""
)

@Serializable
data class LoginResult(
    val success: Boolean = false,
    val message: String = "",
    val user: LoginUser? = null
)

@Serializable
data class DashboardStats(
    val lastActivityAt: String = "—",
    val totalEnquiriesMonth: Int = 0,
    val totalActiveStudents: Int = 0,
    val upcomingExitStudents: Int = 0,
    val totalCollectionMonth: Double = 0.0,
    val cashCollectionMonth: Double = 0.0,
    val onlineCollectionMonth: Double = 0.0,
    val therapyCollection: Map<String, Double> = emptyMap(),
    val totalFeeDue: Double = 0.0,
    val dueChildrenCount: Int = 0,
    val newStudents: Int = 0,
    val exitedStudents: Int = 0,
    val totalExpensesMonth: Double = 0.0,
    val netThisMonth: Double = 0.0,
    val noSession40: Int = 0,
    val noSession60: Int = 0,
    val upcomingLeaveCount: Int = 0
)

@Serializable
data class Student(
    @SerialName("Student ID") val studentId: String = "",
    @SerialName("Registration Date") val registrationDate: String = "",
    @SerialName("Student Name") val studentName: String = "",
    @SerialName("Date of Birth") val dateOfBirth: String = "",
    @SerialName("Age") val age: String = "",
    @SerialName("Gender") val gender: String = "",
    @SerialName("Father Name") val fatherName: String = "",
    @SerialName("Mother Name") val motherName: String = "",
    @SerialName("Parent/Guardian Name") val guardianName: String = "",
    @SerialName("Parent Mobile") val parentMobile: String = "",
    @SerialName("Alternate Mobile") val altMobile: String = "",
    @SerialName("Parent Email") val parentEmail: String = "",
    @SerialName("Parents Occupation") val parentsOccupation: String = "",
    @SerialName("Address") val address: String = "",
    @SerialName("City") val city: String = "",
    @SerialName("Joining Date") val joiningDate: String = "",
    @SerialName("Exit Date") val exitDate: String = "",
    @SerialName("Student Status") val status: String = "",
    @SerialName("Therapies Taking") val therapiesTaking: String = "",
    @SerialName("Notes") val notes: String = ""
)

@Serializable
data class TherapyOption(
    val code: String = "",
    val label: String = ""
)

@Serializable
data class Fee(
    @SerialName("Fee ID") val feeId: String = "",
    @SerialName("Student ID") val studentId: String = "",
    @SerialName("Student Name") val studentName: String = "",
    @SerialName("Therapy") val therapy: String = "",
    @SerialName("Session Type") val sessionType: String = "",
    @SerialName("Billing Month") val billingMonth: String = "",
    @SerialName("Billing Year") val billingYear: String = "",
    @SerialName("Fee Amount") val feeAmount: Double = 0.0,
    @SerialName("Discount") val discount: Double = 0.0,
    @SerialName("Net Amount") val netAmount: Double = 0.0,
    @SerialName("Amount Paid") val amountPaid: Double = 0.0,
    @SerialName("Balance Due") val balanceDue: Double = 0.0,
    @SerialName("Payment Status") val paymentStatus: String = "",
    @SerialName("Session Start Date") val sessionStartDate: String = ""
)

@Serializable
data class Payment(
    @SerialName("Payment ID") val paymentId: String = "",
    @SerialName("Fee ID") val feeId: String = "",
    @SerialName("Student ID") val studentId: String = "",
    @SerialName("Student Name") val studentName: String = "",
    @SerialName("Therapy") val therapy: String = "",
    @SerialName("Billing Month") val billingMonth: String = "",
    @SerialName("Billing Year") val billingYear: String = "",
    @SerialName("Amount Received") val amountReceived: Double = 0.0,
    @SerialName("Discount Given") val discountGiven: Double = 0.0,
    @SerialName("Payment Mode") val paymentMode: String = "",
    @SerialName("Payment Date") val paymentDate: String = "",
    @SerialName("Receipt Number") val receiptNumber: String = "",
    @SerialName("Remarks") val remarks: String = ""
)

@Serializable
data class FeeRate(
    @SerialName("Therapy") val therapy: String = "",
    @SerialName("Session Type") val sessionType: String = "",
    @SerialName("Fee Amount") val feeAmount: String = ""
)

/**
 * Request (write) payloads. These deliberately use the same camelCase
 * field names Index.html's own JS already sends inside its "data"
 * objects to saveStudent/saveFee/recordPayment/updatePayment — those
 * functions read data.name, data.studentId, etc., not sheet header
 * text, so no @SerialName mapping is needed here (unlike the response
 * DTOs above).
 */

@Serializable
data class StudentInput(
    val studentId: String? = null,
    val name: String,
    val dob: String = "",
    val gender: String = "",
    val fatherName: String = "",
    val motherName: String = "",
    val guardianName: String = "",
    val parentMobile: String = "",
    val altMobile: String = "",
    val parentEmail: String = "",
    val parentsOccupation: String = "",
    val address: String = "",
    val city: String = "",
    val joiningDate: String = "",
    val exitDate: String = "",
    val status: String = "Active",
    val therapies: List<String> = emptyList(),
    val notes: String = ""
)

@Serializable
data class FeeInput(
    val feeId: String? = null,
    val studentId: String,
    val studentName: String,
    val therapy: String,
    val sessionType: String,
    val sessionStartDate: String,
    val feeAmount: Double = 0.0,
    val discount: Double = 0.0,
    val amountPaid: Double = 0.0
)

@Serializable
data class PaymentInput(
    val feeId: String,
    val studentId: String,
    val studentName: String,
    val therapy: String,
    val billingMonth: String,
    val billingYear: String,
    val amountReceived: Double,
    val discount: Double = 0.0,
    val paymentMode: String,
    val paymentDate: String = "",
    val receiptNumber: String = "",
    val remarks: String = ""
)

@Serializable
data class PaymentEditInput(
    val paymentId: String,
    val amountReceived: Double,
    val discount: Double = 0.0,
    val paymentMode: String,
    val paymentDate: String = "",
    val receiptNumber: String = "",
    val remarks: String = ""
)

/** Generic "a write succeeded" response — saveStudent/saveFee/
 * updatePayment/deleteFee/deletePayment all return some variation of
 * { success, message, ... }; only these two fields are common to all of
 * them and are all this app actually needs to show a result toast. */
@Serializable
data class WriteResult(
    val success: Boolean = false,
    val message: String = ""
)

/** recordPayment's response carries more than the generic WriteResult
 * (see recordPayment() in Code.gs) — the receipt number and updated
 * balance/status are worth surfacing the same way Index.html's
 * submitPayment() shows them in its toast. Unknown-key decoding is
 * lenient elsewhere in this app, so adding this alongside WriteResult
 * (rather than changing it) is purely additive. */
@Serializable
data class PaymentRecordResult(
    val success: Boolean = false,
    val message: String = "",
    val paymentId: String = "",
    val receiptNumber: String = "",
    val newBalance: Double = 0.0,
    val newStatus: String = ""
)

/** saveStudent()'s response also carries the new/updated Student ID
 * (see saveStudent() in Code.gs) — same "decode the same action's
 * response into a richer DTO" pattern as PaymentRecordResult above.
 * Needed specifically for Convert Enquiry → Student, which must pass
 * the freshly created student's ID on to markEnquiryConverted(). */
@Serializable
data class StudentSaveResult(
    val success: Boolean = false,
    val message: String = "",
    val studentId: String = ""
)

// ─────────────────────────────────────────────────────────────────────────
// ENQUIRIES (Code.gs) — response DTOs read raw sheet rows (@SerialName to
// header text) same as Student/Fee/Payment above; getEnquiryOptions() and
// checkDuplicateContact() are hand-built camelCase JS objects, so those
// need no @SerialName.
// ─────────────────────────────────────────────────────────────────────────

@Serializable
data class EnquiryOptions(
    val services: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val lostReasons: List<String> = emptyList(),
    val contactModes: List<String> = emptyList()
)

@Serializable
data class Enquiry(
    @SerialName("Enquiry ID") val enquiryId: String = "",
    @SerialName("Enquiry Date") val enquiryDate: String = "",
    @SerialName("Child Name") val childName: String = "",
    @SerialName("Parent/Guardian Name") val parentName: String = "",
    @SerialName("Mobile Number") val mobile: String = "",
    @SerialName("Age") val age: String = "",
    @SerialName("City/Area") val city: String = "",
    @SerialName("Enquiry For") val enquiryFor: String = "",
    @SerialName("Source") val source: String = "",
    @SerialName("Source Detail") val sourceDetail: String = "",
    @SerialName("Status") val status: String = "",
    @SerialName("Assigned To") val assignedTo: String = "",
    @SerialName("Last Follow-up Date") val lastFollowUpDate: String = "",
    @SerialName("Next Follow-up Date") val nextFollowUpDate: String = "",
    @SerialName("Remarks") val remarks: String = "",
    @SerialName("Lost Reason") val lostReason: String = "",
    @SerialName("Converted Date") val convertedDate: String = "",
    @SerialName("Student ID") val studentId: String = "",
    // Added by getEnquiries() itself (e.Priority = getEnquiryPriority_(e)),
    // not a sheet column — still plain "Priority" in the JSON either way.
    @SerialName("Priority") val priority: String = ""
)

@Serializable
data class EnquiryFollowup(
    @SerialName("Follow-up ID") val followUpId: String = "",
    @SerialName("Enquiry ID") val enquiryId: String = "",
    @SerialName("Follow-up Date") val followUpDate: String = "",
    @SerialName("Contact Mode") val contactMode: String = "",
    @SerialName("Remarks") val remarks: String = "",
    @SerialName("Next Follow-up Date") val nextFollowUpDate: String = "",
    @SerialName("Status") val status: String = "",
    @SerialName("Followed Up By") val followedUpBy: String = ""
)

/** checkDuplicateContact()'s two possible matches use different field
 * names (enquiryId/childName vs studentId/studentName) but share
 * "status" — one flexible DTO covers both since ignoreUnknownKeys/
 * missing-key-defaults make decoding either shape into it safe. */
@Serializable
data class DuplicateContactMatch(
    val enquiryId: String = "",
    val studentId: String = "",
    val childName: String = "",
    val studentName: String = "",
    val status: String = ""
)

@Serializable
data class DuplicateContactCheck(
    val enquiry: DuplicateContactMatch? = null,
    val student: DuplicateContactMatch? = null
)

@Serializable
data class EnquiryInput(
    val enquiryId: String? = null,
    val childName: String = "",
    val parentName: String = "",
    val mobile: String,
    val age: String = "",
    val city: String = "",
    val enquiryFor: List<String> = emptyList(),
    val source: String = "",
    val sourceDetail: String = "",
    val assignedTo: String = "",
    val remarks: String = "",
    val nextFollowUpDate: String = ""
)

@Serializable
data class FollowupInput(
    val enquiryId: String,
    val followUpDate: String = "",
    val contactMode: String = "",
    val remarks: String = "",
    val nextFollowUpDate: String = "",
    val status: String = "",
    val lostReason: String = ""
)

/** getEnquiryDashboardStats() — hand-built camelCase JS object
 * (Code.gs). The web version also carries an Admin-only sourceSummary/
 * serviceSummary/staffPerformance breakdown and a followUpActionTable;
 * this mobile "mini" dashboard only surfaces the top-line numbers, and
 * ignoreUnknownKeys (see ApiClient's Json config) means simply not
 * declaring those fields here is enough to skip them safely. */
@Serializable
data class EnquiryDashboardStats(
    val totalEnquiries: Int = 0,
    val newEnquiries: Int = 0,
    val followUpsDueToday: Int = 0,
    val overdueFollowUps: Int = 0,
    val converted: Int = 0,
    val conversionRate: Double = 0.0,
    val lost: Int = 0
)

// ─────────────────────────────────────────────────────────────────────────
// STAFF MANAGEMENT — Therapists (StaffManagement.gs)
// ─────────────────────────────────────────────────────────────────────────

@Serializable
data class Therapist(
    @SerialName("Therapist ID") val therapistId: String = "",
    @SerialName("Therapist Name") val name: String = "",
    @SerialName("Therapy/Service") val service: String = "",
    @SerialName("Mobile") val mobile: String = "",
    @SerialName("Status") val status: String = "",
    @SerialName("Notes") val notes: String = "",
    @SerialName("Shift") val shift: String = "",
    // Sheet-native Number, redacted to "" server-side for non-Manager-tier
    // viewers (see getStaffPageData() in StaffManagement.gs) — String here
    // the same way FeeRate.feeAmount above is, so either a number or an
    // empty string decodes cleanly.
    @SerialName("Monthly Salary") val monthlySalary: String = "",
    @SerialName("Joining Date") val joiningDate: String = ""
)

@Serializable
data class StaffPageData(
    val services: List<String> = emptyList(),
    val therapists: List<Therapist> = emptyList()
)

@Serializable
data class TherapistInput(
    val therapistId: String? = null,
    val name: String,
    val service: String,
    val mobile: String = "",
    val status: String = "Active",
    val notes: String = "",
    val shift: String = "",
    val salary: String = "",
    val joiningDate: String = ""
)

// ─────────────────────────────────────────────────────────────────────────
// STAFF MANAGEMENT — Therapist Leaves (Leaves.gs)
// ─────────────────────────────────────────────────────────────────────────

@Serializable
data class LeaveTherapistOption(
    val therapistId: String = "",
    val therapistName: String = "",
    val therapy: String = ""
)

@Serializable
data class LeaveOptions(
    val leaveTypes: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val paidStatusOptions: List<String> = emptyList(),
    val therapists: List<LeaveTherapistOption> = emptyList(),
    val accrualRate: Double = 0.0
)

@Serializable
data class LeaveRequest(
    @SerialName("Leave ID") val leaveId: String = "",
    @SerialName("Therapist ID") val therapistId: String = "",
    @SerialName("Therapist Name") val therapistName: String = "",
    @SerialName("Therapy") val therapy: String = "",
    @SerialName("Leave Type") val leaveType: String = "",
    @SerialName("Start Date") val startDate: String = "",
    @SerialName("End Date") val endDate: String = "",
    @SerialName("Number Of Days") val numberOfDays: String = "",
    @SerialName("Reason") val reason: String = "",
    @SerialName("Status") val status: String = "",
    @SerialName("Approved By") val approvedBy: String = "",
    @SerialName("Remarks") val remarks: String = "",
    @SerialName("Created By") val createdBy: String = "",
    @SerialName("Paid Or Unpaid") val paidOrUnpaid: String = ""
)

@Serializable
data class LeaveApplicationInput(
    val therapistId: String,
    val leaveType: String,
    val startDate: String,
    val endDate: String,
    val paidStatus: String,
    val reason: String = "",
    val remarks: String = ""
)

/** getLeaveDashboardStats() — hand-built camelCase JS object (Leaves.gs),
 * same "leave stat grid" numbers the web Leaves tab shows at the top of
 * its own subnav view. */
@Serializable
data class LeaveDashboardStats(
    val totalTherapists: Int = 0,
    val onLeaveToday: Int = 0,
    val onLeaveTodayNames: List<String> = emptyList(),
    val pendingCount: Int = 0,
    val approvedThisMonth: Int = 0,
    val leaveDaysThisMonth: Int = 0
)

/** getLeaveSummaryByTherapist() — hand-built camelCase JS object
 * (Leaves.gs); byType keys are whichever of LEAVE_TYPES that therapist
 * actually used this year, so a plain Map covers it without needing to
 * know the key set up front. */
@Serializable
data class LeaveSummaryByTherapist(
    val therapistId: String = "",
    val therapistName: String = "",
    val therapy: String = "",
    val totalDays: Int = 0,
    val paidDays: Int = 0,
    val unpaidDays: Int = 0,
    val byType: Map<String, Int> = emptyMap()
)

/** getLeaveBalances() — hand-built camelCase JS object (Leaves.gs).
 * accrued/bonusDays/used/remaining are computed numbers (accrual rate
 * can be a fraction, e.g. 1.5/month), so Double rather than the
 * lenient-string-numeric convention used for raw sheet cells. */
@Serializable
data class LeaveBalance(
    val therapistId: String = "",
    val therapistName: String = "",
    val therapy: String = "",
    val accrued: Double = 0.0,
    val bonusDays: Double = 0.0,
    val used: Double = 0.0,
    val remaining: Double = 0.0
)

/** getLeaveAdjustments() — raw LeaveAdjustments sheet rows via
 * sheetToObjects_(), so @SerialName maps to the sheet's own header text
 * same as every other raw-row DTO in this file. */
@Serializable
data class LeaveAdjustment(
    @SerialName("Adjustment ID") val adjustmentId: String = "",
    @SerialName("Therapist ID") val therapistId: String = "",
    @SerialName("Therapist Name") val therapistName: String = "",
    @SerialName("Days") val days: Double = 0.0,
    @SerialName("Reason") val reason: String = "",
    @SerialName("Granted By") val grantedBy: String = "",
    @SerialName("Granted Date") val grantedDate: String = ""
)

// ─────────────────────────────────────────────────────────────────────────
// EXPENSES (Expenses.gs)
// ─────────────────────────────────────────────────────────────────────────

@Serializable
data class ExpenseOptions(
    val categories: List<String> = emptyList(),
    val paymentModes: List<String> = emptyList()
)

@Serializable
data class Expense(
    @SerialName("Expense ID") val expenseId: String = "",
    @SerialName("Date") val date: String = "",
    @SerialName("Category") val category: String = "",
    @SerialName("Description") val description: String = "",
    @SerialName("Amount") val amount: Double = 0.0,
    @SerialName("Payment Mode") val paymentMode: String = "",
    @SerialName("Paid To") val paidTo: String = "",
    @SerialName("Reference Number") val referenceNumber: String = "",
    @SerialName("Remarks") val remarks: String = ""
)

@Serializable
data class ExpenseInput(
    val expenseId: String? = null,
    val date: String,
    val category: String,
    val description: String = "",
    val amount: Double,
    val paymentMode: String = "",
    val paidTo: String = "",
    val referenceNumber: String = "",
    val remarks: String = ""
)

// ─────────────────────────────────────────────────────────────────────────
// TO-DO (ToDo.gs)
// ─────────────────────────────────────────────────────────────────────────

@Serializable
data class ToDoItem(
    @SerialName("Task ID") val taskId: String = "",
    @SerialName("Title") val title: String = "",
    @SerialName("Status") val status: String = "",
    @SerialName("Created By") val createdBy: String = "",
    @SerialName("Created Date") val createdDate: String = "",
    @SerialName("Completed By") val completedBy: String = "",
    @SerialName("Completed Date") val completedDate: String = "",
    @SerialName("Comment") val comment: String = "",
    @SerialName("Comment By") val commentBy: String = "",
    @SerialName("Comment Date") val commentDate: String = ""
)

// ─────────────────────────────────────────────────────────────────────────
// REPORTS — getStaleSessionReport()/getExpenseCategorySummary() are
// hand-built camelCase JS objects (Code.gs / Expenses.gs); getMonthlySummary()
// rows are too.
// ─────────────────────────────────────────────────────────────────────────

@Serializable
data class StaleSessionEntry(
    val studentId: String = "",
    val studentName: String = "",
    val parentName: String = "",
    val parentMobile: String = "",
    val therapies: String = "",
    val joiningDate: String = "",
    val lastSessionDate: String = "",
    val daysSinceLastSession: Int? = null
)

@Serializable
data class CategoryBreakdown(
    val count: Int = 0,
    val total: Double = 0.0
)

@Serializable
data class ExpenseCategorySummary(
    val monthLabel: String = "",
    val byCategory: Map<String, CategoryBreakdown> = emptyMap(),
    val total: Double = 0.0,
    val salaryTotal: Double = 0.0,
    val nonSalaryTotal: Double = 0.0
)

@Serializable
data class MonthlySummaryRow(
    val month: String = "",
    val totalCollection: Double = 0.0,
    val cashCollection: Double = 0.0,
    val onlineCollection: Double = 0.0,
    val feeDue: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val net: Double = 0.0,
    val newStudents: Int = 0,
    val exitedStudents: Int = 0
)

// ─────────────────────────────────────────────────────────────────────────
// USERS (Code.gs) — getUsers() is a hand-built object with literal keys
// "Username"/"Full Name"/"Role"/"Active" (see getUsers() in Code.gs).
// ─────────────────────────────────────────────────────────────────────────

@Serializable
data class AppUser(
    @SerialName("Username") val username: String = "",
    @SerialName("Full Name") val fullName: String = "",
    @SerialName("Role") val role: String = "",
    @SerialName("Active") val active: String = ""
)

@Serializable
data class UserInput(
    val username: String,
    val password: String,
    val fullName: String = "",
    val role: String = "Coordinator"
)
