package ovh.gabrielhuav.pow.features.streetfighter.data

import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId

// ═══════════════════════════════════════════════════════════════════════════
// CAPA DE TEMA (assets intercambiables) del modo de pelea 1v1.
//
// TODO lo que "sabe" de los assets ACTUALES (los del clon Street Fighter) vive
// AQUÍ y solo aquí. El motor (VM/modelos) es agnóstico: lee frames/cajas del
// JSON y emite claves de sonido; la View dibuja lo que el TEMA le dice.
//
// MIGRACIÓN A ASSETS PROPIOS DE POW (para no depender de material con copyright):
//   1) Personajes: genera el sprite sheet nuevo + su JSON (mismo formato que
//      DATA/ryu.json; ver README for IAS/ASSETS_STREETFIGHTER_MIGRACION.md) y
//      cambia spriteAsset/jsonAsset en SfFighterId (p. ej. Prankedy).
//   2) Escenario/HUD/decals/sombra/sonidos: crea otro SfTheme (copia de
//      SF_CLASSIC_THEME con otras rutas/recortes) y cámbialo en la View.
//   Los recortes/posiciones son DATOS, no lógica: nada más se toca.
// ═══════════════════════════════════════════════════════════════════════════

/** Recorte de un sheet + origen respecto al ancla. */
data class SfSpriteFrame(val src: List<Int>, val origin: List<Int>)

/** Persona/objeto decorativo del fondo (frame estático que se mece con el barco). */
data class SfStageProp(val src: List<Int>, val x: Int, val y: Int)

/** Tira de dígitos dentro del sheet del HUD (x0 + dx*dígito). */
data class SfDigitStrip(val x0: Int, val dx: Int, val y: Int, val w: Int, val h: Int) {
    fun digit(d: Int): List<Int> = listOf(x0 + dx * d, y, w, h)
}

/**
 * Tema visual/sonoro completo del modo. Instancia actual: [SF_CLASSIC_THEME].
 * Para el tema POW: crear otra instancia con assets propios (mismo formato).
 */
data class SfTheme(
    // ---- Rutas base en assets/ ----
    val imagesDir: String,
    val soundsDir: String,
    /** Nombres de archivo de imagen que la View decodifica al entrar. */
    val imageFiles: List<String>,
    /** Claves de sonido (nombre base .ogg) que emite el VM. */
    val soundKeys: List<String>,
    val musicFile: String,
    val musicVolume: Float,

    // ---- Escenario ----
    val stageImage: String,
    val stageBackground: List<Int>,      // cielo/océano (parallax lejano)
    val stageBoat: List<Int>,            // elemento medio que se mece
    val stageFloor: List<Int>,
    val stageFloorBottom: List<Int>,
    val ballardSmall: List<Int>,
    val ballardLarge: List<Int>,
    val sideBarrels: List<Int>,
    val flagFrames: List<List<Int>>,     // animación decorativa (133 ms/frame)
    val boatBob: List<Int>,              // offsets Y del bamboleo
    val stagePeople: List<SfStageProp>,

    // ---- Sombra ----
    val shadowImage: String,
    val shadowFrame: SfSpriteFrame,

    // ---- Proyectil especial (hadouken): sheet + frames ----
    val fireballImage: String,
    val fireballActive: List<SfSpriteFrame>,
    val fireballCollided: List<SfSpriteFrame>,

    // ---- Splashes de impacto (fila por playerId) ----
    val splashImage: String,
    val splashFrames: Map<SfAttackStrength, List<List<SfSpriteFrame>>>,

    // ---- HUD ----
    val hudImage: String,
    val healthBar: List<Int>,
    val koWhite: List<Int>,
    val koBlack: List<Int>,
    val nameTags: Map<SfFighterId, List<Int>>,
    val timeDigits: SfDigitStrip,
    val timeDigitsFlash: SfDigitStrip,
    val scoreDigits: SfDigitStrip,
    val scoreLetterP: List<Int>,

    // ---- Texto de ganador (fila por PERSONAJE; sin entrada = no se dibuja) ----
    val winnerImage: String,
    val winnerRows: Map<SfFighterId, Int>,
    val winnerSrcHeight: Int,            // alto visible de cada fila en el png
    val winnerRowStride: Int,            // separación vertical entre filas
    val winnerSrcWidth: Int,
)

