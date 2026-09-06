package ovh.gabrielhuav.pow.data.json

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import ovh.gabrielhuav.pow.features.map_exterior.ui.LandmarkWebPayload
import ovh.gabrielhuav.pow.features.map_exterior.ui.NpcWebPayload

/**
 * 🍏 REGRESIÓN DE LA FASE 3 — "serializar de verdad, no solo compilar".
 *
 * ESTE TEST NACE DE UN BUG REAL, y del más peligroso de toda la migración:
 * **`PowJson.encodeToString(x)` COMPILA aunque `x` no se pueda serializar, y revienta en RUNTIME**
 * con `SerializationException: Serializer for class 'X' is not found`. Pasa con `Map<String, Any?>`
 * (heterogéneo) y con cualquier `data class` a la que se le olvidó el `@Serializable`.
 *
 * La auditoría encontró **9 sitios así** tras migrar de Gson:
 *  - los 3 transportes de "Titulación por Combate" (online / LAN+BT / WebRTC) → habría reventado
 *    **todo el multijugador** en el primer mensaje enviado;
 *  - 6 payloads del **mapa WEB**, que es el renderer POR DEFECTO del juego (`CARTO_VOYAGER`) →
 *    NPCs, metro, metrobús, policía, zombis y calles.
 *
 * Nada de eso lo detecta el compilador ni los tests que había: solo se ve al ejecutar.
 * Por eso este test **serializa de verdad** cada payload.
 *
 * ⚠️ Regla: si añades un payload que se envíe al WebView o por red, **añádelo aquí**.
 * Si es un mapa heterogéneo, no uses `encodeToString`: usa `jsonOf`/`jsonArrayOf`.
 */
class PayloadsSeSerializanEnRuntimeTest {

    @Test
    fun `los data class de payload TIENEN serializador`() {
        assertTrue(PowJson.encodeToString(listOf(NpcWebPayload("a", 1.0, 2.0, 0f, "CAR"))).isNotEmpty())
        assertTrue(
            PowJson.encodeToString(
                listOf(LandmarkWebPayload("a", 1.0, 2.0, 0f, 1f, 1f, 1f, 1f, 1f, "x")),
            ).isNotEmpty(),
        )
        assertTrue(
            PowJson.encodeToString(
                listOf(ActiveCollectible("id", "n", "d", "a", 1.0, 2.0)),
            ).isNotEmpty(),
        )
    }

    @Test
    fun `jsonOf y jsonArrayOf aguantan lo HETEROGENEO (que es donde peta encodeToString)`() {
        // Exactamente la forma de los payloads del mapa web y de los mensajes de red.
        val unNpc = mapOf("id" to "x", "lat" to 19.5, "lng" to -99.1, "emoji" to "👮", "vivo" to true)
        assertTrue(jsonArrayOf(listOf(unNpc)).startsWith("[{"))
        assertTrue(jsonOf(unNpc).startsWith("{"))
        // Anidado (roadsPayload manda una lista de nodos dentro de cada vía).
        val unaVia = mapOf("id" to "1", "nodes" to listOf(mapOf("lat" to 1.0, "lon" to 2.0)))
        assertTrue(jsonArrayOf(listOf(unaVia)).contains("\"nodes\":[{"))
    }

    @Test
    fun `encodeToString sobre un mapa heterogeneo SIGUE petando (por eso existen los helpers)`() {
        // Se fija el comportamiento para que nadie "simplifique" los helpers a encodeToString.
        val heterogeneo: Map<String, Any?> = mapOf("s" to "a", "i" to 1)
        val r = runCatching { PowJson.encodeToString(heterogeneo) }
        assertTrue(
            "encodeToString sobre Map<String,Any?> deberia fallar; si ya no falla, revisa si los " +
                "helpers jsonOf/jsonArrayOf siguen siendo necesarios",
            r.isFailure,
        )
    }
}
