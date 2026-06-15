package ovh.gabrielhuav.pow.data.repository

import android.content.Context
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.MetrobusStation

object MetrobusRepository {
    fun loadStations(context: Context): List<MetrobusStation> {
        val stations = mutableListOf<MetrobusStation>()
        try {
            val inputStream = context.resources.openRawResource(R.raw.metrobus)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val features = jsonObject.getJSONArray("features")
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val properties = feature.getJSONObject("properties")
                val name = properties.optString("name", "Unknown")
                val routesList = mutableListOf<String>()
                val routesArray = properties.optJSONArray("routes")
                if (routesArray != null) {
                    for (j in 0 until routesArray.length()) routesList.add(routesArray.getString(j))
                }
                val geometry = feature.getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")
                if (coordinates.length() >= 2) {
                    stations.add(MetrobusStation(name, routesList, GeoPoint(coordinates.getDouble(1), coordinates.getDouble(0))))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return stations
    }
}