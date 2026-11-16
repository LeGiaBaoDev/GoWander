package com.gowander.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidationTest {
    @Test
    fun emails() {
        assertEquals("lananh@example.com", normalizeEmail("  LanAnh@Example.COM "))
        assertNull(normalizeEmail("lananh@example"))
        assertNull(normalizeEmail("lan anh@example.com"))
        assertNull(normalizeEmail(""))
    }

    @Test
    fun vietnamesePhonesBecomeE164() {
        assertEquals("+84912345678", normalizePhone("0912 345 678"))
        assertEquals("+84912345678", normalizePhone("+84 912 345 678"))
        assertEquals("+84912345678", normalizePhone("84-912-345-678"))
        assertEquals("+14155550100", normalizePhone("+1 415 555 0100"))
        assertNull(normalizePhone("0123"))
        assertNull(normalizePhone("abc"))
        assertNull(normalizePhone(""))
    }

    @Test
    fun usernames() {
        assertEquals("lananh", normalizeUsername("LanAnh"))
        assertEquals("lan.anh", normalizeUsername(" lan.anh "))
        assertNull(normalizeUsername("9lananh"))
        assertNull(normalizeUsername("la"))
        assertEquals("Tên đăng nhập cần ít nhất 3 ký tự.", usernameIssue("la"))
        assertTrue(usernameIssue("9lananh")!!.contains("bắt đầu bằng chữ"))
        assertNull(usernameIssue("lananh"))
    }

    @Test
    fun passwords() {
        assertNull(passwordIssue("cà phê sữa đá 47", "lananh", "lananh@example.com"))
        assertEquals("Mật khẩu cần ít nhất 8 ký tự.", passwordIssue("1234"))
        assertTrue(passwordIssue("lananh123", "lananh123")!!.contains("tên đăng nhập"))
        assertTrue(passwordIssue("lananh@example.com", "", "lananh@example.com")!!.contains("email"))
    }

    @Test
    fun strengthHasThreeSteps() {
        assertEquals(0, passwordStrength(""))
        assertEquals(1, passwordStrength("abcdefgh"))
        assertEquals(2, passwordStrength("abcdefgh1"))
        assertEquals(3, passwordStrength("abcdefgh1!"))
        assertEquals(3, passwordStrength("mật khẩu dài 2026 có dấu!"))
    }

    @Test
    fun theSignInFieldTakesThreeShapes() {
        assertEquals(Identifier.Email("lananh@example.com"), identify("LanAnh@Example.com"))
        assertEquals(Identifier.Phone("+84912345678"), identify("0912 345 678"))
        assertEquals(Identifier.Username("lananh"), identify("LanAnh"))
        assertEquals(Identifier.None, identify("!!"))
        assertEquals(Identifier.None, identify("  "))
        assertNull(identifierIssue("lananh"))
        assertTrue(identifierIssue("@@")!!.startsWith("Nhập email"))
    }
}
