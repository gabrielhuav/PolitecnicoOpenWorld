package ovh.gabrielhuav.pow.data.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ovh.gabrielhuav.pow.domain.models.MapNode
import ovh.gabrielhuav.pow.domain.models.MapWay
import java.io.File

/**
 * Caché persistente de la red de calles en disco.
 *
 * DISEÑO:
 * - Guarda los MapWay como JSON en un archivo en el directorio de caché de la app.
 * - Cada zona descargada se identifica por una "celda" de cuadrícula de ~2km x 2km.
 *   Esto permite tener múltiples zonas cacheadas independientemente.
 * - Un archivo de metadatos (index.json) registra qué celdas existen y cuándo se descargaron.
 * - Los datos son válidos por CACHE_TTL_MS (7 días por defecto).
 *
 * ESTRUCTURA DE ARCHIVOS:
 *   cache/road_network/
 *     index.json          ← metadatos: {celdaKey: timestampMs}
 *     cell_19500_-99150.json  ← datos de una celda específica
 *     cell_19520_-99140.json
 *     ...
 *
 * VENTAJAS SOBRE SHAREDPREFERENCES:
 * - SharedPreferences tiene un límite práctico de ~1-2 MB antes de degradarse.
 * - Una zona de 2km de calles puede ser 200-500 KB de JSON, con varias zonas
 *   fácilmente superaría ese límite.
 * - Los archivos son directamente inspeccionables para debug.
 */
class RoadNetworkCache(context: Context) {

    private val TAG = "RoadNetworkCache"

    // Directorio exclusivo para esta caché
    private val cacheDir = File(context.cacheDir, "road_network").also { it.mkdirs() }
    private val indexFile = File(cacheDir, "index.json")

    // ─── CONFIGURACIÓN ──────────────────────────────────────────────────────────
    companion object {
        // Datos válidos por 7 días. Las calles de ESCOM no cambian en una semana.
        private const val CACHE_TTL_MS = 1000L * 60 * 60 * 24 * 7

        // Tamaño de celda en "grados × 10000" para la clave del índice.
        // 0.02 grados ≈ 2.2 km → cada celda cubre ~2km x 2km.
        // Un jugador en ESCOM raramente necesitará más de 2-3 celdas en total.
        private const val CELL_SIZE_DEGREES = 0.02

        // Máximo de celdas a guardar en caché. Cada celda ≈ 200-400 KB.
        // 20 celdas × 400 KB ≈ 8 MB máximo de datos de calles en disco.
        private const val MAX_CACHED_CELLS = 20
    }

    // ─── API PÚBLICA ─────────────────────────────────────────────────────────────

    /**
     * Devuelve los datos de calles para la zona indicada si existen en caché y no han expirado.
     * Retorna null si no hay caché válida (hay que llamar a Overpass).
     */
    suspend fun get(lat: Double, lon: Double): List<MapWay>? = withContext(Dispatchers.IO) {
        val key = cellKey(lat, lon)
        val index = loadIndex()

        val timestamp = index.optLong(key, -1L)
        if (timestamp == -1L) {
            Log.d(TAG, "MISS (no existe): celda $key")
            return@withContext null
        }

        val age = System.currentTimeMillis() - timestamp
        if (age > CACHE_TTL_MS) {
            Log.d(TAG, "MISS (expirada, ${age / 3600000}h): celda $key")
            // Limpiar entrada vencida del índice
            index.remove(key)
            saveIndex(index)
            File(cacheDir, "cell_$key.json").delete()
            return@withContext null
        }

        val cellFile = File(cacheDir, "cell_$key.json")
        if (!cellFile.exists()) {
            Log.d(TAG, "MISS (archivo borrado): celda $key")
            return@withContext null
        }

        return@withContext try {
            val ways = deserializeWays(cellFile.readText())
            Log.d(TAG, "HIT: celda $key → ${ways.size} ways (${age / 3600000}h de antigüedad)")
            ways
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo caché de celda $key: ${e.message}")
            null
        }
    }

    /**
     * Guarda la red de calles descargada para la zona indicada.
     * Aplica LRU si se supera el límite de celdas.
     */
    suspend fun put(lat: Double, lon: Double, ways: List<MapWay>) = withContext(Dispatchers.IO) {
        if (ways.isEmpty()) return@withContext

        val key = cellKey(lat, lon)
        val index = loadIndex()

        // LRU: Si ya tenemos demasiadas celdas, borrar la más antigua
        if (index.length() >= MAX_CACHED_CELLS && !index.has(key)) {
            evictOldestCell(index)
        }

        val cellFile = File(cacheDir, "cell_$key.json")
        try {
            cellFile.writeText(serializeWays(ways))
            index.put(key, System.currentTimeMillis())
            saveIndex(index)
            Log.d(TAG, "GUARDADO: celda $key → ${ways.size} ways (${cellFile.length() / 1024} KB)")
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando celda $key: ${e.message}")
        }
    }

    /**
     * Devuelve información de diagnóstico sobre el estado de la caché.
     * Útil para mostrar en un panel de debug o logs.
     */
    suspend fun getStats(): CacheStats = withContext(Dispatchers.IO) {
        val index = loadIndex()
        val totalSizeBytes = cacheDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
        CacheStats(
            cellCount = index.length(),
            totalSizeKb = (totalSizeBytes / 1024).toInt(),
            maxCells = MAX_CACHED_CELLS,
            ttlDays = (CACHE_TTL_MS / (1000 * 60 * 60 * 24)).toInt()
        )
    }

