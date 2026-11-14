package com.gowander.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeVoicePrompt
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.setActive
import platform.UIKit.UIAccessibilityIsVoiceOverRunning
import platform.darwin.NSObject

@Composable
actual fun rememberSpeaker(): Speaker {
    val speaker = remember { IosSpeaker() }
    DisposableEffect(speaker) { onDispose { speaker.stop() } }
    return speaker
}

@OptIn(ExperimentalForeignApi::class)
private class IosSpeaker : Speaker {
    private val voice = AVSpeechSynthesisVoice.voiceWithLanguage("vi-VN")
    override val ready: Boolean = voice != null
    private val synth = AVSpeechSynthesizer()
    private val session = AVAudioSession.sharedInstance()

    // Give the other apps their full volume back once the sentence is over.
    private val delegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
        @ObjCSignatureOverride
        override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didFinishSpeechUtterance: AVSpeechUtterance) = release()
        @ObjCSignatureOverride
        override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didCancelSpeechUtterance: AVSpeechUtterance) = release()
    }

    init { synth.delegate = delegate }

    override fun speak(text: String) {
        if (!ready || UIAccessibilityIsVoiceOverRunning()) return
        session.setCategory(AVAudioSessionCategoryPlayback, mode = AVAudioSessionModeVoicePrompt, options = AVAudioSessionCategoryOptionDuckOthers, error = null)
        session.setActive(true, error = null)
        synth.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        synth.speakUtterance(AVSpeechUtterance.speechUtteranceWithString(text).also { it.voice = voice })
    }

    override fun stop() { synth.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate); release() }

    private fun release() { session.setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null) }
}
