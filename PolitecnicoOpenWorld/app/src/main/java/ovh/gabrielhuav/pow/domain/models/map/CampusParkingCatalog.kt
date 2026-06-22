package ovh.gabrielhuav.pow.domain.models.map

import ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph

/**
 * Un campus "enterable" cuyo estacionamiento se comparte entre el mapa EXTERIOR y su
 * LOBBY interior.
 *
 *  - [assetMatch]: subcadena del assetPath/backgroundAsset que identifica al campus
 *    (p. ej. "building_escom"). El truco funciona porque el landmark del mapa global y el
 *    fondo del lobby usan EL MISMO archivo, así que una coordenada local 0-1 cae igual en ambos.
 *  - [navGraphAsset]: ruta en assets/ del navGraph del campus. Sus nodos `isParkingSlot=true`
 *    (coords locales 0-1) son la FUENTE ÚNICA de las plazas de estacionamiento.
 *  - [baseWidthMeters]: ancho REAL del asset en metros. Permite escalar los autos del interior
 *    (metros→píxel) para que se vean del MISMO tamaño que en el exterior.
 */
data class CampusParking(
    val assetMatch: String,
    val navGraphAsset: String,
    val baseWidthMeters: Float,
    val baseHeightMeters: Float
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