    // ─── SERIALIZACIÓN ───────────────────────────────────────────────────────────

    /**
     * Serializa la lista de MapWay a JSON.
     *
     * FORMATO:
     * {
     *   "ways": [
     *     {
     *       "id": 123456,
     *       "cars": true,
     *       "people": false,
     *       "nodes": [[19.5045, -99.1469], [19.5050, -99.1465], ...]
     *     },
     *     ...
     *   ]
     * }
     *
     * Compacto: los nodos se guardan como arrays de 2 elementos en lugar de objetos
     * con "lat" y "lon", reduciendo el tamaño del JSON ~40%.
     */
    private fun serializeWays(ways: List<MapWay>): String {
        val root = JSONObject()
        val waysArray = JSONArray()

        for (way in ways) {
            val wayObj = JSONObject()
            wayObj.put("id", way.id)
            wayObj.put("cars", way.isForCars)
            wayObj.put("people", way.isForPeople)

            val nodesArray = JSONArray()
            for (node in way.nodes) {
                // Formato compacto: [id, lat, lon] como array de 3 elementos
                val nodeArr = JSONArray()
                nodeArr.put(node.id)
                // Guardamos como enteros × 1e6 para evitar decimales flotantes en JSON
                // Ej: 19.504512 → 19504512 (ahorro significativo de caracteres)
                nodeArr.put((node.lat * 1_000_000).toLong())
                nodeArr.put((node.lon * 1_000_000).toLong())
                nodesArray.put(nodeArr)
            }
            wayObj.put("nodes", nodesArray)
            waysArray.put(wayObj)
        }

        root.put("ways", waysArray)
        return root.toString()
    }

    private fun deserializeWays(json: String): List<MapWay> {
        val root = JSONObject(json)
        val waysArray = root.getJSONArray("ways")
        val ways = ArrayList<MapWay>(waysArray.length())

        for (i in 0 until waysArray.length()) {
            val wayObj = waysArray.getJSONObject(i)
            val id = wayObj.getLong("id")
            val isForCars = wayObj.getBoolean("cars")
            val isForPeople = wayObj.getBoolean("people")

            val nodesArray = wayObj.getJSONArray("nodes")
            val nodes = ArrayList<MapNode>(nodesArray.length())

            for (j in 0 until nodesArray.length()) {
                val nodeArr = nodesArray.getJSONArray(j)
                val nodeId = nodeArr.getLong(0)
                // Revertir la conversión de entero × 1e6 a Double
                val lat = nodeArr.getLong(1) / 1_000_000.0
                val lon = nodeArr.getLong(2) / 1_000_000.0
                nodes.add(MapNode(nodeId, lat, lon))
            }

            if (nodes.size > 1) {
                ways.add(MapWay(id, nodes, isForCars, isForPeople))
            }
        }
        return ways
    }

    // ─── GESTIÓN DEL ÍNDICE ───────────────────────────────────────────────────────

    private fun loadIndex(): JSONObject {
        return if (indexFile.exists()) {
            try {
                JSONObject(indexFile.readText())
            } catch (e: Exception) {
                Log.e(TAG, "Índice corrupto, reiniciando: ${e.message}")
                JSONObject()
            }
        } else {
            JSONObject()
        }
    }

    private fun saveIndex(index: JSONObject) {
        try {
            indexFile.writeText(index.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando índice: ${e.message}")
        }
    }

    private fun evictOldestCell(index: JSONObject) {
        var oldestKey: String? = null
        var oldestTime = Long.MAX_VALUE

        val keys = index.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val t = index.optLong(k, Long.MAX_VALUE)
            if (t < oldestTime) {
                oldestTime = t
                oldestKey = k
            }
        }

        oldestKey?.let { key ->
            File(cacheDir, "cell_$key.json").delete()
            index.remove(key)
            Log.d(TAG, "LRU EVICT: celda $key eliminada para hacer espacio")
        }
    }

    // ─── UTILIDADES ──────────────────────────────────────────────────────────────

    /**
     * Genera una clave única para la celda de la cuadrícula que contiene (lat, lon).
     * Ejemplo: lat=19.5045, lon=-99.1469 → "19500_-99150"
     *
     * Todas las coordenadas dentro de un cuadrado de ~2km x 2km producen la misma clave,
     * lo que significa que una sola descarga de Overpass sirve para toda esa zona.
     */
    private fun cellKey(lat: Double, lon: Double): String {
        val cellLat = Math.floor(lat / CELL_SIZE_DEGREES).toInt()
        val cellLon = Math.floor(lon / CELL_SIZE_DEGREES).toInt()
        return "${cellLat}_${cellLon}"
    }

    data class CacheStats(
        val cellCount: Int,
        val totalSizeKb: Int,
        val maxCells: Int,
        val ttlDays: Int
    ) {
        override fun toString() =
            "Calles: $cellCount/$maxCells celdas, ${totalSizeKb}KB en disco, TTL ${ttlDays}d"
    }
}