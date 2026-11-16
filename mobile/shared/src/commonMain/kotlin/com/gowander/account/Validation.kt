package com.gowander.account

/**
 * The same rules the server applies (ADR-040 §3.2–3.3), so the form can say what is wrong before a request is sent.
 * The server stays the authority: it repeats every check.
 */

private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val usernamePattern = Regex("^[a-z][a-z0-9._]{2,29}$")
private val phonePattern = Regex("^\\+[1-9][0-9]{7,14}$")

/** Lower case, trimmed; null when it is not a plain address. */
fun normalizeEmail(raw: String): String? {
    val e = raw.trim().lowercase()
    return if (e.length <= 254 && emailPattern.matches(e)) e else null
}

/**
 * E.164, reading a leading 0 as a Vietnamese number: "0912 345 678" → "+84912345678". Null when it is not a number.
 * Empty input is null too: the caller decides whether the field is required.
 */
fun normalizePhone(raw: String): String? {
    val s = raw.filterNot { it.isWhitespace() || it == '-' || it == '.' || it == '(' || it == ')' }
    if (s.isEmpty()) return null
    val e164 = when {
        s.startsWith("+84") -> "+84" + s.removePrefix("+84").trimStart('0')
        s.startsWith("84") -> "+84" + s.removePrefix("84").trimStart('0')
        s.startsWith("0") -> "+84" + s.trimStart('0')
        else -> s
    }
    return if (e164.length <= 16 && phonePattern.matches(e164)) e164 else null
}

/** Lower case, trimmed; null when it is not 3–30 characters starting with a letter (a-z 0-9 . _). */
fun normalizeUsername(raw: String): String? {
    val u = raw.trim().lowercase()
    return if (usernamePattern.matches(u)) u else null
}

/** Why a username is not usable, or null when it is. */
fun usernameIssue(raw: String): String? = when {
    raw.isBlank() -> "Bạn đặt tên đăng nhập nhé."
    raw.trim().length < 3 -> "Tên đăng nhập cần ít nhất 3 ký tự."
    normalizeUsername(raw) == null -> "Tên đăng nhập bắt đầu bằng chữ, chỉ gồm a-z, 0-9, dấu chấm và gạch dưới."
    else -> null
}

/** Why a password is not usable, or null when it is. The server also refuses the thousand most common ones. */
fun passwordIssue(password: String, username: String = "", email: String = ""): String? = when {
    password.length < 8 -> "Mật khẩu cần ít nhất 8 ký tự."
    password.length > 128 -> "Mật khẩu dài tối đa 128 ký tự."
    password.isBlank() -> "Bạn nhập mật khẩu nhé."
    username.isNotBlank() && password.equals(username, ignoreCase = true) -> "Mật khẩu không được giống tên đăng nhập."
    email.isNotBlank() && (password.equals(email, ignoreCase = true) || password.equals(email.substringBefore('@'), ignoreCase = true)) ->
        "Mật khẩu không được giống email của bạn."
    else -> null
}

/** How strong the typed password looks: 0 (empty) to 3, for the three bars under the field. */
fun passwordStrength(password: String): Int {
    if (password.isEmpty()) return 0
    var score = 1
    if (password.length >= 12) score++
    if (password.any { it.isDigit() } && password.any { it.isLetter() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return score.coerceAtMost(3)
}

/** What the person typed into the single sign-in field. */
sealed interface Identifier {
    data class Email(val value: String) : Identifier
    data class Phone(val value: String) : Identifier
    data class Username(val value: String) : Identifier
    data object None : Identifier
}

fun identify(raw: String): Identifier {
    val s = raw.trim()
    if (s.isEmpty()) return Identifier.None
    if (s.contains("@")) normalizeEmail(s)?.let { return Identifier.Email(it) }
    val phone = normalizePhone(s)
    if (phone != null && (s.startsWith("+") || s.all { it.isDigit() || it in " .-" })) return Identifier.Phone(phone)
    normalizeUsername(s)?.let { return Identifier.Username(it) }
    return Identifier.None
}

/** Why a sign-in identifier is not usable, or null when it is. */
fun identifierIssue(raw: String): String? = if (identify(raw) == Identifier.None) {
    "Nhập email, số điện thoại hoặc tên đăng nhập của bạn."
} else {
    null
}
