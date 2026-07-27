package ovh.gabrielhuav.pow.domain.models.map

import kotlinx.serialization.encodeToString
import ovh.gabrielhuav.pow.data.json.PowJson

import ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph

/**
 * Un campus "enterable" cuyo estacionamiento se comparte entre el mapa EXTERIOR y su
 * LOBBY interior.
 *
 *  - [assetMatch]: subcadena del assetPath/backgroundAsset que identifica al campus
 *    (p. ej. "building_escom"). El truco funciona porque el landmark del mapa global y el
 *    fondo del lobby usan EL MISMO archivo.
 *  - [navGraphAsset]: ruta en assets/ del navGraph del campus. Sus nodos `isParkingSlot=true`
 *    (coords locales 0-1) son la FUENTE ÚNICA de las plazas de estacionamiento. La coord local
 *    0-1 cae IGUAL en ambos consumidores: el exterior mapea localY=0 al norte (arriba) vía
 *    `Landmark.toGlobalGeoPoint` (dyMeters=(0.5-localY)), y el lobby dibuja el asset top-down
 *    (localY=0 arriba). Por eso NO hay que espejar la Y (verificado sobre building_escom.webp).
 *  - [baseWidthMeters]/[baseHeightMeters]: dimensiones REALES del asset en metros. Escalan los autos
 *    del interior (metros→píxel) para que se vean del MISMO tamaño que en el exterior y fijan el
 *    ASPECTO con el que se calcula la orientación del cajón (ver ParkedCarsLayer).
 *
 * CALIBRACIÓN DEL LOBBY (transformación de GRUPO estilo PowerPoint): las coords crudas del navGraph
 * trazan los CARRILES (curvas alrededor de las islas), no filas de cajones, y el lobby dibuja el asset
 * SIN rotar. El exterior las coloca bien porque su pipeline aplica dos rotaciones que casan
 * (`toGlobalGeoPoint` gira las posiciones por el ángulo del campus + `GroundOverlay.bearing` gira el
 * asset lo mismo); el lobby no aplica ninguna, así que el CÚMULO entero queda rotado/desplazado. Por
 * eso NO basta un offset de orientación por auto: hay que mover el grupo como cuerpo rígido. Esa
 * transformación NO vive aquí: es un archivo JSON en assets ([parkingCalibrationAsset]) que produce el
 * calibrador en vivo (Diseñador → Estacionamiento → EXPORTAR) y lee [CampusParkingCatalog.loadCalibration].
 * Así, sumar/ajustar un campus = soltar su .json, sin tocar Kotlin (igual que los navGraphs).
 */
data class CampusParking(
    val assetMatch: String,
    val navGraphAsset: String,
    val baseWidthMeters: Float,
    val baseHeightMeters: Float
) {
    /** Ruta en assets/ del JSON de calibración del estacionamiento del lobby (exportado por el calibrador). */
    val parkingCalibrationAsset: String get() = "CONFIG/parking/$assetMatch.json"
}

/**
 * Transformación de GRUPO del estacionamiento del lobby (mismo esquema que exporta ParkingTuneTool).
 * Reproduce el acomodo del exterior a partir de las coords crudas del navGraph. Identidad = todo 0,
 * scale 1, flipped vacío.
 */
data class ParkingCalibration(
    val headingDeg: Float = 0f,
    val offsetXFrac: Float = 0f,
    val offsetYFrac: Float = 0f,
    val scale: Float = 1f,
    val selfRotationDeg: Float = 0f,
    val flipped: List<Int> = emptyList()
)

/**
 * FUENTE ÚNICA DE VERDAD del estacionamiento, compartida por el mapa exterior (NPCs que
 * aparcan en los slots) y el lobby interior (autos "presentes" desde el mismo JSON). Ambos
 * lados leen los MISMOS nodos `isParkingSlot` del navGraph del campus, en coords locales 0-1
 * relativas al asset, así que quedan en la misma posición/tamaño relativo sin duplicar datos.
 *
 * EXPANDIBLE (FES, UAM, …): añadir una universidad = UNA línea aquí + su navGraph en assets +
 * usar su asset top-down como fondo TANTO del landmark exterior COMO de su lobby interior.
 */
object CampusParkingCatalog {

    val campuses: List<CampusParking> = listOf(
        CampusParking(
            assetMatch = "building_escom",
            navGraphAsset = "CONFIG/navgraphs/escom_navgraph.json",
            baseWidthMeters = 212.7f,
            baseHeightMeters = 263.0f
            // Calibración de grupo del lobby → assets/CONFIG/parking/building_escom.json
        ),
        // Ejemplo para sumar otra universidad (cuando tenga su navGraph y comparta asset):
        // CampusParking("building_fes", "CONFIG/navgraphs/fes_navgraph.json", 180f, 150f),
    )

    /**
     * Campus cuyo asset coincide con [assetPath] (sirve para el assetPath del landmark exterior
     * o el backgroundAsset del lobby interior). null = ese asset no tiene estacionamiento.
     */
    fun forAsset(assetPath: String?): CampusParking? {
        if (assetPath.isNullOrBlank()) return null
        return campuses.firstOrNull { assetPath.contains(it.assetMatch, ignoreCase = true) }
    }

    /**
     * Lee la calibración de GRUPO del lobby desde assets ([CampusParking.parkingCalibrationAsset]).
     * BLOQUEANTE (I/O de assets): invócalo fuera del hilo principal. Si el archivo no existe o no
     * parsea, devuelve IDENTIDAD (el lote se dibuja con las coords crudas, sin acomodar) y lo loguea.
     */
    fun loadCalibration(context: android.content.Context, campus: CampusParking): ParkingCalibration {
        return try {
            context.assets.open(campus.parkingCalibrationAsset).use { ins ->
                PowJson.decodeFromString<ParkingCalibration>(ins.reader().readText())
            } ?: ParkingCalibration()
        } catch (e: Exception) {
            android.util.Log.w("CampusParkingCatalog", "Sin calibración de estacionamiento para ${campus.assetMatch} (${campus.parkingCalibrationAsset}); se usa identidad.", e)
            ParkingCalibration()
        }
    }
}

/**
 * Una plaza ocupada: posición local 0-1 + la DIRECCIÓN de su carril (nodo previo → plaza) en
 * coords locales normalizadas. La dirección sirve para orientar el auto IGUAL que el exterior
 * (`NpcAiManager.spawnParkedCar`, que usa el mismo nodo previo→plaza).
 */
data class ParkingSlot(
    val localX: Float,
    val localY: Float,
    val dirX: Float,
    val dirY: Float
)

/**
 * Plazas de estacionamiento de un navGraph (coords locales 0-1) + la dirección de su carril.
 * Renderer-agnóstico: el exterior las convierte a lat/lon vía el landmark; el lobby interior las
 * convierte a píxel (local*worldW/H) y orienta el auto con `dirX/dirY` (ver `ParkedCarsLayer`).
 */
fun LandmarkNavGraph.parkingSlots(): List<ParkingSlot> =
    ways.flatMap { w ->
        w.nodes.mapIndexedNotNull { i, n ->
            if (!n.isParkingSlot) return@mapIndexedNotNull null
            val prev = if (i > 0) w.nodes[i - 1] else n   // mismo "nodo previo" que spawnParkedCar
            ParkingSlot(n.localX, n.localY, n.localX - prev.localX, n.localY - prev.localY)
        }
    }
