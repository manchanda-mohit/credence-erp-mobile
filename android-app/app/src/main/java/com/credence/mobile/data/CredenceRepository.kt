package com.credence.mobile.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * One typed function per MobileApi.gs action — everything below just
 * calls ApiClient.get/post with the right action name and shape, and
 * decodes the "data" element into the matching model from Models.kt.
 * ViewModels call these, never ApiClient directly, so the raw JSON
 * plumbing stays in one place.
 */
class CredenceRepository {

    suspend fun login(username: String, password: String): LoginResult =
        ApiClient.get("login", mapOf("username" to username, "password" to password)).decodeAs()

    suspend fun restoreSession(username: String): LoginResult =
        ApiClient.get("restoreSession", mapOf("username" to username)).decodeAs()

    suspend fun getDashboardStats(username: String): DashboardStats =
        ApiClient.get("getDashboardStats", mapOf("username" to username)).decodeAs()

    suspend fun getStudents(username: String): List<Student> =
        ApiClient.get("getStudents", mapOf("username" to username)).decodeAs()

    suspend fun getTherapyOptions(username: String): List<TherapyOption> =
        ApiClient.get("getTherapyOptions", mapOf("username" to username)).decodeAs()

    suspend fun saveStudent(username: String, input: StudentInput): WriteResult {
        val extra = buildJsonObject { put("data", ApiClient.json.encodeToJsonElement(input)) }
        return ApiClient.post("saveStudent", username, extra).decodeAs()
    }

    /** Same "saveStudent" action as above, decoded into the richer
     * StudentSaveResult instead — used only by Convert Enquiry → Student,
     * which needs the new student's ID to pass to markEnquiryConverted(). */
    suspend fun saveStudentAndGetId(username: String, input: StudentInput): StudentSaveResult {
        val extra = buildJsonObject { put("data", ApiClient.json.encodeToJsonElement(input)) }
        return ApiClient.post("saveStudent", username, extra).decodeAs()
    }

    suspend fun getFees(username: String): List<Fee> =
        ApiClient.get("getFees", mapOf("username" to username)).decodeAs()

    suspend fun getDueList(username: String): List<Fee> =
        ApiClient.get("getDueList", mapOf("username" to username)).decodeAs()

    suspend fun getFeeRates(username: String): List<FeeRate> =
        ApiClient.get("getFeeRates", mapOf("username" to username)).decodeAs()

    suspend fun saveFee(username: String, input: FeeInput): WriteResult {
        val extra = buildJsonObject { put("data", ApiClient.json.encodeToJsonElement(input)) }
        return ApiClient.post("saveFee", username, extra).decodeAs()
    }

    suspend fun deleteFee(username: String, feeId: String): WriteResult {
        val extra = buildJsonObject { put("feeId", feeId) }
        return ApiClient.post("deleteFee", username, extra).decodeAs()
    }

    suspend fun getPayments(username: String): List<Payment> =
        ApiClient.get("getPayments", mapOf("username" to username)).decodeAs()

    suspend fun recordPayment(username: String, input: PaymentInput): PaymentRecordResult {
        val extra = buildJsonObject { put("data", ApiClient.json.encodeToJsonElement(input)) }
        return ApiClient.post("recordPayment", username, extra).decodeAs()
    }

    suspend fun updatePayment(username: String, input: PaymentEditInput): WriteResult {
        val extra = buildJsonObject { put("data", ApiClient.json.encodeToJsonElement(input)) }
        return ApiClient.post("updatePayment", username, extra).decodeAs()
    }

    suspend fun deletePayment(username: String, paymentId: String): WriteResult {
        val extra = buildJsonObject { put("paymentId", paymentId) }
        return ApiClient.post("deletePayment", username, extra).decodeAs()
    }

    // ── Enquiries ──

    suspend fun getEnquiryOptions(username: String): EnquiryOptions =
        ApiClient.get("getEnquiryOptions", mapOf("username" to username)).decodeAs()

    suspend fun getEnquiries(username: String): List<Enquiry> =
        ApiClient.get("getEnquiries", mapOf("username" to username)).decodeAs()

