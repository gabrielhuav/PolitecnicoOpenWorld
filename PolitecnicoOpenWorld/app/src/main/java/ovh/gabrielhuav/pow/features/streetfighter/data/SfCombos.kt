package ovh.gabrielhuav.pow.features.streetfighter.data

import android.content.Context
import org.json.JSONObject
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * 🆕 (2026-07-21) CATÁLOGO DE COMBOS de HUELUM VS. GOYA.
 *
 * Fuente: `assets/STREETFIGHTER/DATA/combos.json` (data-driven: ampliar combos NO exige
 * tocar código). Lo consumen dos sitios:
 *  - el **TUTORIAL interactivo**: cada combo es una lección; sus `steps` se validan uno a uno.
 *  - la **IA**: encola los `steps` de una ruta y los ejecuta en orden (ver `cpuComboQueue`).
 *
 * Hay combos **universales** (todos los peleadores comparten el moveset) y uno de **firma**
 * por personaje, que remata con su especial o su súper.
 */
enum class SfComboAction {
    LIGHT_PUNCH, MEDIUM_PUNCH, HEAVY_PUNCH,
    LIGHT_KICK, MEDIUM_KICK, HEAVY_KICK,
    CROUCH_PUNCH, CROUCH_KICK, CROUCH_HEAVY_PUNCH, SWEEP,
    LONG_KICK, OVERHEAD,
    AIR_PUNCH, AIR_KICK,
    DASH_FORWARD, DASH_BACKWARD,
    PARRY, GRAB, TAUNT,
    SPECIAL, SUPER_ART,
    JUMP, CROUCH,
    ;

    companion object {
        /** Nombre del JSON (camelCase) → acción. null si el JSON trae algo desconocido. */
        fun fromKey(key: String): SfComboAction? = entries.firstOrNull {
            it.name.replace("_", "").equals(key.replace("_", ""), ignoreCase = true)
        }
    }
}

data class SfCombo(
    val id: String,
    val nameEs: String,
    val nameEn: String,
    val hintEs: String,
    val hintEn: String,
    /** 1 = básico … 4 = avanzado. El tutorial las presenta en este orden. */
    val level: Int,
    val steps: List<SfComboAction>,
    /** null = universal; si no, el peleador dueño de esta ruta de firma. */
    val ownerId: SfFighterId? = null,
) {
    fun name(langTag: String): String =
        if (langTag.lowercase(Locale.ROOT).take(2) == "en") nameEn else nameEs

    fun hint(langTag: String): String =
        if (langTag.lowercase(Locale.ROOT).take(2) == "en") hintEn else hintEs
}

object SfCombos {
    private const val ASSET = "STREETFIGHTER/DATA/combos.json"
    private val cache = AtomicReference<Pair<List<SfCombo>, Map<SfFighterId, SfCombo>>?>(null)

    fun clearCache() {
        cache.set(null)
    }

    /** Combos que valen para CUALQUIER peleador, ordenados por dificultad. */
    fun universal(context: Context): List<SfCombo> = load(context).first

    /** Combo de FIRMA del peleador (null si no tiene uno declarado). */
    fun signature(context: Context, id: SfFighterId): SfCombo? = load(context).second[id]

    /** Universales + el de firma del peleador, en el orden en que los enseña el tutorial. */
    fun forFighter(context: Context, id: SfFighterId): List<SfCombo> =
        universal(context) + listOfNotNull(signature(context, id))

    private fun load(context: Context): Pair<List<SfCombo>, Map<SfFighterId, SfCombo>> {
        cache.get()?.let { return it }
        val parsed = runCatching {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()?.let { parse(it) } ?: (emptyList<SfCombo>() to emptyMap())
        cache.compareAndSet(null, parsed)
        return cache.get() ?: parsed
    }

    private fun steps(raw: org.json.JSONArray?): List<SfComboAction> {
        if (raw == null) return emptyList()
        val out = mutableListOf<SfComboAction>()
        for (i in 0 until raw.length()) {
            SfComboAction.fromKey(raw.optString(i))?.let(out::add)
        }
        return out
    }

    private fun parse(rawJson: String): Pair<List<SfCombo>, Map<SfFighterId, SfCombo>> {
        val root = JSONObject(rawJson)
        val universal = mutableListOf<SfCombo>()
        root.optJSONArray("universal")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val actions = steps(o.optJSONArray("steps"))
                if (actions.isEmpty()) continue
                universal += SfCombo(
                    id = o.optString("id", "combo_$i"),
                    nameEs = o.optString("es", ""),
                    nameEn = o.optString("en", o.optString("es", "")),
                    hintEs = o.optString("hintEs", ""),
                    hintEn = o.optString("hintEn", o.optString("hintEs", "")),
                    level = o.optInt("level", 1).coerceIn(1, 4),
                    steps = actions,
                )
            }
        }
        val signatures = mutableMapOf<SfFighterId, SfCombo>()
        root.optJSONObject("signature")?.let { obj ->
            val keys = obj.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val id = runCatching { SfFighterId.valueOf(name) }.getOrNull() ?: continue
                val o = obj.optJSONObject(name) ?: continue
                val actions = steps(o.optJSONArray("steps"))
                if (actions.isEmpty()) continue
                signatures[id] = SfCombo(
                    id = "signature_${name.lowercase(Locale.ROOT)}",
                    nameEs = o.optString("es", ""),
                    nameEn = o.optString("en", o.optString("es", "")),
                    hintEs = o.optString("hintEs", ""),
                    hintEn = o.optString("hintEn", o.optString("hintEs", "")),
                    level = 4,
                    steps = actions,
                    ownerId = id,
                )
            }
        }
        return universal.sortedBy { it.level } to signatures
    }
}
