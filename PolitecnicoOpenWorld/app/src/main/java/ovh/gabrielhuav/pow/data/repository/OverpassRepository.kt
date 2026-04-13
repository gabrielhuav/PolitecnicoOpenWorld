package ovh.gabrielhuav.pow.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ovh.gabrielhuav.pow.domain.models.MapNode
import ovh.gabrielhuav.pow.domain.models.MapWay
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Repositorio que consulta la Overpass API para obtener la red de calles.
 *
 * RESPONSABILIDAD ÚNICA: Solo sabe hablar con Overpass. No decide si hay que
 * descargar o no — esa decisión la toma WorldMapViewModel usando RoadNetworkCache.
 *
 * CAMBIOS RESPECTO A LA VERSIÓN ANTERIOR:
 * - Radio ampliado de 600m → 2000m: descargamos más en cada petición, pero
 *   hacemos muchas menos peticiones (el jugador puede caminar 1km antes de
 *   necesitar un nuevo fetch).
 * - Timeout de lectura ampliado a 45s (queries de 2km pueden ser más pesadas).
 * - Mejor manejo de errores HTTP con logging detallado.
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
    }

    /**
     * Descarga la red de calles alrededor de (lat, lon) desde Overpass.
     * Retorna lista vacía en caso de cualquier error — el caller decide qué hacer.
     */
    suspend fun fetchRoadNetwork(lat: Double, lon: Double): List<MapWay> =
        withContext(Dispatchers.IO) {

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

            val urlString = "https://overpass-api.de/api/interpreter?data=${
                URLEncoder.encode(query, "UTF-8")
            }"

            var connection: HttpURLConnection? = null
            return@withContext try {
                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 45_000       // 45s para queries más grandes
                    setRequestProperty(
                        "User-Agent",
                        "PolitecnicoOpenWorld/1.0 (Android; educational game; contact: dev@pow.ovh)"
                    )
                }

                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val parsed = parseOverpassJson(response)
                        Log.d(TAG, "Descarga OK: ${parsed.size} ways en radio ${FETCH_RADIUS_METERS}m")
                        parsed
                    }
                    429 -> {
                        // Too Many Requests — Overpass nos está limitando
                        Log.w(TAG, "Rate limited por Overpass (429). Usar caché.")
                        emptyList()
                    }
                    504 -> {
                        // Gateway Timeout — el servidor está ocupado
                        Log.w(TAG, "Overpass timeout en servidor (504).")
                        emptyList()
                    }
                    else -> {
                        Log.e(TAG, "HTTP $code inesperado")
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error de red: ${e.javaClass.simpleName}: ${e.message}")
                emptyList()
            } finally {
                connection?.disconnect()
            }
        }

    // ─── PARSING ─────────────────────────────────────────────────────────────────

    private fun parseOverpassJson(jsonString: String): List<MapWay> {
        val root = JSONObject(jsonString)
        if (!root.has("elements")) return emptyList()

        val elements = root.getJSONArray("elements")
        val elementCount = elements.length()

        // Pre-allocamos los mapas con capacidad estimada para evitar rehashing
        val nodesMap = HashMap<Long, MapNode>(elementCount)
        val ways = ArrayList<MapWay>(elementCount / 3) // Las ways son ~1/3 de los elementos

        // Pasada 1: indexar todos los nodos
        for (i in 0 until elementCount) {
            val element = elements.getJSONObject(i)
            if (element.getString("type") == "node") {
                val id = element.getLong("id")
                nodesMap[id] = MapNode(
                    id = id,
                    lat = element.getDouble("lat"),
                    lon = element.getDouble("lon")
                )
            }
        }

        // Pasada 2: construir los ways usando los nodos indexados
        for (i in 0 until elementCount) {
            val element = elements.getJSONObject(i)
            if (element.getString("type") != "way") continue

            val tags = element.optJSONObject("tags") ?: continue
            val highway = tags.optString("highway", "") .ifEmpty { continue }

            val isForCars = highway.matches(CAR_REGEX)
            val isForPeople = highway.matches(PEOPLE_REGEX)
            if (!isForCars && !isForPeople) continue

            val nodesArray = element.getJSONArray("nodes")
            val wayNodes = ArrayList<MapNode>(nodesArray.length())
            for (j in 0 until nodesArray.length()) {
                nodesMap[nodesArray.getLong(j)]?.let { wayNodes.add(it) }
            }

            if (wayNodes.size > 1) {
                ways.add(MapWay(element.getLong("id"), wayNodes, isForCars, isForPeople))
            }
        }

        return ways
    }
}