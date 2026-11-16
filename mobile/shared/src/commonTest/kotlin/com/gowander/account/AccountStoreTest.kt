package com.gowander.account

import com.gowander.api.ApiError
import com.gowander.api.AppSession
import com.gowander.api.AppUser
import com.gowander.api.AuthResponse
import com.gowander.api.FakeApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountStoreTest {
    private val lan = AppUser("u1", "Lan Anh", "lananh", "lananh@example.com", emailVerified = true)

    private fun fake(token: String? = null) = FakeApi().also {
        it.meResult = { lan }
        it.loginResult = { _, _ -> AuthResponse(AppSession("fresh-token", "2027-01-15T00:00:00Z"), lan) }
        it.token = token
    }

    @Test
    fun startAsksWhoTheSavedTokenBelongsTo() = runTest {
        val api = fake("saved-token")
        val tokens = MemoryTokenStore("saved-token")
        val store = AccountStore(api, tokens, device = "iPhone")

        store.start()

        assertTrue(store.isSignedIn)
        assertEquals("Lan Anh", store.user?.name)
        assertEquals("saved-token", api.token) // the API got the token from the keychain
    }

    @Test
    fun aRevokedTokenSignsOutQuietly() = runTest {
        val api = fake("old")
        api.error = ApiError.Server("AUTH_REQUIRED", "Bạn cần đăng nhập để làm việc này.", 401)
        val tokens = MemoryTokenStore("old")

        AccountStore(api, tokens, device = "iPhone").start()

        assertFalse(AccountStore(api, tokens, device = "iPhone").isSignedIn)
        assertNull(tokens.read()) // the dead token is not kept
    }

    @Test
    fun beingOfflineKeepsTheTokenForNextTime() = runTest {
        val api = fake("saved")
        api.error = ApiError.Network(RuntimeException("offline"))
        val tokens = MemoryTokenStore("saved")
        val store = AccountStore(api, tokens, device = "iPhone")

        store.start()

        assertFalse(store.isSignedIn) // a guest until the server answers
        assertEquals("saved", tokens.read()) // but the session is not thrown away
    }

    @Test
    fun signInKeepsTheToken() = runTest {
        val api = fake()
        val tokens = MemoryTokenStore()
        val store = AccountStore(api, tokens, device = "Pixel")

        assertNull(store.signIn("LanAnh", "cà phê sữa đá 47"))

        assertTrue(store.isSignedIn)
        assertEquals("fresh-token", tokens.read())
        assertEquals("fresh-token", api.token)
        assertFalse(store.busy)
    }

    @Test
    fun aWrongPasswordShowsTheServerSentence() = runTest {
        val api = fake()
        api.loginResult = { _, _ -> throw ApiError.Server("LOGIN_FAILED", "Sai email, số điện thoại, tên đăng nhập hoặc mật khẩu.", 401) }
        val store = AccountStore(api, MemoryTokenStore(), device = "Pixel")

        val message = store.signIn("lananh", "sai mật khẩu")

        assertEquals("Sai email, số điện thoại, tên đăng nhập hoặc mật khẩu.", message)
        assertEquals(message, store.lastError)
        assertFalse(store.isSignedIn)
    }

    @Test
    fun theFormChecksBeforeCalling() = runTest {
        val api = fake()
        val store = AccountStore(api, MemoryTokenStore(), device = "Pixel")

        assertTrue(store.signIn("@@", "x")!!.startsWith("Nhập email"))
        assertNull(api.token) // nothing was sent
        assertEquals("Bạn nhập mật khẩu nhé.", store.signIn("lananh", ""))
        assertEquals("Tên đăng nhập cần ít nhất 3 ký tự.", store.register("Lan", "la", "a@b.co", "", "cà phê sữa đá 47"))
        assertEquals("Email chưa đúng, bạn kiểm tra lại nhé.", store.register("Lan", "lananh", "a@b", "", "cà phê sữa đá 47"))
        assertEquals("Số điện thoại chưa đúng, bạn kiểm tra lại nhé.", store.register("Lan", "lananh", "a@b.co", "0123", "cà phê sữa đá 47"))
    }

    @Test
    fun registerStoresTheSessionAndReportsWhetherTheCodeWasSent() = runTest {
        val api = fake()
        api.registerResult = { AuthResponse(AppSession("reg-token", "2027-01-15T00:00:00Z"), lan.copy(emailVerified = false), codeSent = false) }
        val tokens = MemoryTokenStore()
        val store = AccountStore(api, tokens, device = "iPhone")

        assertNull(store.register("Lan Anh", "lananh", "  LanAnh@Example.com ", "0912 345 678", "cà phê sữa đá 47"))

        assertFalse(store.codeSent)
        assertFalse(store.user!!.emailVerified)
        assertEquals("reg-token", tokens.read())
    }

    @Test
    fun aCodeMustBeSixDigits() = runTest {
        val api = fake()
        val store = AccountStore(api, MemoryTokenStore("t"), device = "iPhone")
        assertEquals("Mã gồm 6 chữ số, bạn kiểm tra lại nhé.", store.verifyEmail("12a"))
        assertEquals("Mã gồm 6 chữ số, bạn kiểm tra lại nhé.", store.verifyEmail("123"))
    }

    @Test
    fun resetSignsThisDeviceInWithTheNewPassword() = runTest {
        val api = fake()
        var sentNewPassword: String? = null
        api.loginResult = { identifier, password ->
            sentNewPassword = password
            AuthResponse(AppSession("after-reset", "2027-01-15T00:00:00Z"), lan)
        }
        val tokens = MemoryTokenStore()
        val store = AccountStore(api, tokens, device = "iPhone")

        assertNull(store.resetPassword("lananh@example.com", "123456", "mật khẩu mới 2026"))

        assertEquals("mật khẩu mới 2026", sentNewPassword)
        assertEquals("after-reset", tokens.read())
        assertTrue(store.isSignedIn)
    }

    @Test
    fun signingOutDropsTheTokenEvenWithoutNetwork() = runTest {
        val api = fake("tok")
        api.error = ApiError.Network(RuntimeException("offline"))
        val tokens = MemoryTokenStore("tok")
        val store = AccountStore(api, tokens, device = "iPhone")
        store.start()
        api.error = null
        store.signIn("lananh", "cà phê sữa đá 47")
        api.error = ApiError.Network(RuntimeException("offline"))

        assertNull(store.signOut())

        assertFalse(store.isSignedIn)
        assertNull(tokens.read())
        assertNull(api.token)
    }

    @Test
    fun deletingTheAccountSignsOut() = runTest {
        val api = fake("tok")
        val tokens = MemoryTokenStore("tok")
        val store = AccountStore(api, tokens, device = "iPhone")
        store.start()

        assertNull(store.deleteAccount("mật khẩu", null))

        assertFalse(store.isSignedIn)
        assertNull(tokens.read())
    }

    @Test
    fun aUsernameAlreadyUsedIsReported() = runTest {
        val api = fake("tok")
        val tokens = MemoryTokenStore("tok")
        val store = AccountStore(api, tokens, device = "iPhone")
        store.start()
        api.meResult = { throw ApiError.Server("ACCOUNT_EXISTS", "Email, số điện thoại hoặc tên đăng nhập này đã được dùng. Thử cách khác nhé.", 409) }

        val message = store.updateProfile(null, "lananh", null)

        assertTrue(message!!.contains("đã được dùng"))
    }
}
