package ovh.gabrielhuav.pow.features.streetfighter.data

import android.content.Context
// 🍏 En vez de `org.json` (que es de la JVM y NO existe en iOS), los accesores compatibles de
// `:shared`. Se llaman igual y respetan la misma semántica: solo cambia el import.
import ovh.gabrielhuav.pow.data.json.getJSONObject
import ovh.gabrielhuav.pow.data.json.optJSONObject
import ovh.gabrielhuav.pow.data.json.optString
import ovh.gabrielhuav.pow.data.json.powJsonObjeto
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * 🆕 (2026-07-20) Catálogo de FRASES por CLIP de voz (subtítulo de cada `special_*.m4a`).
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

    /** Mapa clip (nombre base del .m4a) → frase curada. Clips sin `es` no aparecen. */
    fun load(context: Context): Map<String, SfVoicePhrase> {
        cache.get()?.let { return it }
        val loaded = runCatching {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()?.let { parse(it) } ?: emptyMap()
        cache.compareAndSet(null, loaded)
        return cache.get() ?: loaded
    }

    private fun parse(raw: String): Map<String, SfVoicePhrase> {
        val clips = powJsonObjeto(raw).optJSONObject("clips") ?: return emptyMap()
        val out = mutableMapOf<String, SfVoicePhrase>()
        for (file in clips.keys) {
            val o = clips.getJSONObject(file)
            val es = o.optString("es", "")
            if (es.isBlank()) continue // sin frase curada = sin subtítulo (los draft no cuentan)
            out[file] = SfVoicePhrase(es = es, en = o.optString("en", ""))
        }
        return out
    }
}
