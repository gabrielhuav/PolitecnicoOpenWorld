package ovh.gabrielhuav.pow.features.streetfighter.data

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ovh.gabrielhuav.pow.data.json.PowJson
import ovh.gabrielhuav.pow.platform.assets.PowAssets
import ovh.gabrielhuav.pow.platform.concurrencia.PowCerrojo
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

    // 🍏 Fase 5: sustituye a `@Synchronized`, que solo existe en la JVM. REENTRANTE porque `load`
    // llama a `template()` y las dos cierran sobre este mismo cerrojo.
    private val cerrojo = PowCerrojo()

    fun load(id: SfFighterId): SfFighterData = cerrojo.ejecutar {
        cache.getOrPut(id) {
            if (id.sharedSet != null) {
                remapToRuntimeGrid(template())
            } else {
                parse(PowAssets.texto(id.jsonAsset))
            }
        }
    }

    /** Claves del template en su orden (define el layout de la hoja runtime compartida). */
    fun templateFrameOrder(): List<String> = cerrojo.ejecutar { template().frames.keys.toList() }

    /** Template de cajas/timings de los COMPARTIDOS (NO depende de ryu.json: release-safe). */
    private fun template(): SfFighterData = cerrojo.ejecutar {
        templateCache ?: parse(PowAssets.texto(TEMPLATE_ASSET)).also { templateCache = it }
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
        val root = PowJson.parseToJsonElement(json).jsonObject

        val frames = mutableMapOf<String, SfFrameDef>()
        for ((key, value) in root.getValue("frames").jsonObject) {
            val o = value.jsonObject
            frames[key] = SfFrameDef(
                src = o.getValue("src").jsonArray.map { it.jsonPrimitive.int },
                origin = o.getValue("origin").jsonArray.map { it.jsonPrimitive.int },
                push = o["push"]?.jsonArray?.map { it.jsonPrimitive.int },
                hurt = o["hurt"]?.jsonArray?.map { row -> row.jsonArray.map { it.jsonPrimitive.int } },
                hit = o["hit"]?.jsonArray?.map { it.jsonPrimitive.int },
                flipX = o["flipX"]?.jsonPrimitive?.boolean ?: false,
            )
        }

        val animations = mutableMapOf<String, List<SfAnimFrame>>()
        for ((key, value) in root.getValue("animations").jsonObject) {
            animations[key] = value.jsonArray.map { step ->
                val arr = step.jsonArray
                SfAnimFrame(frameKey = arr[0].jsonPrimitive.content, delay = arr[1].jsonPrimitive.int)
            }
        }

        // 🆕 (2026-07-22) MAREO: los 18 JSON (y el template) traen los CUADROS stun-1/2/3
        // pero NINGUNO trae la ANIMACIÓN "stun" → se SINTETIZA aquí para el estado STUN
        // sin re-empacar nada. (Hoy los 3 cuadros son la misma pose inclinada; si algún día
        // el packer emite "stun" propia, esta síntesis se salta sola.)
        if ("stun" !in animations) {
            val stunFrames = listOf("stun-1", "stun-2", "stun-3")
                .filter { frames.containsKey(it) }
                .map { SfAnimFrame(frameKey = it, delay = 8) }
            if (stunFrames.isNotEmpty()) animations["stun"] = stunFrames
        }

        val projectileEvents = mutableMapOf<SfAttackStrength, SfProjectileEvent>()
        val projectileRoot = root["events"]?.jsonObject?.get("projectile")?.jsonObject
        if (projectileRoot != null) {
            for (strength in SfAttackStrength.entries) {
                val key = strength.name.lowercase()
                val event = projectileRoot[key]?.jsonObject ?: continue
                val offset = event["offset"]?.jsonArray
                projectileEvents[strength] = SfProjectileEvent(
                    animationFrame = event["frame"]?.jsonPrimitive?.int ?: 3,
                    offsetX = if (offset != null && offset.size > 0) offset[0].jsonPrimitive.float else 76f,
                    offsetY = if (offset != null && offset.size > 1) offset[1].jsonPrimitive.float else -57f,
                    visualScale = event["scale"]?.jsonPrimitive?.float ?: 1f,
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
