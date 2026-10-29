package ovh.gabrielhuav.pow.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import ovh.gabrielhuav.pow.data.cache.celdaDeCalles
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
 *
 * 🆕 (2026-10-28) **Varios servidores** ([OverpassServidores]). Con uno solo, cuando
 * `overpass-api.de` se saturaba o nos limitaba (429/504) una instalación nueva —sin calles en
 * caché— se quedaba para siempre en el 25 % de la carga del mundo abierto: el VM reintenta cada
 * 3 s contra el MISMO servidor. Ahora cada intento recorre la lista hasta que uno responda.
 *
 * 🆕 (2026-10-29) **Primero las calles ESTÁTICAS** ([CallesEstaticas]): la CDMX ya recortada en
 * celdas, publicada en la rama `datos-calles` por la GitHub Action `calles-cdmx.yml`. Ni con seis
 * servidores bastó: en horas pico TODOS los públicos respondían 502/504 ("at capacity"), y
 * Overpass pide expresamente que las apps no usen su endpoint público. Overpass queda solo para
 * celdas fuera de la cobertura (respuesta 404) o si GitHub no responde.
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
        private const val USER_AGENT =
            "PolitecnicoOpenWorld/1.0 (educational game; contact: dev@pow.ovh)"

        /** Índice en [OverpassServidores.URLS] del último que respondió bien. */
        @Volatile
        private var indiceUltimoBueno = 0

        private val http by lazy {
            HttpClient {
                install(HttpTimeout) {
                    // 10 s: un servidor caído debe fallar rápido para pasar al siguiente.
                    connectTimeoutMillis = 10_000L
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

            descargarEstaticas(lat, lon)?.let { return@withContext it }

            val datos = codificarUrl(query)
            val orden = OverpassServidores.ordenDesde(indiceUltimoBueno)
            for (base in orden) {
                val ways = intentar(base, datos) ?: continue
                // El próximo intento empieza por el que funcionó (no castigar siempre al caído).
                indiceUltimoBueno = OverpassServidores.URLS.indexOf(base)
                return@withContext ways
            }
            powLog(TAG, "Ningún servidor de Overpass respondió (${orden.size} probados).")
            emptyList()
        }

    /**
     * La celda de (lat, lon) desde la rama `datos-calles`. `null` = no hay archivo (fuera de la
     * cobertura → 404) o GitHub no respondió: el caller sigue con Overpass.
     */
    private suspend fun descargarEstaticas(lat: Double, lon: Double): List<MapWay>? = try {
        val url = CallesEstaticas.url(lat, lon)
        val respuesta = http.get(url) { header("User-Agent", USER_AGENT) }
        if (respuesta.status.isSuccess()) {
            val parsed = parseOverpassJson(respuesta.bodyAsText())
            powLog(TAG, "Calles estáticas OK (${celdaDeCalles(lat, lon)}): ${parsed.size} ways")
            parsed
        } else {
            // 404 = celda fuera de la cobertura; no es un error.
            powLog(TAG, "Sin calles estáticas para ${celdaDeCalles(lat, lon)} (HTTP ${respuesta.status.value})")
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        powLog(TAG, "Calles estáticas no disponibles: ${e.message}")
        null
    }

    /**
     * Una petición a UN servidor. `null` = falló (pasar al siguiente); lista (aunque vacía) =
     * respuesta válida. Una zona sin calles es una respuesta válida: no se pregunta a los demás.
     */
    private suspend fun intentar(base: String, datos: String): List<MapWay>? = try {
        val respuesta = http.get("$base?data=$datos") { header("User-Agent", USER_AGENT) }
        val cuerpo = if (respuesta.status.isSuccess()) respuesta.bodyAsText() else null
        when {
            cuerpo == null -> {
                // 429 = nos limita; 504 = saturado; cualquier otro = caído. Todos: siguiente.
                powLog(TAG, "HTTP ${respuesta.status.value} en $base")
                null
            }
            OverpassServidores.esErrorDelServidor(cuerpo) -> {
                powLog(TAG, "Overpass devolvió 200 con error de ejecución en $base")
                null
            }
            else -> {
                val parsed = parseOverpassJson(cuerpo)
                powLog(TAG, "Descarga OK de $base: ${parsed.size} ways en radio ${FETCH_RADIUS_METERS}m")
                parsed
            }
        }
    } catch (e: CancellationException) {
        throw e // salir del mundo cancela la carga: no tragarse la cancelación
    } catch (e: Exception) {
        powLog(TAG, "Error de red en $base: ${e.message}")
        null
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

    /**
     * Parsea el JSON de Overpass. Las celdas estáticas usan EXACTAMENTE este formato (lo escribe
     * `generar_celdas.py`), así que un solo parser sirve para las dos fuentes.
     */
    internal fun parseOverpassJson(jsonString: String): List<MapWay> {
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

/**
 * Servidores públicos de Overpass, en orden de preferencia (lista de instancias públicas de la
 * wiki de OpenStreetMap). Todos hablan el mismo protocolo, así que la consulta no cambia.
 *
 * ⚠️ `lz4.` y `z.` son las máquinas detrás de `overpass-api.de`: si ESE operador nos limita por
 * IP pueden limitarnos también, por eso siguen instancias de otros operadores. Si una desaparece,
 * basta con quitarla de aquí.
 */
internal object OverpassServidores {
    val URLS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://lz4.overpass-api.de/api/interpreter",
        "https://z.overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )

    /** Todos los servidores, empezando por [inicio] y dando la vuelta. */
    fun ordenDesde(inicio: Int): List<String> {
        val desde = if (inicio in URLS.indices) inicio else 0
        return URLS.indices.map { URLS[(desde + it) % URLS.size] }
    }

    /**
     * Overpass a veces responde **200** con un JSON cuyo `remark` dice que la consulta falló
     * (timeout o memoria del servidor) y `elements` vacío. Tomarlo como "zona sin calles"
     * dejaba la carga atorada igual que un 504.
     */
    fun esErrorDelServidor(cuerpo: String): Boolean {
        val remark = runCatching { powJsonObjeto(cuerpo).optString("remark", "") }.getOrNull()
            ?: return true // ni siquiera es JSON: página de error del proxy/servidor
        return remark.contains("error", ignoreCase = true) ||
            remark.contains("timed out", ignoreCase = true) ||
            remark.contains("out of memory", ignoreCase = true)
    }
}

/**
 * Calles de la CDMX ya recortadas en celdas de ~2 km ([celdaDeCalles]), una por archivo, en la
 * rama huérfana `datos-calles` de este mismo repo. Las genera y publica la GitHub Action
 * `.github/workflows/calles-cdmx.yml` desde el extracto de OpenStreetMap de Geofabrik (© OSM,
 * ODbL). Cada archivo trae su celda **más un margen** (ver `MARGEN_DEG` en el generador), así que
 * cerca del borde no faltan las calles de la celda vecina.
 *
 * Se sirven por `raw.githubusercontent.com` y no por GitHub Pages porque Pages publica UNA sola
 * rama por repo, y aquí es `gh-pages` (políticas de Play). raw sirve cualquier rama, con gzip.
 */
internal object CallesEstaticas {
    const val RAMA = "datos-calles"
    const val BASE = "https://raw.githubusercontent.com/gabrielhuav/PolitecnicoOpenWorld/$RAMA/v1/celdas"

    fun url(lat: Double, lon: Double): String = "$BASE/${celdaDeCalles(lat, lon)}.json"
}
