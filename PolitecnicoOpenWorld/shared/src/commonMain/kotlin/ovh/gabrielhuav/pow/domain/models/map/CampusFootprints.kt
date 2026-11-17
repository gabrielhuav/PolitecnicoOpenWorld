package ovh.gabrielhuav.pow.domain.models.map

import kotlinx.serialization.Serializable
import ovh.gabrielhuav.pow.data.json.PowJson
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.platform.assets.PowAssets

// Huellas del campus (edificios, canchas, estacionamientos, parques) tomadas de OpenStreetMap
// (ODbL). Alimentan el render "cenital estilizado" del prototipo; el mismo dato puede servir
// después para colisiones. Detalle en README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Archivo `CONFIG/escom_osm_footprints.json`. Los campos nuevos deben tener valor por defecto. */
@Serializable
data class CampusFootprintsFile(
    val source: String = "",
    val ways: List<FootprintWay> = emptyList(),
)

@Serializable
data class FootprintWay(
    val id: Long,
    val tags: Map<String, String> = emptyMap(),
    /** Pares `[lat, lon]`; el último repite al primero (polígono cerrado de OSM). */
    val pts: List<List<Double>> = emptyList(),
)

enum class FootprintKind { BUILDING, ROOF, PITCH, PARKING, PARK }

/** Una huella ya clasificada. [heightMeters] solo se usa para dar volumen al dibujarla. */
data class CampusFootprint(
    val id: Long,
    val name: String,
    val kind: FootprintKind,
    val heightMeters: Double,
    val points: List<GeoPoint>,
)

private const val METERS_PER_LEVEL = 3.2
private const val DEFAULT_BUILDING_HEIGHT = 6.0
private const val LOW_BUILDING_HEIGHT = 3.0

internal fun FootprintWay.classify(): FootprintKind = when {
    tags["amenity"] == "parking" -> FootprintKind.PARKING
    tags["leisure"] == "park" -> FootprintKind.PARK
    tags["leisure"] == "pitch" -> FootprintKind.PITCH
    tags["building"] == "roof" -> FootprintKind.ROOF
    else -> FootprintKind.BUILDING
}

internal fun FootprintWay.heightMeters(kind: FootprintKind): Double {
    if (kind != FootprintKind.BUILDING) return if (kind == FootprintKind.ROOF) LOW_BUILDING_HEIGHT else 0.0
    tags["height"]?.toDoubleOrNull()?.let { return it }
    tags["building:levels"]?.toDoubleOrNull()?.let { return it * METERS_PER_LEVEL }
    return when (tags["building"]) {
        "stable" -> LOW_BUILDING_HEIGHT
        else -> if (tags["amenity"] == "shelter") LOW_BUILDING_HEIGHT else DEFAULT_BUILDING_HEIGHT
    }
}

/**
 * Convierte el archivo en huellas dibujables, ordenadas de atrás hacia adelante: primero el suelo
 * (parques, estacionamientos, canchas) y luego los edificios de norte a sur, para que un edificio
 * al sur tape las paredes del que tiene detrás. Descarta formas con menos de 3 vértices.
 */
fun CampusFootprintsFile.toFootprints(): List<CampusFootprint> {
    val result = ways.mapNotNull { way ->
        val raw = way.pts.mapNotNull { if (it.size >= 2) GeoPoint(it[0], it[1]) else null }
        val pts = if (raw.size > 1 && raw.first() == raw.last()) raw.dropLast(1) else raw
        if (pts.size < 3) return@mapNotNull null
        val kind = way.classify()
        CampusFootprint(way.id, way.tags["name"].orEmpty(), kind, way.heightMeters(kind), pts)
    }
    val groundOrder = listOf(FootprintKind.PARK, FootprintKind.PARKING, FootprintKind.PITCH)
    val ground = groundOrder.flatMap { k -> result.filter { it.kind == k } }
    val volumes = result.filter { it.kind == FootprintKind.BUILDING || it.kind == FootprintKind.ROOF }
        .sortedByDescending { fp -> fp.points.maxOf { it.latitude } }
    return ground + volumes
}

/** Dónde vive el JSON de huellas del campus (OSM). */
const val RUTA_HUELLAS_CAMPUS = "CONFIG/escom_osm_footprints.json"

/**
 * Colisiones de edificios derivadas de las huellas: solo `building` (no techos sueltos como gasolineras,
 * ni canchas/estacionamientos/parques, que se pisan). Nombre `OSM_<id>` para distinguirlos de los dibujados a mano.
 */
fun CampusFootprintsFile.toCollisionPolygons(): List<CollisionPolygon> =
    toFootprints().filter { it.kind == FootprintKind.BUILDING }.map { fp ->
        CollisionPolygon(
            name = "OSM_${fp.id}" + if (fp.name.isNotBlank()) "_${fp.name}" else "",
            nodes = fp.points.map { GeoNode(it.latitude, it.longitude) },
        )
    }

/** Agrega las huellas como polígonos BARRIER; no repite nombres ya presentes. */
fun ExteriorCollisionsConfig.conHuellasDelCampus(huellas: CampusFootprintsFile): ExteriorCollisionsConfig {
    val existentes = polygons.map { it.name }.toSet()
    return copy(polygons = polygons + huellas.toCollisionPolygons().filter { it.name !in existentes })
}

/**
 * Colisiones del exterior + huellas de OSM del campus. Si faltan las huellas devuelve solo las dibujadas a
 * mano (nunca lanza). Bloqueante (I/O de assets): llamarlo fuera del hilo de UI. Lo usan Android (ViewModel) e iOS
 * (`MapaMundoIos`); mover al jugador con [avanzarConColision] para que quien quede dentro de un edificio pueda salir.
 */
fun cargarColisionesConHuellas(): ExteriorCollisionsConfig {
    val base = cargarColisionesExteriores()
    return try {
        base.conHuellasDelCampus(PowJson.decodeFromString<CampusFootprintsFile>(PowAssets.texto(RUTA_HUELLAS_CAMPUS)))
    } catch (e: Exception) {
        base
    }
}
