package ovh.gabrielhuav.pow.features.streetfighter.data

import android.content.Context
// 🍏 Accesores compatibles de `:shared` en vez de `org.json` (de la JVM, no existe en iOS).
import ovh.gabrielhuav.pow.data.json.getJSONObject
import ovh.gabrielhuav.pow.data.json.optJSONObject
import ovh.gabrielhuav.pow.data.json.optLong
import ovh.gabrielhuav.pow.data.json.optString
import ovh.gabrielhuav.pow.data.json.powJsonObjeto
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Catálogo de frases del special (audio + subtítulo i18n).
 *
 * Fuente: `assets/STREETFIGHTER/DATA/special_phrases.json`
 * Generado por `tools/build_special_phrases_pack.py` desde
 * `tools/sf_voice_scrape/special_phrases_catalog.json`.
 *
 * - `phrase_es` = original (lo que se detectó / curó del audio)
 * - `phrase_en` = traducción para idioma inglés del juego
 * - `phrase_hud` = A-Z/0-9 para la fuente arcade POW del HUD
 *
 * Los 21 peleadores tienen audio; los SFX sin habla pueden no tener subtítulo.
 */
data class SfSpecialPhrase(
    val fighterId: SfFighterId,
    val phraseEs: String,
    val phraseEn: String,
    val phraseHud: String,
    val audioKey: String,
    val subtitleMs: Long,
    val tier: String,
) {
    /** Texto a mostrar según locale del juego ("es" / "en" / otro → es). */
    fun textForLang(langTag: String): String {
        val lang = langTag.lowercase(Locale.ROOT).take(2)
        return if (lang == "en") phraseEn else phraseEs
    }

    /** Línea para la fuente pixel (solo A-Z 0-9). */
    fun hudLine(): String = phraseHud.ifBlank {
        phraseEs.uppercase(Locale.ROOT)
            .replace('Á', 'A').replace('É', 'E').replace('Í', 'I')
            .replace('Ó', 'O').replace('Ú', 'U').replace('Ñ', 'N')
            .replace('Ü', 'U')
            .replace(Regex("[^A-Z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

object SfSpecialPhrases {
    private const val ASSET = "STREETFIGHTER/DATA/special_phrases.json"
    private val cache = AtomicReference<Map<SfFighterId, SfSpecialPhrase>?>(null)

    fun clearCache() {
        cache.set(null)
    }

    fun load(context: Context): Map<SfFighterId, SfSpecialPhrase> {
        cache.get()?.let { return it }
        val loaded = runCatching {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()?.let { parse(it) } ?: emptyMap()
        cache.compareAndSet(null, loaded)
        return cache.get() ?: loaded
    }

    fun get(context: Context, id: SfFighterId): SfSpecialPhrase? = load(context)[id]

    private fun parse(raw: String): Map<SfFighterId, SfSpecialPhrase> {
        val root = powJsonObjeto(raw)
        val fighters = root.optJSONObject("fighters") ?: return emptyMap()
        val out = mutableMapOf<SfFighterId, SfSpecialPhrase>()
        for (name in fighters.keys) {
            val id = runCatching { SfFighterId.valueOf(name) }.getOrNull() ?: continue
            val o = fighters.getJSONObject(name)
            val es = o.optString("phrase_es", "")
            if (es.isBlank()) continue
            val en = o.optString("phrase_en", es).ifBlank { es }
            val hud = o.optString("phrase_hud", "").ifBlank { es }
            val audio = o.optString("audio", "special_${id.name.lowercase(Locale.ROOT)}.ogg")
            val ms = o.optLong("subtitle_ms", 2800L).coerceIn(800L, 9000L)
            val tier = o.optString("tier", "generic")
            out[id] = SfSpecialPhrase(
                fighterId = id,
                phraseEs = es,
                phraseEn = en,
                phraseHud = hud,
                audioKey = audio.removeSuffix(".ogg"),
                subtitleMs = ms,
                tier = tier,
            )
        }
        return out
    }
}
