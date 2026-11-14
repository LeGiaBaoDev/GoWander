package com.gowander.route

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
actual fun rememberSpeaker(): Speaker {
    val context = LocalContext.current.applicationContext
    val speaker = remember { AndroidSpeaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.close() } }
    return speaker
}

private class AndroidSpeaker(context: Context) : Speaker {
    override var ready by mutableStateOf<Boolean?>(null)
        private set
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val a11y = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= 26) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
    } else null
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS && tts.setLanguage(Locale("vi", "VN")) >= TextToSpeech.LANG_AVAILABLE
            tts.setAudioAttributes(attrs)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) = release()
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) = release()
            })
        }
    }

    override fun speak(text: String) {
        if (ready != true || a11y.isTouchExplorationEnabled) return
        if (focus != null && Build.VERSION.SDK_INT >= 26) audio.requestAudioFocus(focus)
        else @Suppress("DEPRECATION") audio.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gowander")
    }

    override fun stop() { tts.stop(); release() }

    fun close() { stop(); tts.shutdown() }

    private fun release() {
        if (focus != null && Build.VERSION.SDK_INT >= 26) audio.abandonAudioFocusRequest(focus)
        else @Suppress("DEPRECATION") audio.abandonAudioFocus(null)
    }
}
