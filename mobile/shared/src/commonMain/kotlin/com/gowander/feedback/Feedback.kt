package com.gowander.feedback

import com.gowander.nav.AppNav
import com.gowander.nav.Screen
import com.gowander.nav.Tab

/** Feedback kinds, as the API names them (POST /api/v1/feedbacks), with their Vietnamese labels. */
enum class FeedbackKind(val api: String, val label: String) {
    Information("INFORMATION", "Thông tin chưa đúng"),
    Bug("BUG", "Lỗi ứng dụng"),
    Suggestion("SUGGESTION", "Góp ý tính năng"),
    Other("OTHER", "Khác"),
}

/**
 * What the user is writing. Guests may send without a name or email; both are optional and unverified. The image
 * stays on the device until Send.
 */
data class FeedbackDraft(
    val kind: FeedbackKind = FeedbackKind.Information,
    val message: String = "",
    val context: String = "",
    val name: String = "",
    val email: String = "",
    val image: ByteArray? = null,
) {
    /** A sentence for the user, or null when the draft can be sent (the same limits the server checks). */
    fun validationError(): String? = when {
        message.isBlank() -> "Hãy viết nội dung góp ý."
        message.trim().length > 1000 -> "Nội dung tối đa 1000 ký tự."
        name.trim().length > 100 -> "Tên tối đa 100 ký tự."
        email.isNotBlank() && !EMAIL.matches(email.trim()) -> "Email chưa đúng định dạng."
        email.trim().length > 254 -> "Email quá dài."
        (image?.size ?: 0) > FEEDBACK_IMAGE_MAX_BYTES -> "Ảnh phải nhỏ hơn 5 MB."
        else -> null
    }

    override fun equals(other: Any?) = other is FeedbackDraft && kind == other.kind && message == other.message && context == other.context &&
        name == other.name && email == other.email && image.contentEqualsNullable(other.image)
    override fun hashCode() = listOf(kind, message, context, name, email, image?.contentHashCode()).hashCode()
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?) = if (this == null) other == null else other != null && contentEquals(other)

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/**
 * Where the feedback was written: the screen and, for a place, its name (or id while the name is not loaded). Never a
 * location, a route's endpoints or the text of a private plan (the Admin sees this line).
 */
fun feedbackContext(nav: AppNav, placeName: String? = null): String = when (val top = nav.top) {
    null -> when (nav.tab) {
        Tab.Map -> "Bản đồ"
        Tab.Discover -> "Khám phá"
        Tab.Journey -> "Hành trình"
        Tab.Profile -> "Hồ sơ"
    }
    is Screen.Place -> "Địa điểm · " + (placeName?.takeIf { it.isNotBlank() } ?: top.id)
    is Screen.Region -> "Cẩm nang vùng · " + top.id
    is Screen.Dish -> "Món ăn · " + top.id
    is Screen.Trip -> "Hành trình gợi ý · " + top.id
    is Screen.PlanEdit, is Screen.PlanMap -> "Chỉnh sửa kế hoạch"
    Screen.Search, Screen.PickOrigin, Screen.PickDestination -> "Tìm tuyến"
    Screen.Results -> "Danh sách tuyến"
    Screen.Detail -> "Chi tiết tuyến"
    Screen.Steps -> "Xem từng bước"
    Screen.Navigation -> "Dẫn đường"
    is Screen.Preview -> "Xem trước tuyến"
    is Screen.FindPlace -> "Tìm địa điểm"
    Screen.PortfolioEdit -> "Thiết kế portfolio"
    Screen.Settings -> "Cài đặt"
    Screen.SearchHistory -> "Lịch sử tìm kiếm"
    Screen.AccountWelcome, Screen.SignIn, Screen.Register, Screen.VerifyEmail, Screen.ForgotPassword ->
        "Tài khoản · đăng nhập"
    Screen.AccountSettings -> "Tài khoản · cài đặt"
    Screen.CompleteProfile -> "Tài khoản · hoàn tất hồ sơ"
    Screen.DeleteAccount -> "Tài khoản · xoá tài khoản"
}
