package ovh.gabrielhuav.pow.domain.models.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

// ESCALA DEL MUNDO: medidas reales (metros) de personas y coches, y su conversión a pantalla. Una sola fuente
// para el jugador (PlayerCharacter), los NPC del WebView (WorldMapLeafletHtml) y el mapa nativo (NativeOsmMap),
// de modo que todos guarden las mismas proporciones con edificios, calles y entre sí. Ver 04_MAP_EXTERIOR.md.

/** Altura del CUERPO de una persona (cabeza a pies), en metros reales. */
const val PERSON_BODY_METERS = 1.7

/**
 * Factor de agrandado ESTILIZADO de los personajes (estilo GTA): a zoom 22 (el máximo) una persona real de 1.7 m se veía
 * pequeña en pantalla, así que jugador y peatones se dibujan ~1.3× más grandes que lo real. Los coches no cambian.
 */
const val CHARACTER_VISUAL_SCALE = 1.3

/** Altura con la que se DIBUJA el cuerpo de una persona (≈ 2.2 m). */
const val PERSON_VISUAL_BODY_METERS = PERSON_BODY_METERS * CHARACTER_VISUAL_SCALE

/**
 * Fracción de la altura del lienzo del sprite que ocupa el cuerpo opaco (medida en el WebView sobre los
 * `npc_walk` de 512×512: ~0.74). El jugador usa `PlayerSkin.WORLD_BODY_CANVAS_FRACTION`.
 */
const val PERSON_SPRITE_BODY_FRACTION = 0.74

/** Lado de la caja (cuadrada) del sprite de un peatón para que su cuerpo mida [PERSON_VISUAL_BODY_METERS]. */
const val PERSON_BOX_METERS = PERSON_VISUAL_BODY_METERS / PERSON_SPRITE_BODY_FRACTION

/**
 * Lado largo de la caja (cuadrada, 175×175) del sprite de un coche. El sedán ocupa ~0.84 de la caja, así que 5.0 m
 * dan ~4.2 m visibles (antes 4.0 m → 3.4 m, demasiado pequeño frente a una persona y a los edificios).
 * La colisión (`CarModel.collisionDimensions`) escala igual para seguir al sprite.
 */
const val CAR_BOX_METERS = 5.0

/** Metros reales que cubre un dp (= un píxel CSS del WebView) a este zoom y latitud (proyección Web Mercator). */
fun metersPerDp(zoom: Double, latitudeDegrees: Double): Double =
    40_075_016.686 * cos(latitudeDegrees * PI / 180.0) / (256.0 * 2.0.pow(zoom))

/** Dp que ocupan [meters] a este zoom y latitud. */
fun dpForMeters(meters: Double, zoom: Double, latitudeDegrees: Double): Double =
    meters / metersPerDp(zoom, latitudeDegrees)

/**
 * Radio del cuerpo del jugador a pie para la colisión estática (metros). Android (`WorldMapMovement`) e iOS
 * (`MapaMundoIos`) lo comparten; acompaña a [CHARACTER_VISUAL_SCALE] (antes 0.35 m, con el cuerpo dibujado 1.0×).
 */
const val PLAYER_FOOT_COLLIDER_METERS = 0.45
