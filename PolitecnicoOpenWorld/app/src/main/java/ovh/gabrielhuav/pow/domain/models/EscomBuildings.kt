package ovh.gabrielhuav.pow.domain.models

import org.osmdroid.util.GeoPoint

/**
 * Catálogo de los 6 edificios interiores de ESCOM.
 *
 * Cada edificio tiene:
 *  - id: string estable usado como sufijo de ruta de navegación
 *  - displayName: nombre visible
 *  - location: coordenada del open world donde aparece su ZombiHand
 *  - backgroundAsset: imagen de fondo dentro de assets/
 *  - routeName: ruta registrada en el NavHost de MainActivity
 *
 * COORDENADAS: son placeholders distribuidos alrededor del centro de ESCOM
 * (19.50456, -99.14674) con offsets pequeños. Para afinarlas, activa el modo
 * debug en el mapa y mueve los marcadores amarillos visibles.
 */
enum class InteriorBuilding(
    val id: String,
    val displayName: String,
    val location: GeoPoint,
    val backgroundAsset: String,
    val routeName: String
) {
    AUDITORIO(
        id = "auditorio",
        displayName = "Auditorio",
        location = GeoPoint(19.50480, -99.14640),
        backgroundAsset = "ZOMBIS_MOD/interiores/za_auditorio.webp",
        routeName = "interior_auditorio"
    ),
    BIBLIOTECA(
        id = "biblioteca",
        displayName = "Biblioteca",
        location = GeoPoint(19.50445, -99.14705),
        backgroundAsset = "ZOMBIS_MOD/interiores/za_biblioteca.webp",
        routeName = "interior_biblioteca"
    ),
    CAFETERIA(
        id = "cafeteria",
        displayName = "Cafetería",
        location = GeoPoint(19.50425, -99.14660),
        backgroundAsset = "ZOMBIS_MOD/interiores/za_cafeteria.webp",
        routeName = "interior_cafeteria"
    ),
    EDIFICIO(
        id = "edificio",
        displayName = "Edificio Principal",
        location = GeoPoint(19.50470, -99.14695),
        backgroundAsset = "ZOMBIS_MOD/interiores/za_edificio.webp",
        routeName = "interior_edificio"
    ),
    ESTACIONAMIENTO(
        id = "estacionamiento",
        displayName = "Estacionamiento",
        location = GeoPoint(19.50435, -99.14635),
        backgroundAsset = "ZOMBIS_MOD/interiores/za_estacionamiento.webp",
        routeName = "interior_estacionamiento"
    ),
    PALAPAS(
        id = "palapas",
        displayName = "Palapas",
        location = GeoPoint(19.50460, -99.14720),
        backgroundAsset = "ZOMBIS_MOD/interiores/za_palapas.webp",
        routeName = "interior_palapas"
    );

    companion object {
        fun fromId(id: String?): InteriorBuilding? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Bounding box visible en modo debug. Coincide con el rectángulo que usa
 * WorldMapViewModel.isInsideEscom() para decidir dónde puede spawnear contenido
 * ESCOM-específico.
 */
object EscomBoundingBox {
    const val CENTER_LAT = 19.50456
    const val CENTER_LON = -99.14674
    const val HALF_OFFSET = 0.001 // ~111 m de radio

    val topLeft = GeoPoint(CENTER_LAT + HALF_OFFSET, CENTER_LON - HALF_OFFSET)
    val topRight = GeoPoint(CENTER_LAT + HALF_OFFSET, CENTER_LON + HALF_OFFSET)
    val bottomRight = GeoPoint(CENTER_LAT - HALF_OFFSET, CENTER_LON + HALF_OFFSET)
    val bottomLeft = GeoPoint(CENTER_LAT - HALF_OFFSET, CENTER_LON - HALF_OFFSET)
}