package ovh.gabrielhuav.pow.data.json

import com.google.gson.Gson
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Test
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetFireball

/**
 * 🍏 COMPATIBILIDAD DEL FORMATO DE CABLE — red de seguridad de la Fase 3
 * (`README for IAS/PLAN_MIGRACION_KMP.md`).
 *
 * POR QUÉ: los mensajes de red los leen **clientes que YA están instalados** (1.0.0.14) y los
 * **servidores Node** (`Multiplayer/`, `MultiplayerInteriores/`, `MultiplayerSF/`). Al cambiar Gson
 * por `jsonOf`, el JSON tiene que salir **idéntico**; si no, se rompe el multijugador de la gente
 * que está jugando ahora mismo, y no hay compilador que avise.
 *
 * Cada test compara `jsonOf(...)` contra `gson.toJson(mapOf(...))` con las MISMAS parejas, usando
 * payloads reales copiados de los sitios de envío del juego.
 *
 * ⚠️ Este test es el que autoriza a tocar la capa de red. No lo borres mientras Gson siga en el
 * proyecto y, sobre todo, mientras haya jugadores en versiones antiguas.
 */
class JsonObjectCompatGsonTest {

    private val gson = Gson()

    private fun comparar(vararg pares: Pair<String, Any?>) {
        val deGson = gson.toJson(mapOf(*pares))
        val deJsonOf = jsonOf(*pares)
        assertEquals(deGson, deJsonOf)
    }

    @Test
    fun `NPC_DESTROY y POLICE_DESTROY (solo strings)`() {
        comparar("type" to "NPC_DESTROY", "npcId" to "npc-abc-123")
        comparar("type" to "POLICE_DESTROY", "npcId" to "pol-9")
    }

    @Test
    fun `PLAYER_DAMAGE (string + entero)`() {
        comparar("type" to "PLAYER_DAMAGE", "targetId" to "npc-1", "damage" to 25)
    }

    @Test
    fun `ZOMBIE_DAMAGE e ITEM_PICKUP`() {
        comparar("type" to "ZOMBIE_DAMAGE", "zombieId" to "z-7", "damage" to 34)
        comparar("type" to "ITEM_PICKUP", "itemId" to "key_lab1")
    }

    @Test
    fun `JOIN_ROOM y PLAYER_UPDATE de zombis (dobles, flotantes y booleanos)`() {
        comparar(
            "type" to "JOIN_ROOM", "roomId" to "za_auditorio", "displayName" to "Lázaro",
            "mode" to "COOP", "x" to 0.4312, "y" to 0.7778,
        )
        comparar(
            "type" to "PLAYER_UPDATE", "displayName" to "Lázaro", "mode" to "COOP",
            "x" to 0.5, "y" to 0.25, "action" to "WALK",
            "facingRight" to true, "health" to 87.5f,
        )
    }

    @Test
    fun `descubrimiento LAN de SF`() {
        comparar("app" to "POW_SF", "name" to "Motorola de Gabriel")
    }

    @Test
    fun `los NULOS se OMITEN, igual que hace Gson (no se escriben como null)`() {
        // Es el detalle que rompería el protocolo de forma más silenciosa: un cliente viejo
        // distingue "clave ausente" de "clave con null".
        comparar("type" to "PLAYER_STATE", "character" to null, "hp" to 100)
        assertEquals("""{"type":"X"}""", jsonOf("type" to "X", "vacio" to null))
    }

    @Test
    fun `numeros enteros, dobles y flotantes salen con el MISMO texto que Gson`() {
        // Gson y kotlinx podrían discrepar al imprimir floats/doubles: aquí se fija que no.
        comparar("i" to 0, "j" to -42, "k" to 2147483647)
        comparar("d" to 0.0, "e" to 19.504603, "f" to -99.145985)
        comparar("g" to 1.5f, "h" to 0.1f, "z" to 100f)
        comparar("neg" to -0.000123, "big" to 1.7976931348623157E308)
    }

    @Test
    fun `booleanos y cadenas con acentos, comillas y barras`() {
        comparar("t" to true, "f" to false)
        comparar("nombre" to "Señor de la tienda", "raro" to "com\"illas\\y barras/")
    }

    @Test
    fun `PLAYER_STATE con fireballs conserva el formato Gson`() {
        val fireballs = listOf(
            SfNetFireball(
                x = 321.5f,
                y = 144.25f,
                dir = -1,
                strength = "HEAVY",
                state = "FLYING",
                frame = 3,
            ),
        )
        val deGson = gson.toJson(
            mapOf(
                "type" to "PLAYER_STATE",
                "fireballs" to fireballs,
                "meter" to 60,
            ),
        )
        val deJsonOf = jsonOf(
            "type" to "PLAYER_STATE",
            "fireballs" to PowJson.encodeToJsonElement(fireballs),
            "meter" to 60,
        )

        assertEquals(deGson, deJsonOf)
    }
}
