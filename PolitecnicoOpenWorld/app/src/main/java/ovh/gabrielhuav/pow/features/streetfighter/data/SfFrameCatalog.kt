package ovh.gabrielhuav.pow.features.streetfighter.data

import android.content.Context
import com.google.gson.JsonParser
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAnimFrame
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterData
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFrameDef
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfProjectileEvent

// Carga el frame data de los peleadores desde assets/STREETFIGHTER/DATA/*.json
// (generados 1:1 desde Ryu.js/Ken.js del clon original: recortes del sprite sheet,
// orígenes, pushbox/hurtbox/hitbox por frame y animaciones con sus frame-delays).
// Cache estático: se parsea UNA vez por proceso (los usan el VM y el render).
//
// 🆕 PELEADORES COMPARTIDOS (id.sharedSet != null): su jsonAsset es el TEMPLATE
// sf_template.json (copia de las cajas/timings de ryu.json que SÍ viaja en release —
// ryu.json/ken.json viven solo en el source set DEBUG por copyright); se conservan
// cajas/timings pero el `src` se REMAPEA a la rejilla 10×N de celdas 256² (origin 128,224)
// que SfSharedSheets arma en runtime desde los sprites del mundo. El orden de celdas =
// orden de claves del template (idéntico a pack_sf_character.py). ⚠️ NO reordenar sus claves.

object SfFrameCatalog {

    private const val CELL = 256
    private const val COLS = 10
    private const val TEMPLATE_ASSET = "STREETFIGHTER/DATA/sf_template.json"

    private val cache = mutableMapOf<SfFighterId, SfFighterData>()
    private var templateCache: SfFighterData? = null

    @Synchronized
    fun load(context: Context, id: SfFighterId): SfFighterData = cache.getOrPut(id) {
        if (id.sharedSet != null) {
            remapToRuntimeGrid(template(context))
        } else {
            val json = context.assets.open(id.jsonAsset).bufferedReader().use { it.readText() }
            parse(json)
        }
    }

    /** Claves del template en su orden (define el layout de la hoja runtime compartida). */
    @Synchronized
    fun templateFrameOrder(context: Context): List<String> =
        template(context).frames.keys.toList()

    /** Template de cajas/timings de los COMPARTIDOS (NO depende de ryu.json: release-safe). */
    @Synchronized
    private fun template(context: Context): SfFighterData = templateCache ?: run {
        val json = context.assets.open(TEMPLATE_ASSET).bufferedReader().use { it.readText() }
        parse(json).also { templateCache = it }
    }

    /** Mismos frames/cajas/animaciones, pero con src = rejilla runtime y pies en (128,224). */
    private fun remapToRuntimeGrid(template: SfFighterData): SfFighterData {
        val frames = LinkedHashMap<String, SfFrameDef>()
        template.frames.entries.forEachIndexed { idx, (key, def) ->
            frames[key] = SfFrameDef(
                src = listOf((idx % COLS) * CELL, (idx / COLS) * CELL, CELL, CELL),
                origin = listOf(128, 224),
                push = def.push,
                hurt = def.hurt,
                hit = def.hit,
                flipX = def.flipX,
            )
        }
        return SfFighterData(
            frames = frames,
            animations = template.animations,
            projectileEvents = template.projectileEvents,
        )
    }

    private fun parse(json: String): SfFighterData {
        val root = JsonParser.parseString(json).asJsonObject

        val frames = mutableMapOf<String, SfFrameDef>()
        for ((key, value) in root.getAsJsonObject("frames").entrySet()) {
            val o = value.asJsonObject
            frames[key] = SfFrameDef(
                src = o.getAsJsonArray("src").map { it.asInt },
                origin = o.getAsJsonArray("origin").map { it.asInt },
                push = o.getAsJsonArray("push")?.map { it.asInt },
                hurt = o.getAsJsonArray("hurt")?.map { row -> row.asJsonArray.map { it.asInt } },
                hit = o.getAsJsonArray("hit")?.map { it.asInt },
                flipX = o.get("flipX")?.asBoolean ?: false,
            )
        }

        val animations = mutableMapOf<String, List<SfAnimFrame>>()
        for ((key, value) in root.getAsJsonObject("animations").entrySet()) {
            animations[key] = value.asJsonArray.map { step ->
                val arr = step.asJsonArray
                SfAnimFrame(frameKey = arr[0].asString, delay = arr[1].asInt)
            }
        }

        val projectileEvents = mutableMapOf<SfAttackStrength, SfProjectileEvent>()
        val projectileRoot = root.getAsJsonObject("events")?.getAsJsonObject("projectile")
        if (projectileRoot != null) {
            for (strength in SfAttackStrength.entries) {
                val key = strength.name.lowercase()
                val event = projectileRoot.getAsJsonObject(key) ?: continue
                val offset = event.getAsJsonArray("offset")
                projectileEvents[strength] = SfProjectileEvent(
                    animationFrame = event.get("frame")?.asInt ?: 3,
                    offsetX = if (offset != null && offset.size() > 0) offset[0].asFloat else 76f,
                    offsetY = if (offset != null && offset.size() > 1) offset[1].asFloat else -57f,
                    visualScale = event.get("scale")?.asFloat ?: 1f,
                )
            }
        }

        return SfFighterData(
            frames = frames,
            animations = animations,
            projectileEvents = projectileEvents,
        )
    }
}
