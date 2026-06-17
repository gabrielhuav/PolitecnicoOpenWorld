package ovh.gabrielhuav.pow.features.map_exterior.ui.components

/**
 * Define cada skin disponible para el jugador principal.
 *
 * Convención de assets (todos en assets/MAIN/):
 *   <skinFolder>Idle/   →  <skinPrefix>i_<frame>.webp   (frames 1–idleFrames)
 *   <skinFolder>Walk/   →  <skinPrefix>w_<frame>.webp   (frames 1–walkFrames)
 *   <skinFolder>Run/    →  <skinPrefix>r_<frame>.webp   (frames 1–runFrames)
 *   <skinFolder>Special/→  <skinPrefix>s_<frame>.webp   (frames 1–specialFrames)
 *
 * Para agregar una skin nueva:
 *   1. Coloca los sprites en assets/MAIN/<skinFolder>{Idle|Walk|Run|Special}/
 *   2. Agrega una entrada al enum con sus valores correspondientes.
 */
enum class PlayerSkin(
    val displayName: String,
    /** Subcarpeta dentro de MAIN/, sin barra final */
    val skinFolder: String,
    /** Prefijo del archivo antes de la letra de acción, p.ej. "lazaro_" */
    val skinPrefix: String,
    val idleFrames: Int = 25,
    val walkFrames: Int = 25,
    val runFrames: Int = 25,
    val specialFrames: Int = 25,
    /** Ruta al sprite de previsualización (idle frame 1) */
    val previewAsset: String = "MAIN/${skinFolder}Idle/${skinPrefix}i_1.webp",
    /**
     * Sufijo del cómic del Modo Historia para los paneles que cambian según la skin
     * (IntroPOW9/10/11/15). "" = panel por defecto (Lázaro/hombre). Ej.: con "Girl" se usa
     * `IntroPOW9Girl.webp`; con "Robot" se usa `IntroPOW9Robot.webp`. Ver StoryIntroScreen.
     */
    val comicSuffix: String = ""
) {
    LAZARO(
        displayName = "Lázaro",
        skinFolder  = "lazaro",
        skinPrefix  = "lazaro_",
        idleFrames   = 6,   // tienes 6
        walkFrames   = 6,   // tienes 6
        specialFrames = 8,  // tienes 8
        comicSuffix = ""    // panel por defecto (hombre)
    ),

    escomgirl(
        displayName = "Estudianta",
        skinFolder  = "escomgirl",
        skinPrefix  = "escomgirl_",
        idleFrames   = 6,   // tienes 6
        walkFrames   = 5,   // tienes 5
        runFrames    = 4,   // tienes 4
        specialFrames = 6,  // tienes 6
        comicSuffix = "Girl"
    ),
    robot(
        displayName = "Robot Estudiantx",
        skinFolder  = "robot",
        skinPrefix  = "robot_",
        comicSuffix = "Robot"
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

    fun idlePath(frame: Int)   = "MAIN/${skinFolder}Idle/${skinPrefix}i_$frame.webp"
    fun walkPath(frame: Int)   = "MAIN/${skinFolder}Walk/${skinPrefix}w_$frame.webp"
    fun runPath(frame: Int)    = "MAIN/${skinFolder}Run/${skinPrefix}r_$frame.webp"
    fun specialPath(frame: Int)= "MAIN/${skinFolder}Special/${skinPrefix}s_$frame.webp"
}
