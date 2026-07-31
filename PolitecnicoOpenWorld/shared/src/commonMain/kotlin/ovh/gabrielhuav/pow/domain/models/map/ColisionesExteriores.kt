package ovh.gabrielhuav.pow.domain.models.map

import ovh.gabrielhuav.pow.data.json.PowJson
import ovh.gabrielhuav.pow.platform.assets.PowAssets

/**
 * 🧱 CARGAR LAS COLISIONES DEL EXTERIOR — multiplataforma.
 *
 * Antes esto vivía dentro de `WorldMapViewModel.loadExteriorCollisions(context)`, que recibe un
 * `Context` de Android. Aquí lee por [PowAssets], así que **funciona igual en Android y en iOS** y
 * se puede usar sin arrastrar el ViewModel entero.
 *
 * ⚠️ **Bloqueante (I/O de assets): llámalo fuera del hilo de UI.**
 *
 * Si el archivo no está o no parsea, devuelve una configuración **vacía** en vez de lanzar: el
 * mundo sin muros se puede recorrer, y un crash al entrar al mapa sería mucho peor que atravesar
 * una barda.
 */
fun cargarColisionesExteriores(
    ruta: String = RUTA_COLISIONES_EXTERIOR,
): ExteriorCollisionsConfig = try {
    PowJson.decodeFromString<ExteriorCollisionsConfig>(PowAssets.texto(ruta))
} catch (e: Exception) {
    ExteriorCollisionsConfig()
}

/** Dónde vive el JSON de muros. La MISMA ruta que usa Android. */
const val RUTA_COLISIONES_EXTERIOR = "CONFIG/exterior_collisions.json"

/**
 * ¿El paso de ([desdeLat], [desdeLon]) a ([hastaLat], [hastaLon]) choca con algo?
 *
 * Es la misma regla que aplica `WorldMapMovement.kt` en Android: se cruza un muro **o** el destino
 * cae dentro de un polígono bloqueado.
 *
 * ⚠️ **Se comprueba el PASO, no solo el destino.** Con pasos grandes, mirar únicamente el punto
 * final dejaría atravesar una barda de un salto — el jugador aparecería al otro lado.
 */
fun ExteriorCollisionsConfig.chocaAlMoverse(
    desdeLat: Double,
    desdeLon: Double,
    hastaLat: Double,
    hastaLon: Double,
): Boolean {
    for (muro in walls) {
        if (muro.didHitWall(desdeLat, desdeLon, hastaLat, hastaLon)) return true
    }
    for (poligono in polygons) {
        if (poligono.contains(hastaLat, hastaLon)) return true
    }
    return false
}
