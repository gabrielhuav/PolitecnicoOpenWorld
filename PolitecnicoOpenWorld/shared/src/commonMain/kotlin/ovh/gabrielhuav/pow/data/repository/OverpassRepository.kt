package ovh.gabrielhuav.pow.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.data.json.optJSONArray
import ovh.gabrielhuav.pow.data.json.optLong
import ovh.gabrielhuav.pow.data.json.optDouble
import ovh.gabrielhuav.pow.data.json.has
import ovh.gabrielhuav.pow.data.json.optJSONObject
import ovh.gabrielhuav.pow.data.json.optString
import ovh.gabrielhuav.pow.data.json.powJsonObjeto
import ovh.gabrielhuav.pow.domain.models.map.MapNode
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.platform.log.powLog

/**
 * Repositorio que consulta la Overpass API para obtener la red de calles.
 *
 * RESPONSABILIDAD ÚNICA: Solo sabe hablar con Overpass. No decide si hay que
 * descargar o no — esa decisión la toma WorldMapViewModel usando RoadNetworkCache.
 *
 * Radio de 2000 m y timeout de lectura de 45 s: se descarga más por petición para hacer muchas
 * menos (el jugador puede caminar 1 km antes de necesitar otra).
 *
 * 🍏 **Multiplataforma desde el 2026-08-15.** Tres cambios y ninguno toca el protocolo:
 *  - `HttpURLConnection` → **Ktor**, que en Android usa OkHttp y en iOS Darwin.
 *  - `org.json` → `PowJson`, cuyos helpers imitan la API de `org.json` a propósito, así que el
 *    parseo es el mismo código con otros imports.
 *  - ⚠️ `Dispatchers.IO` **no existe en Kotlin/Native** → `Dispatchers.Default` (09 §8 nº4).
 *    Para una petición HTTP con suspensión de verdad esto no bloquea nada: Ktor suspende, no
 *    ocupa el hilo mientras espera.
 */
class OverpassRepository {

    private val TAG = "OverpassRepository"

    companion object {
        // Radio de descarga. 2000m cubre ~12km² alrededor del jugador.
        // Con una celda de caché de 2km, esto garantiza que los bordes de la celda
        // también queden cubiertos aunque el jugador esté en una esquina.
        private const val FETCH_RADIUS_METERS = 2000

        // Regex pre-compiladas una sola vez para toda la vida del proceso
        private val CAR_REGEX =
            Regex("^(primary|secondary|tertiary|residential|unclassified|service|living_street)$")
        private val PEOPLE_REGEX =
            Regex("^(footway|pedestrian|path|residential|living_street|service)$")

        /**
         * Un solo cliente para toda la vida del proceso. Crear un `HttpClient` por petición
         * levanta (y tira) un pool de conexiones cada vez.
         */
        private val http by lazy {
            HttpClient {
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000L
                    // 45 s: las consultas de 2 km pueden tardar de verdad.
                    requestTimeoutMillis = 45_000L
                }
            }
        }
    }

    /**
     * Descarga la red de calles alrededor de (lat, lon) desde Overpass.
     * Retorna lista vacía en caso de cualquier error — el caller decide qué hacer.
     */
    suspend fun fetchRoadNetwork(lat: Double, lon: Double): List<MapWay> =
        withContext(Dispatchers.Default) {

            // La query pide ways de los tipos que nos interesan + sus nodos (> out skel qt)
            // [timeout:30] es el timeout del servidor, distinto al timeout de conexión del cliente
            val query = """
            [out:json][timeout:30];
            (
              way["highway"~"^(primary|secondary|tertiary|residential|unclassified|footway|pedestrian|path|living_street|service)${'$'}"](around:$FETCH_RADIUS_METERS,$lat,$lon);
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()

            val urlString = "https://overpass-api.de/api/interpreter?data=${codificarUrl(query)}"

            return@withContext try {
                val respuesta = http.get(urlString) {
                    header(
                        "User-Agent",
                        "PolitecnicoOpenWorld/1.0 (educational game; contact: dev@pow.ovh)"
                    )
                }
                when {
                    respuesta.status.isSuccess() -> {
                        val parsed = parseOverpassJson(respuesta.bodyAsText())
                        powLog(TAG, "Descarga OK: ${parsed.size} ways en radio ${FETCH_RADIUS_METERS}m")
                        parsed
                    }
                    // Too Many Requests — Overpass nos está limitando
                    respuesta.status.value == 429 -> {
                        powLog(TAG, "Rate limited por Overpass (429). Usar caché.")
                        emptyList()
                    }
                    // Gateway Timeout — el servidor está ocupado
                    respuesta.status.value == 504 -> {
                        powLog(TAG, "Overpass timeout en servidor (504).")
                        emptyList()
                    }
                    else -> {
                        powLog(TAG, "HTTP ${respuesta.status.value} inesperado")
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                powLog(TAG, "Error de red: ${e.message}")
                emptyList()
            }
        }

    /**
     * Percent-encoding de la consulta.
     *
     * ⚠️ Se escribe a mano porque `java.net.URLEncoder` no existe en Kotlin/Native. **No vale
     * `encodeURLParameter` de Ktor**: eso codifica el espacio como `+`, y la consulta de Overpass
     * lleva saltos de línea y `+` sería ambiguo. Aquí todo lo que no sea seguro va a `%XX`, que es
     * lo que hacía `URLEncoder` salvo por el espacio (que aquí sale `%20`, igual de válido).
     */
    private fun codificarUrl(texto: String): String = buildString {
        for (byte in texto.encodeToByteArray()) {
            val c = byte.toInt().toChar()
            if (c.isLetterOrDigit() && c.code < 128 || c in "-_.~") {
                append(c)
            } else {
                append('%').append((byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
            }
        }
    }

    // ─── PARSING ─────────────────────────────────────────────────────────────────

    private fun parseOverpassJson(jsonString: String): List<MapWay> {
        val root = powJsonObjeto(jsonString)
        if (!root.has("elements")) return emptyList()

        val elements = root.optJSONArray("elements") ?: return emptyList()
        val elementCount = elements.size

        val nodesMap = HashMap<Long, MapNode>(elementCount)
        val ways = ArrayList<MapWay>(elementCount / 3) // Las ways son ~1/3 de los elementos

        // Pasada 1: indexar todos los nodos
        for (i in 0 until elementCount) {
            val element = elements.optJSONObject(i) ?: continue
            if (element.optString("type") == "node") {
                val id = element.optLong("id")
                nodesMap[id] = MapNode(
                    id = id,
                    lat = element.optDouble("lat"),
                    lon = element.optDouble("lon")
                )
            }
        }

        // Pasada 2: construir los ways usando los nodos indexados
        for (i in 0 until elementCount) {
            val element = elements.optJSONObject(i) ?: continue
            if (element.optString("type") != "way") continue

            val tags = element.optJSONObject("tags") ?: continue
            val highway = tags.optString("highway", "")
            if (highway.isEmpty()) continue

            val isForCars = highway.matches(CAR_REGEX)
            val isForPeople = highway.matches(PEOPLE_REGEX)
            if (!isForCars && !isForPeople) continue

            val nodesArray = element.optJSONArray("nodes") ?: continue
            val wayNodes = ArrayList<MapNode>(nodesArray.size)
            for (j in 0 until nodesArray.size) {
                nodesMap[nodesArray.optLong(j)]?.let { wayNodes.add(it) }
            }

            if (wayNodes.size > 1) {
                ways.add(MapWay(element.optLong("id"), wayNodes, isForCars, isForPeople))
            }
        }

        return ways
    }
}
