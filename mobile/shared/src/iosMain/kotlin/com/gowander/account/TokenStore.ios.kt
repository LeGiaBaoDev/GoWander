package com.gowander.account

import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.UIKit.UIDevice

/**
 * iOS: the token lives in the Keychain, readable after the first unlock and never synced to another device
 * (ADR-040 §3.4). It never goes through NSUserDefaults.
 */
@OptIn(ExperimentalForeignApi::class)
actual object SecureToken {
    private const val SERVICE = "com.gowander.session"
    private const val ACCOUNT = "token"

    actual fun read(): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val query = query(returnsData = true)
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return null
        val data = result.value as? CFDataRef ?: return null
        val length = CFDataGetLength(data).toInt()
        val bytes = CFDataGetBytePtr(data) ?: return null
        bytes.readBytes(length).decodeToString()
    }

    actual fun write(token: String?) {
        delete()
        if (token == null) return
        val bytes = token.encodeToByteArray()
        val data = bytes.usePinned { CFDataCreate(kCFAllocatorDefault, it.addressOf(0).reinterpret<UByteVar>(), bytes.size.toLong()) }
        val query = query(returnsData = false, value = data)
        SecItemAdd(query, null)
        CFRelease(query)
        CFRelease(data)
    }

    /** The Keychain keeps one item per service + account: deleting first makes a write idempotent. */
    private fun delete() {
        val query = query(returnsData = false)
        SecItemDelete(query)
        CFRelease(query)
    }

    private fun cfString(text: String): CFStringRef? = memScoped {
        CFStringCreateWithCString(kCFAllocatorDefault, text.cstr.ptr, kCFStringEncodingUTF8)
    }

    /**
     * The item description shared by read, write and delete. Extra attributes (return data, the value itself) are
     * added only where they belong: a delete query carrying them would match nothing.
     */
    private fun query(returnsData: Boolean, value: CFDataRef? = null): CFDictionaryRef {
        // The type callbacks make the dictionary retain its keys and values; with null callbacks it would keep raw
        // pointers and the strings freed below would dangle (this crashed SecItemCopyMatching, 16-11-2026).
        val dict: CFMutableDictionaryRef = CFDictionaryCreateMutable(
            kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
        )!!
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        val service = cfString(SERVICE)
        val account = cfString(ACCOUNT)
        CFDictionaryAddValue(dict, kSecAttrService, service)
        CFDictionaryAddValue(dict, kSecAttrAccount, account)
        CFRelease(service)
        CFRelease(account)
        if (returnsData) {
            CFDictionaryAddValue(dict, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(dict, kSecMatchLimit, kSecMatchLimitOne)
        }
        if (value != null) {
            CFDictionaryAddValue(dict, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            CFDictionaryAddValue(dict, kSecValueData, value)
        }
        return dict
    }
}

/** "iPhone", "iPad": shown next to the session in Cài đặt. */
actual val deviceName: String
    get() = UIDevice.currentDevice.model
