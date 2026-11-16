package com.gowander.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gowander.api.ApiError
import com.gowander.api.AppUser
import com.gowander.api.AuthResponse
import com.gowander.api.GoWanderApi
import com.gowander.ui.Strings

/** Who is using the app right now (ADR-040): a guest can do everything except keep a portfolio or a profile. */
sealed interface Account {
    data object Guest : Account
    data class SignedIn(val user: AppUser) : Account
}

/** A label the account pages show and the server stores with the session ("iPhone", "Pixel 7"). */
expect val deviceName: String

/**
 * The signed-in state of the app, shared by every screen. It owns the token: the API only ever sees it through
 * [GoWanderApi.useToken], the Keychain/Keystore keeps the real copy.
 *
 * Every call returns null on success or a Vietnamese sentence to show; [busy] is true while a request runs, so a
 * button can say "Đang đăng nhập…" and ignore a second tap.
 */
class AccountStore(
    private val api: GoWanderApi,
    private val tokens: TokenStore = platformTokens,
    private val device: String = deviceName,
) {
    var state: Account by mutableStateOf(Account.Guest)
        private set
    var busy: Boolean by mutableStateOf(false)
        private set
    var lastError: String? by mutableStateOf(null)
        private set

    /** False when the last register call could not send the code: the app offers "Gửi lại mã". */
    var codeSent: Boolean by mutableStateOf(true)
        private set

    val user: AppUser? get() = (state as? Account.SignedIn)?.user
    val isSignedIn: Boolean get() = state is Account.SignedIn

    /**
     * Reads the saved token at start-up and asks the server who it belongs to. A token the server no longer accepts
     * is dropped; a network failure keeps it for the next open (the person stays a guest until then).
     */
    suspend fun start() {
        val token = tokens.read() ?: return
        api.useToken(token)
        try {
            state = Account.SignedIn(api.me())
        } catch (e: ApiError.Server) {
            if (e.status == 401) clear() // revoked or expired: sign out quietly
        } catch (e: ApiError.Network) {
            api.useToken(null) // offline: try again next time, without pretending to be signed in
        } catch (e: ApiError) {
            clear()
        }
    }

    /** After the network comes back: who is this token? */
    suspend fun refresh(): String? {
        val token = tokens.read() ?: return null
        api.useToken(token)
        return guard { state = Account.SignedIn(api.me()) }
    }

    suspend fun signIn(identifier: String, password: String): String? {
        val issue = identifierIssue(identifier) ?: if (password.isEmpty()) "Bạn nhập mật khẩu nhé." else null
        if (issue != null) return remember(issue)
        return guard { accept(api.login(identifier.trim(), password, device)) }
    }

    suspend fun register(name: String, username: String, email: String, phone: String, password: String): String? {
        val issue = when {
            name.isBlank() -> "Bạn nhập tên hiển thị nhé."
            else -> usernameIssue(username) ?: passwordIssue(password, username, email)
                ?: if (normalizeEmail(email) == null) "Email chưa đúng, bạn kiểm tra lại nhé." else null
                ?: if (phone.isNotBlank() && normalizePhone(phone) == null) "Số điện thoại chưa đúng, bạn kiểm tra lại nhé." else null
        }
        if (issue != null) return remember(issue)
        return guard {
            val auth = api.register(name.trim(), normalizeUsername(username) ?: username.trim(), normalizeEmail(email) ?: email.trim(),
                normalizePhone(phone) ?: "", password, device)
            codeSent = auth.codeSent
            accept(auth)
        }
    }

    suspend fun signInWithGoogle(idToken: String): String? = guard { accept(api.loginWithGoogle(idToken, device)) }

    /** Sets the state from a Google link response (Cài đặt › Liên kết Google). */
    suspend fun linkGoogle(idToken: String): String? = guard { state = Account.SignedIn(api.linkGoogle(idToken)) }

    suspend fun unlinkGoogle(): String? = guard { state = Account.SignedIn(api.unlinkGoogle()) }

    suspend fun verifyEmail(code: String): String? {
        if (code.length != 6 || code.any { !it.isDigit() }) return remember("Mã gồm 6 chữ số, bạn kiểm tra lại nhé.")
        return guard { state = Account.SignedIn(api.verifyEmail(code)) }
    }

    suspend fun resendCode(purpose: String = "VERIFY_EMAIL"): String? = guard { api.resendCode(purpose) }

    /** Always succeeds from the person's point of view: the server answers the same whether the email exists. */
    suspend fun forgotPassword(email: String): String? {
        if (normalizeEmail(email) == null) return remember("Email chưa đúng, bạn kiểm tra lại nhé.")
        return guard { api.forgotPassword(email.trim()) }
    }

    /** Resets the password and signs this device in with it; every other device is signed out by the server. */
    suspend fun resetPassword(email: String, code: String, newPassword: String): String? {
        val issue = passwordIssue(newPassword, email = email)
        if (issue != null) return remember(issue)
        return guard {
            api.resetPassword(email.trim(), code, newPassword)
            accept(api.login(email.trim(), newPassword, device))
        }
    }

    suspend fun updateProfile(name: String?, username: String?, phone: String?): String? {
        val issue = when {
            username != null -> usernameIssue(username)
            phone != null && phone.isNotBlank() && normalizePhone(phone) == null -> "Số điện thoại chưa đúng, bạn kiểm tra lại nhé."
            else -> null
        }
        if (issue != null) return remember(issue)
        return guard {
            state = Account.SignedIn(
                api.updateMe(name?.trim(), username?.let { normalizeUsername(it) ?: it.trim() }, phone?.let { normalizePhone(it) ?: "" }),
            )
        }
    }

    suspend fun changePassword(current: String, new: String): String? {
        val issue = passwordIssue(new, user?.username.orEmpty(), user?.email.orEmpty())
        if (issue != null) return remember(issue)
        return guard { api.changePassword(current, new) }
    }

    /** Deletes the account for good; [idToken] confirms a Google-only account. */
    suspend fun deleteAccount(password: String, idToken: String?): String? {
        val message = guard { api.deleteMe(password.takeIf { it.isNotBlank() }, idToken) }
        if (message == null) clear()
        return message
    }

    /** Signs out; [everywhere] also revokes the other devices. Network failures do not keep anyone signed in. */
    suspend fun signOut(everywhere: Boolean = false): String? {
        val message = try {
            if (everywhere) api.logoutEverywhere() else api.logout()
            null
        } catch (e: ApiError.Server) {
            if (e.status == 401) null else e.userMessage // an already-dead session needs no warning
        } catch (e: ApiError.Network) {
            null // the token is dropped locally anyway; the server copy expires in 60 days
        } catch (e: ApiError) {
            null
        }
        clear()
        return message
    }

    suspend fun usernameAvailable(username: String): Boolean? = try {
        if (usernameIssue(username) != null) null else api.usernameAvailable(username.trim().lowercase())
    } catch (e: ApiError) {
        null // the field simply says nothing; the server checks again on submit
    }

    fun clearError() { lastError = null }

    private fun accept(auth: AuthResponse) {
        tokens.write(auth.session.token)
        api.useToken(auth.session.token)
        state = Account.SignedIn(auth.user)
    }

    private fun clear() {
        tokens.write(null)
        api.useToken(null)
        state = Account.Guest
    }

    private fun remember(message: String): String {
        lastError = message
        return message
    }

    private suspend fun guard(block: suspend () -> Unit): String? {
        busy = true
        lastError = null
        return try {
            block()
            null
        } catch (e: ApiError.Server) {
            remember(e.userMessage)
        } catch (e: ApiError.Network) {
            remember(Strings.NETWORK_ERROR)
        } catch (e: ApiError) {
            remember("Có lỗi xảy ra, bạn thử lại nhé.")
        } finally {
            busy = false
        }
    }
}
