package ovh.gabrielhuav.pow.features.streetfighter.data

import android.content.Context
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * 🆕 (2026-07-20) Catálogo de FRASES por CLIP de voz (subtítulo de cada `special_*.ogg`).
 *
 * Fuente: `assets/STREETFIGHTER/DATA/voice_phrases.json`, generado/actualizado por
 * `tools/build_voice_phrases_catalog.py` (conserva lo curado por el dueño; los `draft`
 * de Whisper son solo referencia y NUNCA se muestran).
 *
 * Es el "lugar único" donde el dueño escribe/valida la frase de cada audio: si un clip
 * tiene `es` no vacía, esa frase MANDA sobre la inline de `SfVoiceLine` en el VM.
 * Mientras `voiceSubtitlesEnabled` siga en false, nada se muestra (código listo, apagado).
 */
data class SfVoicePhrase(val es: String, val en: String) {
    /** Texto según locale del juego ("en" → inglés si existe; resto → español). */
    fun textForLang(langTag: String): String {
        val lang = langTag.lowercase(Locale.ROOT).take(2)
        return if (lang == "en" && en.isNotBlank()) en else es
    }
}

object SfVoicePhrases {
    private const val ASSET = "STREETFIGHTER/DATA/voice_phrases.json"
    private val cache = AtomicReference<Map<String, SfVoicePhrase>?>(null)

    fun clearCache() {
        cache.set(null)
    }

    /** Mapa clip (nombre base del .ogg) → frase curada. Clips sin `es` no aparecen. */
    fun load(context: Context): Map<String, SfVoicePhrase> {
        cache.get()?.let { return it }
        val loaded = runCatching {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()?.let { parse(it) } ?: emptyMap()
        cache.compareAndSet(null, loaded)
        return cache.get() ?: loaded
    }

    private fun parse(raw: String): Map<String, SfVoicePhrase> {
        val clips = JSONObject(raw).optJSONObject("clips") ?: return emptyMap()
        val out = mutableMapOf<String, SfVoicePhrase>()
        val keys = clips.keys()
        while (keys.hasNext()) {
            val file = keys.next()
            val o = clips.getJSONObject(file)
            val es = o.optString("es", "")
            if (es.isBlank()) continue // sin frase curada = sin subtítulo (los draft no cuentan)
            out[file] = SfVoicePhrase(es = es, en = o.optString("en", ""))
        }
        return out
    }
}
