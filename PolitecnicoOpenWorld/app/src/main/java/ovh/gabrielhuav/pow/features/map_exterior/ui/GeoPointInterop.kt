package ovh.gabrielhuav.pow.features.map_exterior.ui

import org.osmdroid.util.GeoPoint as OsmGeoPoint
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint as PowGeoPoint

/**
 * 🍏 FRONTERA entre el punto lat/lon del dominio y el de osmdroid (Fase 2 de
 * `README for IAS/PLAN_MIGRACION_KMP.md`).
 *
 * Tras la Fase 2, el ESTADO del juego habla `PowGeoPoint` (multiplataforma, en `:shared`) y solo
 * los renderers nativos siguen hablando osmdroid. Este archivo es el único sitio donde se traducen,
 * y por eso vive en `androidMain`/`:app`: **a iOS no viaja**.
 *
 * ⚠️ Si un día se va osmdroid (o se sustituye por MapLibre), este archivo se borra entero y no
 * queda deuda repartida por el código: esa es justo la razón de concentrar aquí la conversión en
 * vez de escribir `OsmGeoPoint(x.latitude, x.longitude)` a mano en 19 sitios.
 *
 * No hace falta el sentido inverso (`toPow`) hoy: los renderers CONSUMEN posiciones, no las
 * producen. Si algún día lo necesitas, añádelo aquí y no en la clase del dominio.
 */
internal fun PowGeoPoint.toOsm(): OsmGeoPoint = OsmGeoPoint(latitude, longitude)

/** Atajo para las polilíneas/polígonos, que se pasan a osmdroid como lista entera. */
internal fun List<PowGeoPoint>.toOsm(): List<OsmGeoPoint> = map { it.toOsm() }
