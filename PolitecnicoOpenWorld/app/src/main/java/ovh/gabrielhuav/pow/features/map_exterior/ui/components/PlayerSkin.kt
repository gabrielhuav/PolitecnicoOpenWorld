package ovh.gabrielhuav.pow.features.map_exterior.ui.components

/**
 * Define cada skin disponible para el jugador principal.
 *
 * Convención de assets (todos en assets/SPRITES/PLAYER/):
 *   <skinFolder>Idle/   →  <skinPrefix>i_<frame>.webp   (frames 1–idleFrames)
 *   <skinFolder>Walk/   →  <skinPrefix>w_<frame>.webp   (frames 1–walkFrames)
 *   <skinFolder>Run/    →  <skinPrefix>r_<frame>.webp   (frames 1–runFrames)
 *   <skinFolder>Special/→  <skinPrefix>s_<frame>.webp   (frames 1–specialFrames)
 *
 * Para agregar una skin nueva:
 *   1. Coloca los sprites en assets/SPRITES/PLAYER/<skinFolder>{Idle|Walk|Run|Special}/
 *   2. Agrega una entrada al enum con sus valores correspondientes.
 */
enum class PlayerSkin(
    val displayName: String,
    /** Subcarpeta dentro de la carpeta base; para los nuevos NPC termina en "/" (p.ej. "SenorTienda/") */
    val skinFolder: String,
    /** Prefijo del archivo antes de la letra de acción, p.ej. "lazaro_" */
    val skinPrefix: String,
    /**
     * Carpeta BASE de los sprites. Por defecto `SPRITES/PLAYER/`. Los personajes NUEVOS de prueba
     * (Señor de la Tienda, Rey de las Bromas, Pepe del Rey) viven bajo `SPRITES/NPC/` con el
     * `skinFolder` terminado en "/" (p.ej. "SenorTienda/") para anidar Idle/Walk/Run/Special, igual
     * que Prankedy. Así `${basePath}${skinFolder}Idle/` = "SPRITES/NPC/SenorTienda/Idle/".
     */
    val basePath: String = "SPRITES/PLAYER/",
    val idleFrames: Int = 25,
    val walkFrames: Int = 25,
    val runFrames: Int = 25,
    val specialFrames: Int = 25,
    /** Ruta al sprite de previsualización (idle frame 1) */
    val previewAsset: String = "${basePath}${skinFolder}Idle/${skinPrefix}i_1.webp",
    /**
     * Sufijo del cómic del Modo Historia para los paneles que cambian según la skin
     * (IntroPOW9/10/11/15). "" = panel por defecto (Lázaro/hombre). Ej.: con "Girl" se usa
     * `IntroPOW9Girl.webp`; con "Robot" se usa `IntroPOW9Robot.webp`. Ver StoryIntroScreen.
     */
    val comicSuffix: String = "",
    /**
     * Factor de escala visual del sprite a pie (mapa exterior). Compensa skins cuyo
     * personaje ocupa poca fraccion del lienzo (mucho margen transparente) y por eso
     * se veian MAS PEQUENAS que el resto. 1f = sin cambio.
     */
    val renderScale: Float = 1f,
    /**
     * Fracción vertical opaca (alto del personaje / alto del lienzo) del frame 1 de CAMINAR,
     * PRECALCULADA. La usan el INTERIOR (InteriorPlayerViews) y el selector de personaje
     * (NewGameCharacterDialog) como referencia de tamaño. En el mapa EXTERIOR ahora se usan las
     * fracciones POR ACCIÓN de abajo. Si cambias el arte de caminar, vuelve a medirla.
     */
    val walkBodyFraction: Float = 0.6f,
    /**
     * Fracciones opacas PRECALCULADAS por animación (alto del personaje / alto del lienzo, promedio
     * de la animación). Con ellas el CUERPO mide SIEMPRE lo mismo en pantalla (PLAYER_BODY_STANDARD_DP)
     * sin importar la skin ni la animación. Antes el exterior medía la fracción en runtime (async, con
     * fallback 0.6 y desfase de un frame): Lázaro/Robot —que varían MUCHO entre idle/caminar/correr—
     * se "encogían" al correr. Estas son estáticas y deterministas.
     * ⚠️ Los assets NO son uniformes (lienzos 256² / 338×422 / 362×640 / 542×681…): por eso hay que
     * medir cada animación por separado. Lo ideal sería re-exportar TODOS con un lienzo y una fracción
     * de cuerpo comunes (ver 09 §5). <=0f ⇒ usa walkBodyFraction como respaldo.
     */
    val idleBodyFraction: Float = -1f,
    val runBodyFraction: Float = -1f,
    val specialBodyFraction: Float = -1f
) {
    LAZARO(
        displayName = "Lázaro",
        skinFolder  = "lazaro",
        skinPrefix  = "lazaro_",
        idleFrames   = 6,   // tienes 6
        walkFrames   = 6,   // tienes 6
        specialFrames = 8,  // tienes 8
        comicSuffix = "",   // panel por defecto (hombre)
        walkBodyFraction = 0.61f,
        idleBodyFraction = 0.583f, runBodyFraction = 0.473f, specialBodyFraction = 0.544f
    ),

    escomgirl(
        displayName = "Estudianta",
        skinFolder  = "escomgirl",
        skinPrefix  = "escomgirl_",
        idleFrames   = 6,   // tienes 6
        walkFrames   = 5,   // tienes 5
        runFrames    = 4,   // tienes 4
        specialFrames = 6,  // tienes 6
        comicSuffix = "Girl",
        walkBodyFraction = 0.94f,
        idleBodyFraction = 0.953f, runBodyFraction = 0.932f, specialBodyFraction = 0.909f
    ),
    robot(
        displayName = "Robot Estudiantx",
        skinFolder  = "robot",
        skinPrefix  = "robot_",
        comicSuffix = "Robot",
        walkBodyFraction = 0.62f,
        idleBodyFraction = 0.710f, runBodyFraction = 0.679f, specialBodyFraction = 0.650f
    ),
    escomboy(
        displayName = "Estudiante",
        skinFolder  = "escomboy",
        skinPrefix  = "escomboy_",
        idleFrames   = 16,  // tienes 16
        walkFrames   = 25,  // tienes 25
        runFrames    = 16,  // tienes 16
        specialFrames = 16, // tienes 16
        comicSuffix = "Boy", // sin assets IntroPOW*Boy → cae al panel por defecto (hombre)
        renderScale = 1.8f,  // (legado) ya NO se usa para el cuerpo en exterior; ahora normaliza el estándar
        walkBodyFraction = 0.41f,
        idleBodyFraction = 0.406f, runBodyFraction = 0.407f, specialBodyFraction = 0.412f
    ),

    // ── 🆕 PERSONAJES NUEVOS (sprites bajo SPRITES/NPC/<Char>/, recortados con
    //     tools/slice_new_character_sprites.py). Añadidos TEMPORALMENTE como skins de prueba para
    //     validar sus animaciones en el selector de personaje. Sin paneles de cómic (comicSuffix="").
    //     El recorte es a escala uniforme (la figura llena ~0.865 del lienzo en TODAS las animaciones),
    //     así que con una sola walkBodyFraction=0.865 miden igual en idle/caminar/correr/especial. ───
    SENOR_TIENDA(
        displayName = "Señor de la Tienda",
        skinFolder  = "SenorTienda/",
        skinPrefix  = "st_",
        basePath    = "SPRITES/NPC/",
        idleFrames = 3, walkFrames = 4, runFrames = 8, specialFrames = 3,  // special = ESCOBAZO (golpe)
        walkBodyFraction = 0.865f
    ),
    // 🚫 DESACTIVADOS del selector y de la historia (decisión de diseño). Assets CONSERVADOS en
    //    SPRITES/NPC/ReyBromas|PepeRey por si se reusan. Para reactivar: descomenta este bloque
    //    y sus entradas en SkinSelectorDialog.
    /*
    REY_BROMAS(
        displayName = "El Rey de las Bromas",
        skinFolder  = "ReyBromas/",
        skinPrefix  = "rb_",
        basePath    = "SPRITES/NPC/",
        idleFrames = 3, walkFrames = 6, runFrames = 8, specialFrames = 4,  // special = bromas (spray/megáfono/globo)
        walkBodyFraction = 0.865f
    ),
    PEPE_REY(
        displayName = "Pepe del Rey de las Bromas",
        skinFolder  = "PepeRey/",
        skinPrefix  = "pr_",
        basePath    = "SPRITES/NPC/",
        idleFrames = 3, walkFrames = 4, runFrames = 8, specialFrames = 3,  // special = BROMA CON TANQUE (golpe)
        walkBodyFraction = 0.865f
    ),
    */
    // PRANKEDY jugable: reutiliza sus sprites (copiados de SPRITES/NPC/Prankedy/ a la convención de
    // skin en SPRITES/NPC/PrankedyPlayable/). Lienzos 512² uniformes → fracciones por acción medidas.
    PRANKEDY(
        displayName = "Prankedy",
        skinFolder  = "PrankedyPlayable/",
        skinPrefix  = "pk_",
        basePath    = "SPRITES/NPC/",
        idleFrames = 3, walkFrames = 9, runFrames = 8, specialFrames = 5,
        walkBodyFraction = 0.740f,
        idleBodyFraction = 0.717f, runBodyFraction = 0.736f, specialBodyFraction = 0.711f
    ),

    // ── 🆕 5 PERSONAJES (paparazzis, policías, paramédico) — recortados con tools/_slice5.py.
    //     Recorte uniforme (figura ~0.865 del lienzo) → walkBodyFraction=0.865. Sin cómic. Solo dev.
    //     special: foto / disparo / golpe con escudo / comunicar por radio. ───────────────────────
    PAPARAZZI_N1(
        displayName = "Paparazzi #1",
        skinFolder  = "PaparazziN1/",
        skinPrefix  = "pn1_",
        basePath    = "SPRITES/NPC/",
        idleFrames = 3, walkFrames = 6, runFrames = 10, specialFrames = 4,  // special = TOMAR FOTO
        walkBodyFraction = 0.865f
    ),
    PAPARAZZI_N5(
        displayName = "Paparazzi #5",
        skinFolder  = "PaparazziN5/",
        skinPrefix  = "pn5_",
        basePath    = "SPRITES/NPC/",
        idleFrames = 3, walkFrames = 7, runFrames = 10, specialFrames = 4,  // special = TOMAR FOTO
        walkBodyFraction = 0.865f
    ),
    POLICIA_CDMX(
        displayName = "Policía CDMX",
        skinFolder  = "PoliciaCDMX/",
        skinPrefix  = "pcd_",
        basePath    = "SPRITES/NPC/",
        idleFrames = 4, walkFrames = 7, runFrames = 11, specialFrames = 3,  // special = DISPARAR (3 frames)
        walkBodyFraction = 0.865f
    ),
    GRANADERO(
        displayName = "Granadero",
        skinFolder  = "Granaderos/",
        skinPrefix  = "gra_",
        basePath    = "SPRITES/NPC/",
        idleFrames = 3, walkFrames = 8, runFrames = 11, specialFrames = 4,  // special = GOLPE CON ESCUDO (sheet real de granaderos antimotines)
        walkBodyFraction = 0.865f
    ),
    PARAMEDICO(
        displayName = "Paramédico",
        skinFolder  = "Paramedico/",
        skinPrefix  = "pmd_",
        basePath    = "SPRITES/NPC/",
        idleFrames = 4, walkFrames = 6, runFrames = 8, specialFrames = 3,  // special = COMUNICAR POR RADIO
        walkBodyFraction = 0.865f
    ),
    // ── 🆕 NPCs de INTERIOR (Modo Historia): estudiantes IPN + docentes, recortados a
    //    SPRITES/NPC/NPCS/. Solo se usan como NPCs ambientales (InteriorNpcView/AMBIENT_SKINS);
    //    NO seleccionables (van en devOnlySkins). Recorte uniforme → walkBodyFraction 0.86.
    IPN_1(
        displayName = "Estudiante IPN 1",
        skinFolder  = "Ipn1/",
        skinPrefix  = "ipn1_",
        basePath    = "SPRITES/NPC/NPCS/NPCSIPN/",
        idleFrames = 1, walkFrames = 25, runFrames = 25, specialFrames = 25,  // sheet completo (estandar)
        walkBodyFraction = 0.803f
    ),
    IPN_2(
        displayName = "Estudiante IPN 2",
        skinFolder  = "Ipn2/",
        skinPrefix  = "ipn2_",
        basePath    = "SPRITES/NPC/NPCS/NPCSIPN/",
        idleFrames = 1, walkFrames = 20, runFrames = 20, specialFrames = 15,  // sheet completo (estandar)
        walkBodyFraction = 0.803f
    ),
    IPN_3(
        displayName = "Estudiante IPN 3",
        skinFolder  = "Ipn3/",
        skinPrefix  = "ipn3_",
        basePath    = "SPRITES/NPC/NPCS/NPCSIPN/",
        idleFrames = 1, walkFrames = 16, runFrames = 16, specialFrames = 20,  // sheet completo (estandar)
        walkBodyFraction = 0.803f
    ),
    IPN_4(
        displayName = "Estudiante IPN 4",
        skinFolder  = "Ipn4/",
        skinPrefix  = "ipn4_",
        basePath    = "SPRITES/NPC/NPCS/NPCSIPN/",
        idleFrames = 1, walkFrames = 20, runFrames = 20, specialFrames = 20,  // sheet completo (estandar)
        walkBodyFraction = 0.803f
    ),
    IPN_5(
        displayName = "Estudiante IPN 5",
        skinFolder  = "Ipn5/",
        skinPrefix  = "ipn5_",
        basePath    = "SPRITES/NPC/NPCS/NPCSIPN/",
        idleFrames = 1, walkFrames = 20, runFrames = 20, specialFrames = 20,  // sheet completo (estandar)
        walkBodyFraction = 0.803f
    ),
    IPN_6(
        displayName = "Estudiante IPN 6",
        skinFolder  = "Ipn6/",
        skinPrefix  = "ipn6_",
        basePath    = "SPRITES/NPC/NPCS/NPCSIPN/",
        idleFrames = 1, walkFrames = 20, runFrames = 20, specialFrames = 20,  // sheet completo (estandar)
        walkBodyFraction = 0.803f
    ),
    // NPC generico aleatorio (interior + exterior), recortado al estandar como los IPN.
    RND_1(
        displayName = "NPC Random 1",
        skinFolder  = "Random1/",
        skinPrefix  = "rnd1_",
        basePath    = "SPRITES/NPC/NPCS/",
        idleFrames = 1, walkFrames = 16, runFrames = 16, specialFrames = 16,  // sheet completo (estandar)
        walkBodyFraction = 0.803f
    ),
    DOC_1(
        displayName = "Docente 1",
        skinFolder  = "Doc1/",
        skinPrefix  = "doc1_",
        basePath    = "SPRITES/NPC/NPCS/",
        idleFrames = 1, walkFrames = 16, runFrames = 16, specialFrames = 19,  // sheet completo (estandar)
        walkBodyFraction = 0.803f
    ),
    EST_H1(
        displayName = "EST_H1",
        skinFolder  = "EstH1/",
        skinPrefix  = "esth1_",
        basePath    = "SPRITES/NPC/NPCS/",
        idleFrames = 1, walkFrames = 16, runFrames = 16, specialFrames = 16,  // estudiante (estandar)
        walkBodyFraction = 0.803f
    ),
    EST_M1(
        displayName = "EST_M1",
        skinFolder  = "EstM1/",
        skinPrefix  = "estm1_",
        basePath    = "SPRITES/NPC/NPCS/",
        idleFrames = 1, walkFrames = 16, runFrames = 16, specialFrames = 16,  // estudiante (estandar)
        walkBodyFraction = 0.803f
    ),
    // ── Agrega aquí nuevas skins ──────────────────────────────────────────
    // Ejemplo con una skin "Ana":
    //
    // ANA(
    //     displayName = "Ana",
    //     skinFolder  = "ana",
    //     skinPrefix  = "ana_"
    // ),
    // ─────────────────────────────────────────────────────────────────────
    ;

    fun idlePath(frame: Int)   = "${basePath}${skinFolder}Idle/${skinPrefix}i_$frame.webp"
    fun walkPath(frame: Int)   = "${basePath}${skinFolder}Walk/${skinPrefix}w_$frame.webp"
    fun runPath(frame: Int)    = "${basePath}${skinFolder}Run/${skinPrefix}r_$frame.webp"
    fun specialPath(frame: Int)= "${basePath}${skinFolder}Special/${skinPrefix}s_$frame.webp"

    /** Fracción opaca PRECALCULADA de esta acción (respaldo: walkBodyFraction si es <=0f). */
    fun bodyFraction(action: PlayerAction): Float = when (action) {
        PlayerAction.IDLE    -> idleBodyFraction
        PlayerAction.WALK    -> walkBodyFraction
        PlayerAction.RUN     -> runBodyFraction
        PlayerAction.SPECIAL -> specialBodyFraction
    }.let { if (it > 0f) it else walkBodyFraction }

    companion object {
        /**
         * Estándar ÚNICO de tamaño del jugador a pie (mapa exterior): alto en pantalla, en dp, que
         * debe ocupar el CUERPO (parte opaca) del personaje, IGUAL para TODAS las skins y TODAS las
         * animaciones. Referencia = hombre/robot (coincide con la normalización del interior, ver 09 §5).
         * Sube/baja este único valor para agrandar/encoger a TODOS por igual.
         */
        const val PLAYER_BODY_STANDARD_DP = 23.5f
    }
}