    suspend fun checkDuplicateContact(username: String, mobile: String): DuplicateContactCheck =
        ApiClient.get("checkDuplicateContact", mapOf("username" to username, "mobile" to mobile)).decodeAs()

    suspend fun saveEnquiry(username: String, input: EnquiryInput): WriteResult {
        val extra = buildJsonObject { put("data", ApiClient.json.encodeToJsonElement(input)) }
        return ApiClient.post("saveEnquiry", username, extra).decodeAs()
    }

    suspend fun addFollowup(username: String, input: FollowupInput): WriteResult {
        val extra = buildJsonObject { put("data", ApiClient.json.encodeToJsonElement(input)) }
        return ApiClient.post("addFollowup", username, extra).decodeAs()
    }

    suspend fun getFollowupsByEnquiry(username: String, enquiryId: String): List<EnquiryFollowup> =
        ApiClient.get("getFollowupsByEnquiry", mapOf("username" to username, "enquiryId" to enquiryId)).decodeAs()

    suspend fun deleteEnquiry(username: String, enquiryId: String, reason: String): WriteResult {
        val extra = buildJsonObject { put("enquiryId", enquiryId); put("reason", reason) }
        return ApiClient.post("deleteEnquiry", username, extra).decodeAs()
    }

    suspend fun markEnquiryConverted(username: String, enquiryId: String, studentId: String): WriteResult {
        val extra = buildJsonObject { put("enquiryId", enquiryId); put("studentId", studentId) }
        return ApiClient.post("markEnquiryConverted", username, extra).decodeAs()
    }

    suspend fun getEnquiryDashboardStats(username: String, startDate: String, endDate: String): EnquiryDashboardStats =
        ApiClient.get(
            "getEnquiryDashboardStats",
            mapOf("username" to username, "startDate" to startDate, "endDate" to endDate)
        ).decodeAs()

    // ── Staff Management: Therapists ──

    suspend fun getStaffPageData(username: String): StaffPageData =
        ApiClient.get("getStaffPageData", mapOf("username" to username)).decodeAs()

    suspend fun saveTherapist(username: String, input: TherapistInput): WriteResult {
        val extra = buildJsonObject { put("data", ApiClient.json.encodeToJsonElement(input)) }
        return ApiClient.post("saveTherapist", username, extra).decodeAs()
    }

    suspend fun deleteTherapist(username: String, therapistId: String, reason: String): WriteResult {
        val extra = buildJsonObject { put("therapistId", therapistId); put("reason", reason) }
        return ApiClient.post("deleteTherapist", username, extra).decodeAs()
    }

    // ── Staff Management: Leaves ──

    suspend fun getLeaveOptions(username: String): LeaveOptions =
        ApiClient.get("getLeaveOptions", mapOf("username" to username)).decodeAs()

    suspend fun getTherapistLeaveRequests(username: String): List<LeaveRequest> =
        ApiClient.get("getTherapistLeaveRequests", mapOf("username" to username)).decodeAs()

    suspend fun applyForTherapistLeave(username: String, input: LeaveApplicationInput): WriteResult {
        val extra = buildJsonObject { put("data", ApiClient.json.encodeToJsonElement(input)) }
        return ApiClient.post("applyForTherapistLeave", username, extra).decodeAs()
    }

    suspend fun cancelTherapistLeaveRequest(username: String, leaveId: String): WriteResult {
        val extra = buildJsonObject { put("leaveId", leaveId) }
        return ApiClient.post("cancelTherapistLeaveRequest", username, extra).decodeAs()
    }

    suspend fun decideTherapistLeave(username: String, leaveId: String, decision: String, remarks: String): WriteResult {
        val extra = buildJsonObject { put("leaveId", leaveId); put("decision", decision); put("remarks", remarks) }
        return ApiClient.post("decideTherapistLeave", username, extra).decodeAs()
    }

    suspend fun getLeaveDashboardStats(username: String): LeaveDashboardStats =
        ApiClient.get("getLeaveDashboardStats", mapOf("username" to username)).decodeAs()

    suspend fun getLeaveSummaryByTherapist(username: String): List<LeaveSummaryByTherapist> =
        ApiClient.get("getLeaveSummaryByTherapist", mapOf("username" to username)).decodeAs()