/** Tema ACTUAL: assets del clon SF (temporal; se reemplazará por el tema POW). */
val SF_CLASSIC_THEME = SfTheme(
    imagesDir = "STREETFIGHTER/IMAGES/",
    soundsDir = "STREETFIGHTER/SOUNDS/",
    imageFiles = listOf("Ryu.png", "Ken.png", "kenstage.png", "shadow.png", "decals.png", "hud.png", "winnerText.png"),
    soundKeys = listOf(
        "light-attack", "medium-attack", "heavy-attack",
        "light-punch-hit", "medium-punch-hit", "heavy-punch-hit",
        "light-kick-hit", "medium-kick-hit", "heavy-kick-hit",
        "land", "hadouken",
    ),
    musicFile = "kens-theme.ogg",
    musicVolume = 0.2f,

    stageImage = "kenstage.png",
    stageBackground = listOf(72, 208, 768, 176),
    stageBoat = listOf(8, 16, 521, 180),
    stageFloor = listOf(8, 392, 896, 56),
    stageFloorBottom = listOf(8, 448, 896, 16),
    ballardSmall = listOf(800, 184, 21, 16),
    ballardLarge = listOf(760, 176, 31, 24),
    sideBarrels = listOf(560, 472, 151, 96),
    flagFrames = listOf(
        listOf(848, 208, 40, 40), listOf(848, 256, 40, 40), listOf(848, 304, 40, 40),
    ),
    boatBob = listOf(0, -1, -2, -3, -4, -3, -2, -1),
    stagePeople = listOf(
        SfStageProp(listOf(552, 8, 40, 64), 278, 157),
        SfStageProp(listOf(600, 24, 16, 48), 318, 157),
        SfStageProp(listOf(624, 16, 32, 56), 342, 157),
        SfStageProp(listOf(664, 16, 32, 56), 374, 157),
        SfStageProp(listOf(704, 16, 48, 56), 438, 149),
        SfStageProp(listOf(760, 16, 40, 40), 238, 61),
        SfStageProp(listOf(808, 24, 48, 32), 278, 53),
    ),

    shadowImage = "shadow.png",
    shadowFrame = SfSpriteFrame(listOf(0, 0, 43, 9), listOf(21, 7)),

    // Los sprites del hadouken del clon viven dentro de Ken.png
    fireballImage = "Ken.png",
    fireballActive = listOf(
        SfSpriteFrame(listOf(400, 2756, 43, 32), listOf(25, 16)),
        SfSpriteFrame(listOf(0, 0, 0, 0), listOf(0, 0)), // frame de parpadeo (invisible)
        SfSpriteFrame(listOf(460, 2761, 56, 28), listOf(37, 14)),
        SfSpriteFrame(listOf(0, 0, 0, 0), listOf(0, 0)),
    ),
    fireballCollided = listOf(
        SfSpriteFrame(listOf(543, 2767, 26, 20), listOf(13, 10)),
        SfSpriteFrame(listOf(590, 2766, 15, 25), listOf(9, 13)),
        SfSpriteFrame(listOf(625, 2764, 28, 28), listOf(26, 14)),
    ),

    splashImage = "decals.png",
    splashFrames = mapOf(
        SfAttackStrength.LIGHT to listOf(
            listOf(
                SfSpriteFrame(listOf(14, 16, 9, 10), listOf(6, 7)),
                SfSpriteFrame(listOf(34, 15, 13, 11), listOf(7, 7)),
                SfSpriteFrame(listOf(55, 15, 13, 11), listOf(7, 7)),
                SfSpriteFrame(listOf(75, 10, 20, 19), listOf(11, 11)),
            ),
            listOf(
                SfSpriteFrame(listOf(160, 16, 9, 10), listOf(6, 7)),
                SfSpriteFrame(listOf(178, 15, 13, 11), listOf(7, 7)),
                SfSpriteFrame(listOf(199, 15, 13, 11), listOf(7, 7)),
                SfSpriteFrame(listOf(219, 10, 20, 19), listOf(11, 11)),
            ),
        ),
        SfAttackStrength.MEDIUM to listOf(
            listOf(
                SfSpriteFrame(listOf(13, 41, 14, 15), listOf(7, 7)),
                SfSpriteFrame(listOf(34, 39, 21, 19), listOf(10, 9)),
                SfSpriteFrame(listOf(64, 39, 21, 19), listOf(10, 9)),
                SfSpriteFrame(listOf(90, 35, 27, 25), listOf(13, 12)),
            ),
            listOf(
                SfSpriteFrame(listOf(159, 41, 14, 15), listOf(7, 7)),
                SfSpriteFrame(listOf(182, 39, 21, 19), listOf(10, 9)),
                SfSpriteFrame(listOf(211, 39, 21, 19), listOf(10, 9)),
                SfSpriteFrame(listOf(239, 35, 27, 25), listOf(13, 12)),
            ),
        ),
        SfAttackStrength.HEAVY to listOf(
            listOf(
                SfSpriteFrame(listOf(14, 68, 15, 21), listOf(7, 10)),
                SfSpriteFrame(listOf(38, 70, 27, 23), listOf(13, 11)),
                SfSpriteFrame(listOf(73, 70, 27, 23), listOf(13, 11)),
                SfSpriteFrame(listOf(106, 66, 32, 31), listOf(16, 15)),
            ),
            listOf(
                SfSpriteFrame(listOf(160, 68, 15, 21), listOf(7, 10)),
                SfSpriteFrame(listOf(185, 70, 27, 23), listOf(13, 11)),
                SfSpriteFrame(listOf(222, 70, 27, 23), listOf(13, 11)),
                SfSpriteFrame(listOf(255, 66, 32, 31), listOf(16, 15)),
            ),
        ),
    ),

    hudImage = "hud.png",
    healthBar = listOf(16, 18, 145, 11),
    koWhite = listOf(161, 16, 32, 14),
    koBlack = listOf(161, 1, 32, 14),
    nameTags = mapOf(
        SfFighterId.RYU to listOf(16, 56, 28, 9),
        SfFighterId.KEN to listOf(128, 56, 30, 9),
    ),
    timeDigits = SfDigitStrip(x0 = 16, dx = 16, y = 32, w = 14, h = 16),
    timeDigitsFlash = SfDigitStrip(x0 = 16, dx = 16, y = 192, w = 14, h = 16),
    scoreDigits = SfDigitStrip(x0 = 17, dx = 12, y = 101, w = 10, h = 10),
    scoreLetterP = listOf(17, 125, 10, 10),

    winnerImage = "winnerText.png",
    // Prankedy no tiene fila en winnerText.png (asset SF): al ganar él no se dibuja texto.
    // El "PRANKEDY WINS" llegará con el POW_THEME.
    winnerRows = mapOf(SfFighterId.RYU to 0, SfFighterId.KEN to 1),
    winnerSrcHeight = 9,
    winnerRowStride = 11,
    winnerSrcWidth = 70,
)
