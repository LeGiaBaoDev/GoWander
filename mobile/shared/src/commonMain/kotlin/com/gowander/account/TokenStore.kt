package com.gowander.account

/**
 * Where the session token lives (ADR-040 §3.4): the iOS Keychain or Android Keystore, never plain preferences and
 * never a log line. Reading returns null when there is no token or when the system refuses to decrypt it.
 */
expect object SecureToken {
    fun read(): String?
    fun write(token: String?)
}

/** For tests and previews: the same contract, no keychain. */
class MemoryTokenStore(private var token: String? = null) : TokenStore {
    override fun read(): String? = token
    override fun write(token: String?) { this.token = token }
}

/** The two calls the account store needs; [SecureToken] is the real one on both platforms. */
interface TokenStore {
    fun read(): String?
    fun write(token: String?)
}

/** The platform store as a [TokenStore]. */
val platformTokens: TokenStore = object : TokenStore {
    override fun read(): String? = SecureToken.read()
    override fun write(token: String?) = SecureToken.write(token)
}
