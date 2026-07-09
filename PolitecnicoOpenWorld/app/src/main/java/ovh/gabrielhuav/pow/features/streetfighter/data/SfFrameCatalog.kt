package ovh.gabrielhuav.pow.features.streetfighter.data

import android.content.Context
import com.google.gson.JsonParser
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAnimFrame
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterData
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFrameDef

// Carga el frame data de los peleadores desde assets/STREETFIGHTER/DATA/{ryu,ken}.json
// (generados 1:1 desde Ryu.js/Ken.js del clon original: recortes del sprite sheet,
// orígenes, pushbox/hurtbox/hitbox por frame y animaciones con sus frame-delays).
// Cache estático: se parsea UNA vez por proceso (los usan el VM y el render).

object SfFrameCatalog {

    private val cache = mutableMapOf<SfFighterId, SfFighterData>()

    @Synchronized
    fun load(context: Context, id: SfFighterId): SfFighterData = cache.getOrPut(id) {
        val json = context.assets.open(id.jsonAsset).bufferedReader().use { it.readText() }
        parse(json)
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
            )
        }

        val animations = mutableMapOf<String, List<SfAnimFrame>>()
        for ((key, value) in root.getAsJsonObject("animations").entrySet()) {
            animations[key] = value.asJsonArray.map { step ->
                val arr = step.asJsonArray
                SfAnimFrame(frameKey = arr[0].asString, delay = arr[1].asInt)
            }
        }

        return SfFighterData(frames = frames, animations = animations)
    }
}
