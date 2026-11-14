package com.gowander.route

import androidx.compose.runtime.Composable

/**
 * The device's own voice in Vietnamese (Android TextToSpeech, iOS AVSpeechSynthesizer; plan §8.3): no library, nothing
 * leaves the phone. Other apps' audio is ducked while it speaks; it stays silent while TalkBack / VoiceOver runs, since
 * the instruction is already a live region they read.
 */
interface Speaker {
    /** null while the engine starts, false when the device has no `vi-VN` voice (text guidance only). */
    val ready: Boolean?
    fun speak(text: String)
    fun stop()
}

@Composable
expect fun rememberSpeaker(): Speaker
