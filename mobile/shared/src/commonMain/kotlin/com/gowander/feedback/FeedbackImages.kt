package com.gowander.feedback

import androidx.compose.runtime.Composable

/** Images stay on the device until the user reviews the attachment and presses Send. */
interface FeedbackImages {
    /** Capture the app window, never another app. The caller hides its feedback overlay first. */
    fun capture(onResult: (Result<ByteArray?>) -> Unit)
    /** The system picker grants access only to the image chosen by the user; null means cancelled. */
    fun pick(onResult: (Result<ByteArray?>) -> Unit)
}

const val FEEDBACK_IMAGE_MAX_BYTES = 5 * 1024 * 1024

@Composable
expect fun rememberFeedbackImages(): FeedbackImages
