package ovh.gabrielhuav.pow.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ovh.gabrielhuav.pow.domain.models.MapNode
import ovh.gabrielhuav.pow.domain.models.MapWay
import java.net.HttpURLConnection
import java.net.URL

class OverpassRepository {

    private val TAG = "OverpassRepository"

    suspend fun fetchRoadNetwork(lat: Double, lon: Double): List<MapWay> = withContext(Dispatchers.IO) {
        val radius = 600

        val query = """
            [out:json][timeout:25];
            (
              way["highway"~"^(primary|secondary|tertiary|residential|unclassified|footway|pedestrian|path|living_street|service)$"](around:$radius,$lat,$lon);
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()

        val urlString = "https://overpass-api.de/api/interpreter?data=${
            java.net.URLEncoder.encode(query, "UTF-8")
        }"

        var connection: HttpURLConnection? = null
        return@withContext try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            // OPTIMIZACIÓN COPILOT: Timeouts y User-Agent para evitar cuelgues y bloqueos
            connection.connectTimeout = 15000 // 15 segundos de límite para conectar
            connection.readTimeout = 15000    // 15 segundos de límite para descargar
            connection.setRequestProperty("User-Agent", "PolitecnicoOpenWorld/1.0 (Android Game)")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed = parseOverpassJson(response)
                Log.d(TAG, "OK: ${parsed.size} ways descargados")
                parsed
            } else {
                Log.e(TAG, "HTTP Error: ${connection.responseCode}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseOverpassJson(jsonString: String): List<MapWay> {
        val ways = mutableListOf<MapWay>()
        val nodesMap = mutableMapOf<Long, MapNode>()
        val root = JSONObject(jsonString)
        if (!root.has("elements")) return emptyList()

        val elements = root.getJSONArray("elements")
        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)
            if (element.getString("type") == "node") {
                val id  = element.getLong("id")
                val lat = element.getDouble("lat")
                val lon = element.getDouble("lon")
                nodesMap[id] = MapNode(id, lat, lon)
            }
        }

        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)
            if (element.getString("type") == "way") {
                val tags    = if (element.has("tags")) element.getJSONObject("tags") else continue
                val highway = if (tags.has("highway")) tags.getString("highway") else continue

                val isForCars   = highway.matches("(primary|secondary|tertiary|residential|unclassified|service|living_street)".toRegex())
                val isForPeople = highway.matches("(footway|pedestrian|path|residential|living_street|service)".toRegex())

                if (!isForCars && !isForPeople) continue

                val nodesArray = element.getJSONArray("nodes")
                val wayNodes   = mutableListOf<MapNode>()
                for (j in 0 until nodesArray.length()) {
                    nodesMap[nodesArray.getLong(j)]?.let { wayNodes.add(it) }
                }

                if (wayNodes.size > 1) {
                    ways.add(MapWay(element.getLong("id"), wayNodes, isForCars, isForPeople))
                }
            }
        }
        return ways
    }
}