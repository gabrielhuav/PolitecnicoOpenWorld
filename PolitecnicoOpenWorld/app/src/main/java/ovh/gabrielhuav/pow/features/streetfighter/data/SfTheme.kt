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
    // 🆕 kenstage.png ELIMINADO (copyright). hud.png (SF original) REEMPLAZADO por sf_hud_pow.png
    // (fuente + barra + KO + timer POW, generado con GPT). Ver GUIA / build_hud.
    imageFiles = listOf("shadow.png", "sf_decals_pow.png", "sf_hud_pow.png"),
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
    // 🆕 Fondos POW. Los "_anim.png" son ATLAS de frames (con su JSON) generados por
    // tools/build_map_backgrounds.py: se animan en el draw loop (un solo bitmap, ping-pong
    // embebido). Los "_1.png" son fotos fijas (escenarios sin video). El loader detecta cuál
    // es cada uno por el sufijo "_anim". Los 6 originales IPN/UNAM ahora usan su versión
    // ANIMADA (los antiguos fondo_IPN_*/fondo_UNAM_* quedan sin uso).
    fullBackgrounds = listOf(
        // ---- IPN (animados) ----
        SfStageBg("fondo_escom_anim.png", "ESCOM"),
        SfStageBg("fondo_escom_noche_1_anim.png", "ESCOM (Noche)"),
        SfStageBg("fondo_escom_noche_2_anim.png", "ESCOM (Noche 2)"),
        SfStageBg("fondo_queso_ipn_anim.png", "Queso IPN"),
        SfStageBg("fondo_queso_ipn_noche_1_anim.png", "Queso IPN (Noche)"),
        SfStageBg("fondo_queso_ipn_noche_2_anim.png", "Queso IPN (Noche 2)"),
        SfStageBg("fondo_esime_azc_anim.png", "ESIME Azcapotzalco"),
        SfStageBg("fondo_esime_azc_noche_1_anim.png", "ESIME Azcapotzalco (Noche)"),
        SfStageBg("fondo_esime_azc_noche_2_anim.png", "ESIME Azcapotzalco (Noche 2)"),
        SfStageBg("fondo_cecyt_9_anim.png", "CECyT 9"),
        SfStageBg("fondo_cecyt_9_noche_1_anim.png", "CECyT 9 (Noche)"),
        SfStageBg("fondo_cecyt_9_noche_2_anim.png", "CECyT 9 (Noche 2)"),
        SfStageBg("fondo_cecyt_2_anim.png", "CECyT 2"),
        SfStageBg("fondo_cecyt_2_noche_1_anim.png", "CECyT 2 (Noche)"),
        SfStageBg("fondo_cecyt_2_noche_2_anim.png", "CECyT 2 (Noche 2)"),
        // ---- UNAM (animados) ----
        SfStageBg("fondo_unam_biblioteca_cu_anim.png", "Ciudad Universitaria UNAM"),
        SfStageBg("fondo_unam_biblioteca_cu_noche_1_anim.png", "CU UNAM (Noche)"),
        SfStageBg("fondo_unam_biblioteca_cu_noche_2_anim.png", "CU UNAM (Noche 2)"),
        SfStageBg("fondo_fes_acatlan_anim.png", "FES Acatlán"),
        SfStageBg("fondo_fes_acatlan_noche_1_anim.png", "FES Acatlán (Noche)"),
        SfStageBg("fondo_fes_acatlan_noche_2_anim.png", "FES Acatlán (Noche 2)"),
        // ---- Otros escenarios (animados) ----
        SfStageBg("fondo_uam_azcapo_anim.png", "UAM Azcapotzalco"),
        SfStageBg("fondo_uam_azcapo_noche_1_anim.png", "UAM Azcapotzalco (Noche)"),
        SfStageBg("fondo_uam_azcapo_noche_2_anim.png", "UAM Azcapotzalco (Noche 2)"),
        SfStageBg("fondo_islamunecas_anim.png", "Isla de las Muñecas"),
        SfStageBg("fondo_islamunecas_noche_1_anim.png", "Isla de las Muñecas (Noche)"),
        SfStageBg("fondo_islamunecas_noche_2_anim.png", "Isla de las Muñecas (Noche 2)"),
        SfStageBg("fondo_mictlan_anim.png", "Mictlán"),
        SfStageBg("fondo_mictlan_noche_1_anim.png", "Mictlán (Noche)"),
        SfStageBg("fondo_mictlan_noche_2_anim.png", "Mictlán (Noche 2)"),
        // ---- Antes estáticos: ahora también ANIMADOS (Ken Burns desde foto, tool 2026-07-18) ----
        SfStageBg("fondo_campos_agave_jalisco_anim.png", "Campos de Agave Jalisco"),
        SfStageBg("fondo_campos_agave_jalisco_noche_1_anim.png", "Campos de Agave Jalisco (Noche)"),
        SfStageBg("fondo_campos_agave_jalisco_noche_2_anim.png", "Campos de Agave Jalisco (Noche 2)"),
        SfStageBg("fondo_facultad_medicina_anim.png", "Facultad de Medicina"),
        SfStageBg("fondo_facultad_medicina_noche_1_anim.png", "Facultad de Medicina (Noche)"),
        SfStageBg("fondo_facultad_medicina_noche_2_anim.png", "Facultad de Medicina (Noche 2)"),
        SfStageBg("fondo_fes_aragon_anim.png", "FES Aragón"),
        SfStageBg("fondo_fes_aragon_noche_1_anim.png", "FES Aragón (Noche)"),
        SfStageBg("fondo_fes_aragon_noche_2_anim.png", "FES Aragón (Noche 2)"),
        SfStageBg("fondo_piramidesol_anim.png", "Pirámide del Sol"),
        SfStageBg("fondo_piramidesol_noche_1_anim.png", "Pirámide del Sol (Noche)"),
        SfStageBg("fondo_piramidesol_noche_2_anim.png", "Pirámide del Sol (Noche 2)"),
        SfStageBg("fondo_uam_cuajimalpa_anim.png", "UAM Cuajimalpa"),
        SfStageBg("fondo_uam_cuajimalpa_noche_1_anim.png", "UAM Cuajimalpa (Noche)"),
        SfStageBg("fondo_uam_cuajimalpa_noche_2_anim.png", "UAM Cuajimalpa (Noche 2)"),
        SfStageBg("fondo_zocalo_anim.png", "Zócalo"),
        SfStageBg("fondo_zocalo_noche_1_anim.png", "Zócalo (Noche)"),
        SfStageBg("fondo_zocalo_noche_2_anim.png", "Zócalo (Noche 2)"),
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

    // (Proyectil de Ken.png ELIMINADO por copyright — cada peleador usa sus propios frames proj-*.)

    splashImage = "sf_decals_pow.png", // 🆕 chispas POW (reemplaza decals.png de SF, copyright)
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

    // 🆕 Atlas POW (sf_hud_pow.png): fuente + barra + KO + timer, generado desde las imágenes
    // de GPT (chroma-key + recorte + normalización). Reemplaza al hud.png de SF (copyright).
    hudImage = "sf_hud_pow.png",
    healthBar = listOf(2, 16, 145, 11),
    koWhite = listOf(2, 29, 32, 14),
    koBlack = listOf(42, 29, 32, 14),
    timeDigits = SfDigitStrip(x0 = 2, dx = 16, y = 45, w = 14, h = 16),
    timeDigitsFlash = SfDigitStrip(x0 = 2, dx = 16, y = 63, w = 14, h = 16),
    // Fuente arcade POW (A-Z/0-9): recortes dentro de sf_hud_pow.png (alto 10, avance 12).
    letterFont = mapOf(
        '0' to listOf(2, 2, 7, 10), '1' to listOf(11, 2, 5, 10), '2' to listOf(18, 2, 8, 10),
        '3' to listOf(28, 2, 7, 10), '4' to listOf(37, 2, 8, 10), '5' to listOf(47, 2, 7, 10),
        '6' to listOf(56, 2, 7, 10), '7' to listOf(65, 2, 8, 10), '8' to listOf(75, 2, 7, 10),
        '9' to listOf(84, 2, 7, 10),
        'A' to listOf(93, 2, 8, 10), 'B' to listOf(103, 2, 8, 10), 'C' to listOf(113, 2, 8, 10),
        'D' to listOf(123, 2, 8, 10), 'E' to listOf(133, 2, 8, 10), 'F' to listOf(143, 2, 8, 10),
        'G' to listOf(153, 2, 7, 10), 'H' to listOf(162, 2, 9, 10), 'I' to listOf(173, 2, 6, 10),
        'J' to listOf(181, 2, 8, 10), 'K' to listOf(191, 2, 8, 10), 'L' to listOf(201, 2, 8, 10),
        'M' to listOf(211, 2, 10, 10), 'N' to listOf(223, 2, 8, 10), 'O' to listOf(233, 2, 7, 10),
        'P' to listOf(242, 2, 8, 10), 'Q' to listOf(252, 2, 8, 10), 'R' to listOf(262, 2, 8, 10),
        'S' to listOf(272, 2, 7, 10), 'T' to listOf(281, 2, 8, 10), 'U' to listOf(291, 2, 8, 10),
        'V' to listOf(301, 2, 8, 10), 'W' to listOf(311, 2, 10, 10), 'X' to listOf(323, 2, 8, 10),
        'Y' to listOf(333, 2, 8, 10), 'Z' to listOf(343, 2, 8, 10),
    ),
)
