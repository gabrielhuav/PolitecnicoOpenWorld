package ovh.gabrielhuav.pow.features.map_exterior.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL

class RoutingRepository {

    suspend fun fetchPedestrianRoute(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        return fetchRouteFromOSRM("foot", start, end)
    }

    suspend fun fetchDrivingRoute(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        return fetchRouteFromOSRM("driving", start, end)
    }

    // Función genérica para no repetir código entre peatones y autos
    private suspend fun fetchRouteFromOSRM(profile: String, start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        return withContext(Dispatchers.IO) {
            val path = mutableListOf<GeoPoint>()
            try {
                val urlString = "https://router.project-osrm.org/route/v1/$profile/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val jsonObject = JSONObject(response)
                    val routes = jsonObject.getJSONArray("routes")

                    if (routes.length() > 0) {
                        val geometry = routes.getJSONObject(0).getJSONObject("geometry")
                        val coordinates = geometry.getJSONArray("coordinates")

                        for (i in 0 until coordinates.length()) {
                            val coord = coordinates.getJSONArray(i)
                            // OSRM devuelve [longitud, latitud], GeoPoint usa (latitud, longitud)
                            path.add(GeoPoint(coord.getDouble(1), coord.getDouble(0)))
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error al obtener la ruta de OSRM ($profile): ${e.message}")
            }
            path
        }
    }
}