    suspend fun setAccrualRate(username: String, rate: Double): WriteResult {
        val extra = buildJsonObject { put("rate", rate) }
        return ApiClient.post("setAccrualRate", username, extra).decodeAs()
    }

    suspend fun grantBonusLeave(username: String, therapistId: String, days: Double, reason: String): WriteResult {
        val extra = buildJsonObject { put("therapistId", therapistId); put("days", days); put("reason", reason) }
        return ApiClient.post("grantBonusLeave", username, extra).decodeAs()
    }

    suspend fun getLeaveAdjustments(username: String): List<LeaveAdjustment> =
        ApiClient.get("getLeaveAdjustments", mapOf("username" to username)).decodeAs()

    suspend fun getLeaveBalances(username: String): List<LeaveBalance> =
        ApiClient.get("getLeaveBalances", mapOf("username" to username)).decodeAs()

    // ── Expenses ──

    suspend fun getExpenseOptions(username: String): ExpenseOptions =
        ApiClient.get("getExpenseOptions", mapOf("username" to username)).decodeAs()

    suspend fun getExpenses(username: String): List<Expense> =
        ApiClient.get("getExpenses", mapOf("username" to username)).decodeAs()

    suspend fun saveExpense(username: String, input: ExpenseInput): WriteResult {
        val extra = buildJsonObject { put("data", ApiClient.json.encodeToJsonElement(input)) }
        return ApiClient.post("saveExpense", username, extra).decodeAs()
    }

    suspend fun deleteExpense(username: String, expenseId: String, reason: String): WriteResult {
        val extra = buildJsonObject { put("expenseId", expenseId); put("reason", reason) }
        return ApiClient.post("deleteExpense", username, extra).decodeAs()
    }

    // ── To-Do ──

    suspend fun getToDos(username: String): List<ToDoItem> =
        ApiClient.get("getToDos", mapOf("username" to username)).decodeAs()

    suspend fun addToDo(username: String, title: String): WriteResult {
        val extra = buildJsonObject { put("title", title) }
        return ApiClient.post("addToDo", username, extra).decodeAs()
    }

    suspend fun completeToDo(username: String, taskId: String): WriteResult {
        val extra = buildJsonObject { put("taskId", taskId) }
        return ApiClient.post("completeToDo", username, extra).decodeAs()
    }

    suspend fun reopenToDo(username: String, taskId: String): WriteResult {
        val extra = buildJsonObject { put("taskId", taskId) }
        return ApiClient.post("reopenToDo", username, extra).decodeAs()
    }

    suspend fun updateToDoComment(username: String, taskId: String, comment: String): WriteResult {
        val extra = buildJsonObject { put("taskId", taskId); put("comment", comment) }
        return ApiClient.post("updateToDoComment", username, extra).decodeAs()
    }

    // ── Reports ──

    suspend fun getStaleSessionReport(username: String): List<StaleSessionEntry> =
        ApiClient.get("getStaleSessionReport", mapOf("username" to username)).decodeAs()

    suspend fun getExpenseCategorySummary(username: String): ExpenseCategorySummary =
        ApiClient.get("getExpenseCategorySummary", mapOf("username" to username)).decodeAs()

    suspend fun getMonthlySummary(
        username: String,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int
    ): List<MonthlySummaryRow> =
        ApiClient.get(
            "getMonthlySummary",
            mapOf(
                "username" to username,
                "startYear" to startYear.toString(),
                "startMonth" to startMonth.toString(),
                "endYear" to endYear.toString(),
                "endMonth" to endMonth.toString()
            )
        ).decodeAs()

    // ── Users (Admin-only, enforced server-side) ──

    suspend fun getUsers(username: String): List<AppUser> =
        ApiClient.get("getUsers", mapOf("username" to username)).decodeAs()

    suspend fun saveUser(username: String, input: UserInput): WriteResult {
        val extra = buildJsonObject { put("data", ApiClient.json.encodeToJsonElement(input)) }
        return ApiClient.post("saveUser", username, extra).decodeAs()
    }

    suspend fun deleteUser(username: String, targetUsername: String): WriteResult {
        val extra = buildJsonObject { put("targetUsername", targetUsername) }
        return ApiClient.post("deleteUser", username, extra).decodeAs()
    }
}
