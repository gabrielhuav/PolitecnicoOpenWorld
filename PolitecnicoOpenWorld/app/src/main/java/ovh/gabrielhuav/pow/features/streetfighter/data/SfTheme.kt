package ovh.gabrielhuav.pow.features.streetfighter.data

import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength

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

/** Fondo de escenario POW: archivo en imagesDir + nombre para el SELECTOR de mapa. */
data class SfStageBg(val file: String, val name: String)

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
    /**
     * 🆕 FONDOS COMPLETOS de POW: si la lista NO está vacía, el jugador ELIGE el mapa en
     * el selector (paso 2, con opción "Al azar"); se dibuja a pantalla completa con
     * parallax de cámara y NO se pintan las capas del escenario clásico (barco/bandera…).
     * Se decodifica SOLO el elegido (no van en imageFiles).
     */
    val fullBackgrounds: List<SfStageBg>,
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
    val timeDigits: SfDigitStrip,
    val timeDigitsFlash: SfDigitStrip,
    /**
     * 🆕 FUENTE arcade del HUD (recortes A-Z/0-9 dentro del sheet del HUD, ~10×10):
     * con ella se dibujan los TAGS de nombre, los marcadores P1/P2 y el
     * "<PERSONAJE> WINS" de CUALQUIER peleador (adiós winnerText.png por filas).
     * Avance fijo de 12 px por carácter; espacio = hueco.
     */
    val letterFont: Map<Char, List<Int>>,
)

/** Tema ACTUAL: assets del clon SF (temporal; se reemplazará por el tema POW). */
val SF_CLASSIC_THEME = SfTheme(
    imagesDir = "STREETFIGHTER/IMAGES/",
    soundsDir = "STREETFIGHTER/SOUNDS/",
    // ⚠️ (2026-07-15) SIN Ryu.png/Ken.png: las hojas de PELEADOR ya no se precargan aquí — las
    // resuelve SfSharedSheets por peleador elegido. Además Ryu/Ken viven SOLO en el source set
    // DEBUG (app/src/debug/assets/): precargarlos aquí CRASHEARÍA el release de Play Store.
    imageFiles = listOf("kenstage.png", "shadow.png", "decals.png", "hud.png"),
    soundKeys = listOf(
        "light-attack", "medium-attack", "heavy-attack",
        "light-punch-hit", "medium-punch-hit", "heavy-punch-hit",
        "light-kick-hit", "medium-kick-hit", "heavy-kick-hit",
        "land", "hadouken",
    ),
    // Música de Prankedy (Persecución, la de sus videos) en vez del tema del clon SF
    musicFile = "prankedy-persecucion.mp3",
    musicVolume = 0.3f,

    // Fondos POW (IPN/UNAM), elegibles en el selector de mapa; el muelle SF queda de fallback
    fullBackgrounds = listOf(
        SfStageBg("fondo_IPN_ESCOM_1.png", "ESCOM"),
        SfStageBg("fondo_IPN_QUESO_1.png", "Queso IPN"),
        SfStageBg("fondo_IPN_ESIME_AZC_1.png", "ESIME Azcapotzalco"),
        SfStageBg("fondo_IPN_cecyt9_1.png", "CECyT 9"),
        SfStageBg("fondo_IPN_cecyt2_1.png", "CECyT 2"),
        SfStageBg("fondo_UNAM_bibliotecaCentral_1.png", "Biblioteca UNAM"),
    ),
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
    timeDigits = SfDigitStrip(x0 = 16, dx = 16, y = 32, w = 14, h = 16),
    timeDigitsFlash = SfDigitStrip(x0 = 16, dx = 16, y = 192, w = 14, h = 16),
    // Abecedario + dígitos del hud.png (coordenadas del StatusBar.js original)
    letterFont = buildMap {
        // Dígitos (fila y=101, paso 12; el '4' mide 11)
        for (d in 0..9) put('0' + d, listOf(17 + 12 * d, 101, if (d == 4) 11 else 10, 10))
        // A-O (fila y=113)
        put('A', listOf(29, 113, 11, 10)); put('B', listOf(41, 113, 10, 10))
        put('C', listOf(53, 113, 10, 10)); put('D', listOf(65, 113, 10, 10))
        put('E', listOf(77, 113, 10, 10)); put('F', listOf(89, 113, 10, 10))
        put('G', listOf(101, 113, 10, 10)); put('H', listOf(113, 113, 10, 10))
        put('I', listOf(125, 113, 9, 10)); put('J', listOf(136, 113, 10, 10))
        put('K', listOf(149, 113, 10, 10)); put('L', listOf(161, 113, 10, 10))
        put('M', listOf(173, 113, 10, 10)); put('N', listOf(185, 113, 11, 10))
        put('O', listOf(197, 113, 10, 10))
        // P-Z (fila y=125)
        put('P', listOf(17, 125, 10, 10)); put('Q', listOf(29, 125, 10, 10))
        put('R', listOf(41, 125, 10, 10)); put('S', listOf(53, 125, 10, 10))
        put('T', listOf(65, 125, 10, 10)); put('U', listOf(77, 125, 10, 10))
        put('V', listOf(89, 125, 10, 10)); put('W', listOf(101, 125, 10, 10))
        put('X', listOf(113, 125, 10, 10)); put('Y', listOf(125, 125, 10, 10))
        put('Z', listOf(136, 125, 10, 10))
    },
)
