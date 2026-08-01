package ovh.gabrielhuav.pow.features.streetfighter.data

// 🍏 Accesores compatibles de `:shared` en vez de `org.json` (de la JVM, no existe en iOS).
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import ovh.gabrielhuav.pow.data.json.optInt
import ovh.gabrielhuav.pow.data.json.optJSONArray
import ovh.gabrielhuav.pow.data.json.optJSONObject
import ovh.gabrielhuav.pow.data.json.optString
import ovh.gabrielhuav.pow.data.json.powJsonObjeto
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.platform.assets.PowAssets

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
    // 🆕 (2026-07-21) Acciones BÁSICAS que enseña el tutorial paso a paso.
    WALK_FORWARD, RUN, BLOCK_HIGH,
    /** 🆕 FATALITY / poder súper especial: súper EN CARRERA con el medidor lleno. */
    FATALITY,
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
        if (langTag.lowercase().take(2) == "en") nameEn else nameEs

    fun hint(langTag: String): String =
        if (langTag.lowercase().take(2) == "en") hintEn else hintEs
}

/** Catálogo completo: básicos (una acción cada uno), combos universales y firmas. */
private class SfComboData(
    val basics: List<SfCombo>,
    val universal: List<SfCombo>,
    val signatures: Map<SfFighterId, SfCombo>,
)

object SfCombos {
    private const val ASSET = "STREETFIGHTER/DATA/combos.json"
    private var cache: SfComboData? = null

    fun clearCache() {
        cache = null
    }

    /**
     * 🆕 (2026-07-21) LECCIONES BÁSICAS: un movimiento por lección (caminar, agacharse,
     * cada puño, patada, bloqueo, dash, parry, agarre…). Son la primera mitad del tutorial:
     * antes de encadenar combos hay que saber ejecutar cada cosa.
     */
    fun basics(): List<SfCombo> = load().basics

    /** Combos que valen para CUALQUIER peleador, ordenados por dificultad. */
    fun universal(): List<SfCombo> = load().universal

    /** Combo de FIRMA del peleador (null si no tiene uno declarado). */
    fun signature(id: SfFighterId): SfCombo? = load().signatures[id]

    /** Universales + el de firma del peleador (lo que se LISTA en la hoja de combos). */
    fun forFighter(id: SfFighterId): List<SfCombo> =
        universal() + listOfNotNull(signature(id))

    /**
     * Currículum COMPLETO del tutorial: primero los básicos (un movimiento cada uno) y
     * después los combos. Así se aprende "pasito a pasito" antes de encadenar.
     */
    fun curriculum(id: SfFighterId): List<SfCombo> =
        basics() + forFighter(id)

    private fun load(): SfComboData {
        cache?.let { return it }
        val parsed = runCatching { PowAssets.texto(ASSET) }.getOrNull()?.let { parse(it) }
            ?: SfComboData(emptyList(), emptyList(), emptyMap())
        cache = parsed
        return parsed
    }

    private fun steps(raw: JsonArray?): List<SfComboAction> {
        if (raw == null) return emptyList()
        val out = mutableListOf<SfComboAction>()
        for (i in raw.indices) {
            SfComboAction.fromKey(raw.optString(i))?.let(out::add)
        }
        return out
    }

    /** Lee un array de combos ("basics" o "universal") conservando su orden de enseñanza. */
    private fun comboArray(root: JsonObject, key: String): List<SfCombo> {
        val arr = root.optJSONArray(key) ?: return emptyList()
        val out = mutableListOf<SfCombo>()
        for (i in arr.indices) {
            val o = arr.optJSONObject(i) ?: continue
            val actions = steps(o.optJSONArray("steps"))
            if (actions.isEmpty()) continue
            out += SfCombo(
                id = o.optString("id", "${key}_$i"),
                nameEs = o.optString("es", ""),
                nameEn = o.optString("en", o.optString("es", "")),
                hintEs = o.optString("hintEs", ""),
                hintEn = o.optString("hintEn", o.optString("hintEs", "")),
                level = o.optInt("level", 1).coerceIn(0, 4),
                steps = actions,
            )
        }
        return out
    }

    private fun parse(rawJson: String): SfComboData {
        val root = powJsonObjeto(rawJson)
        // Los BÁSICOS conservan el orden del JSON (es el orden pedagógico); los combos se
        // ordenan por dificultad.
        val basics = comboArray(root, "basics")
        val universal = comboArray(root, "universal").sortedBy { it.level }
        val signatures = mutableMapOf<SfFighterId, SfCombo>()
        root.optJSONObject("signature")?.let { obj ->
            for (name in obj.keys) {
                val id = runCatching { SfFighterId.valueOf(name) }.getOrNull() ?: continue
                val o = obj.optJSONObject(name) ?: continue
                val actions = steps(o.optJSONArray("steps"))
                if (actions.isEmpty()) continue
                signatures[id] = SfCombo(
                    id = "signature_${name.lowercase()}",
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
        return SfComboData(basics, universal, signatures)
    }
}
