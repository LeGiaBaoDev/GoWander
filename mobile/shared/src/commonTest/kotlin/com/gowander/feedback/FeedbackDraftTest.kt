package com.gowander.feedback

import com.gowander.nav.AppNav
import com.gowander.nav.Screen
import com.gowander.nav.Tab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse

class FeedbackDraftTest {
    @Test fun guestCanSubmitWithoutContactButNotWithoutMessage() {
        assertNotNull(FeedbackDraft().validationError())
        assertNull(FeedbackDraft(message = "Giờ mở cửa chưa đúng").validationError())
        assertNotNull(FeedbackDraft(message = "  ").validationError())
        assertNotNull(FeedbackDraft(message = "a".repeat(1001)).validationError())
        assertNotNull(FeedbackDraft(message = "Nội dung", email = "abc").validationError())
    }

    @Test fun screenshotAndSenderAreBounded() {
        val draft = FeedbackDraft(message = "Nội dung", name = "Lan Anh", email = "lan@example.com")
        assertNull(draft.validationError())
        assertNotNull(draft.copy(image = ByteArray(5 * 1024 * 1024 + 1)).validationError())
        assertNotNull(draft.copy(name = "a".repeat(101)).validationError())
    }

    @Test fun contextUsesScreenAndEntityButNeverGpsOrPrivatePlanText() {
        assertEquals("Khám phá", feedbackContext(AppNav(Tab.Discover)))
        assertEquals("Địa điểm · p123", feedbackContext(AppNav().push(Screen.Place("p123"))))
        assertEquals("Chỉnh sửa kế hoạch", feedbackContext(AppNav().push(Screen.PlanEdit("private-trip-name"))))
        assertFalse(feedbackContext(AppNav().push(Screen.PlanEdit("private-trip-name"))).contains("private-trip-name"))
    }
}
