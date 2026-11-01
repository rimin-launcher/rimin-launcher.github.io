package kr.personal.sealion.one.launcher.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Korean TTS for gear / over-speed / lock announcements. */
class Announcer(context: Context) {
    private var ready = false
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }.also { it.language = Locale.KOREAN }

    fun say(text: String, volume: Float) {
        if (!ready || volume <= 0f || text.isBlank()) return
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, text.hashCode().toString())
    }

    fun shutdown() = tts.shutdown()
}
