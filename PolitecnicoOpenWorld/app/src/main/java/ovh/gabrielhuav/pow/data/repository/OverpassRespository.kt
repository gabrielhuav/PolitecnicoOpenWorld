package ovh.gabrielhuav.pow.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ovh.gabrielhuav.pow.domain.models.MapNode
import ovh.gabrielhuav.pow.domain.models.MapWay
import java.net.HttpURLConnection
import java.net.URL

class OverpassRepository {

    /**
     * Descarga las calles y banquetas en un radio de 800 metros.
     * Se ejecuta en un hilo secundario para no bloquear la UI.
     */
    suspend fun fetchRoadNetwork(lat: Double, lon: Double): List<MapWay> = withContext(Dispatchers.IO) {
        val radius = 800
        // Query de Overpass API: Busca caminos (ways) que tengan la etiqueta 'highway'
        val query = """
            [out:json][timeout:10];
            (
              way["highway"](around:$radius, $lat, $lon);
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()

        val urlString = "https://overpass-api.de/api/interpreter?data=${java.net.URLEncoder.encode(query, "UTF-8")}"

        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                return@withContext parseOverpassJson(response)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext emptyList()
    }

    private fun parseOverpassJson(jsonString: String): List<MapWay> {
        val jsonObject = JSONObject(jsonString)
        val elements = jsonObject.getJSONArray("elements")

        val nodesMap = mutableMapOf<Long, MapNode>()
        val ways = mutableListOf<MapWay>()

        // 1. Primero extraemos todos los nodos (puntos en el mapa)
        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)
            if (element.getString("type") == "node") {
                val id = element.getLong("id")
                val lat = element.getDouble("lat")
                val lon = element.getDouble("lon")
                nodesMap[id] = MapNode(id, lat, lon)
            }
        }

        // 2. Luego armamos los caminos (ways) conectando los nodos
        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)
            if (element.getString("type") == "way") {
                val tags = if (element.has("tags")) element.getJSONObject("tags") else continue
                val highway = if (tags.has("highway")) tags.getString("highway") else continue

                // Clasificamos si es para autos o personas
                val isForCars = highway.matches("primary|secondary|tertiary|residential|unclassified".toRegex())
                val isForPeople = highway.matches("footway|pedestrian|path|residential|living_street".toRegex())

                if (isForCars || isForPeople) {
                    val nodesArray = element.getJSONArray("nodes")
                    val wayNodes = mutableListOf<MapNode>()

                    for (j in 0 until nodesArray.length()) {
                        val nodeId = nodesArray.getLong(j)
                        nodesMap[nodeId]?.let { wayNodes.add(it) }
                    }

                    if (wayNodes.size > 1) {
                        ways.add(MapWay(element.getLong("id"), wayNodes, isForCars, isForPeople))
                    }
                }
            }
        }
        return ways
    }
}