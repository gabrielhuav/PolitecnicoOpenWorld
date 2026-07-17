package ovh.gabrielhuav.pow.domain.models.streetfighter

// Modelos PUROS del modo STREET FIGHTER (port fiel de StreetFighter-main JS).
// Sin imports de Android ni de UI (contrato MVVM, ver README for IAS 01/09).
// Los sprites/animaciones/cajas por frame viven en assets/STREETFIGHTER/DATA/{ryu,ken}.json
// (generados 1:1 desde Ryu.js/Ken.js); aquí solo van constantes, enums y snapshots.

/** Constantes portadas 1:1 del JS (px y px/s del mundo virtual de 384x224). */
object SfConstants {
    // game.js
    const val FPS = 60
    const val FRAME_TIME_MS = 1000f / FPS

    // stage.js
    const val STAGE_WIDTH = 768f
    const val STAGE_HEIGHT = 256f
    const val STAGE_FLOOR = 218f
    const val STAGE_PADDING = 256f
    const val STAGE_MID_POINT = STAGE_WIDTH / 2f
    const val SCENE_WIDTH = 382f
    const val SCENE_HEIGHT = 224f
    const val SCROLL_BOUNDARY = 100f

    // fighter.js
    const val FIGHTER_START_DISTANCE = 88f
    const val FIGHTER_DEFAULT_WIDTH = 40f
    const val FIGHTER_PUSH_FRICTION = 66f
    const val FIGHTER_STRUCK_DELAY = 15 // frames (delay del 1er frame de HURT)

    // Ryu.js / Ken.js (idénticos en ambos)
    const val GRAVITY = 1000f
    const val WALK_FORWARD_VELOCITY = 180f
    const val WALK_BACKWARD_VELOCITY = -120f
    const val JUMP_FORWARD_VELOCITY = 168f
    const val JUMP_BACKWARD_VELOCITY = -180f
    const val JUMP_VELOCITY = -420f

    // battle.js
    const val HEALTH_MAX_HIT_POINTS = 200
    const val BATTLE_TIME = 99
    val TIME_DELAY_MS: Long = (40 * FRAME_TIME_MS).toLong()       // 1 "segundo" del timer
    val TIME_FLASH_DELAY_MS: Long = (3 * FRAME_TIME_MS).toLong()
    const val HEALTH_CRITICAL_HIT_POINTS = 80                     // 40% de 200
    const val HIT_SPLASH_RANDOMNESS = 10f

    // fireball.js (velocidad por fuerza)
    const val FIREBALL_VELOCITY_LIGHT = 150f
    const val FIREBALL_VELOCITY_MEDIUM = 220f
    const val FIREBALL_VELOCITY_HEAVY = 300f
}

/** Dirección de encaramiento: RIGHT=+1, LEFT=-1 (multiplica velocidades X). */
enum class SfDirection(val sign: Int) {
    RIGHT(1),
    LEFT(-1);

    fun opposite(): SfDirection = if (this == RIGHT) LEFT else RIGHT
}

/**
 * 🆕 (2026-07-15) Fuente COMPARTIDA con el mundo abierto: el peleador NO tiene sheet propio en
 * assets — su hoja se ARMA EN RUNTIME (SfSharedSheets) desde el MISMO set de sprites que usa el
 * mundo (`SPRITES/PLAYER/` o `SPRITES/NPC/`). Los sets tienen LIENZOS HETEROGÉNEOS (256²,
 * 338×422, 542×681…) e incluso escalas DISTINTAS entre animaciones del mismo personaje: el
 * código normaliza midiendo la figura POR ANIMACIÓN (mediana de alturas → ~100 px de alto,
 * pies en 128,224 del lienzo 256²), así que NO importa el tamaño fuente ni que cada acción
 * venga a otra escala (ver SfSharedSheets.normalizeAnim).
 */
data class SfSharedSet(
    /** "SPRITES/PLAYER/" o "SPRITES/NPC/" (misma convención que PlayerSkin.basePath). */
    val basePath: String,
    /** "lazaro" (PLAYER: carpetas <folder>Idle/…) o "PoliciaCDMX/" (NPC: <folder>Idle/…). */
    val folder: String,
    /** Prefijo de archivo: "<prefix>i_1.webp" / _w_ / _r_ / _s_ (igual que PlayerSkin.skinPrefix). */
    val prefix: String,
    /** true = la fuente está dibujada mirando a la IZQUIERDA → espejar (SF exige DERECHA). */
    val flip: Boolean = false,
)

/**
 * Identidad del peleador; jsonAsset apunta a su frame data en assets.
 * `isAlpha` = personaje POW con poses APROXIMADAS (derivadas de su set del mundo);
 * se marca en el selector.
 */
enum class SfFighterId(
    val displayName: String,
    val shortName: String,   // para la FUENTE del HUD (tag de nombre y "<X> WINS"); solo A-Z/0-9/espacio
    /** JSON de frame data. Para los COMPARTIDOS apunta al TEMPLATE ryu.json (cajas/timings). */
    val jsonAsset: String,
    /**
     * Sheet del peleador. Para los COMPARTIDOS es un nombre VIRTUAL "RUNTIME/<X>.png": NUNCA se
     * abre como asset — solo sirve de KEY del mapa de imágenes; el bitmap lo arma SfSharedSheets.
     */
    val spriteAsset: String,
    val isAlpha: Boolean = false,
    // PARCHE TEMPORAL (2026-07-10): algunos peleadores ALPHA se autogeneraron con las poses de GOLPE
    // (hit-*) dibujadas MÁS CHICAS dentro de su celda 256² → "encogen" al recibir daño. Este factor
    // los reescala SOLO en estados HURT (medido: figura de idle / figura de hit). 1f = sin parche.
    // TODO: quitar cuando se regeneren esos sprites al tamaño correcto (ver GUIA_generacion_assets_SF).
    val hurtScale: Float = 1f,
    /** null = sheet propio empaquetado en assets; no-null = COMPARTIDO (armado en runtime). */
    val sharedSet: SfSharedSet? = null,
) {
    // ⚠️ COPYRIGHT (2026-07-17): RYU y KEN se ELIMINARON del juego (versión POW 100% propia).
    // Sus assets quedan en el source set debug pero YA NO hay ids en el enum: si un cliente viejo
    // manda "RYU"/"KEN" por red, SfFighterId.valueOf falla y cae a PRANKEDY (parse defensivo).
    // 🆕 Peleadores PROPIOS de POW. Prankedy trae frames proj-* propios (broma del tanque).
    PRANKEDY("Prankedy", "PRANKEDY", "STREETFIGHTER/DATA/prankedy.json", "STREETFIGHTER/IMAGES/Prankedy.png"),
    SENOR_TIENDA(
        "El Señor de la Tienda", "TIENDA",
        "STREETFIGHTER/DATA/senortienda.json", "STREETFIGHTER/IMAGES/SenorTienda.png",
    ),
    PAPARAZZI_1(
        "Paparazzi 1", "PAPZ 1",
        "STREETFIGHTER/DATA/paparazzi1.json", "STREETFIGHTER/IMAGES/Paparazzi1.png",
    ),
    REY_GRUPERO(
        "Rey Grupero", "GRUPERO",
        "STREETFIGHTER/DATA/reygrupero.json", "STREETFIGHTER/IMAGES/ReyGrupero.png",
    ),
    // ── 🆕 (2026-07-15) PELEADORES COMPARTIDOS: usan los MISMOS assets del mundo abierto
    //    (SPRITES/PLAYER/ y SPRITES/NPC/) — NO tienen sheet/JSON propio en el APK. La hoja se
    //    ARMA EN RUNTIME (SfSharedSheets, cache LRU) con cajas/timings de sf_template.json.
    //    Lázaro está dibujado a la IZQUIERDA → flip=true. (Rey de las Bromas y
    //    Pepe se ELIMINARON del juego en 2026-07-16; ya no existen como personajes.)
    //    Prankedy, Señor Tienda, ambos Paparazzi, Rey Grupero, policías y estudiantes ESCOM ya tienen arte dedicado.
    PAPARAZZI_5(
        "Paparazzi 5", "PAPZ 5",
        "STREETFIGHTER/DATA/paparazzi5.json", "STREETFIGHTER/IMAGES/Paparazzi5.png",
    ),
    LAZARO(
        "Lázaro", "LAZARO", "STREETFIGHTER/DATA/sf_template.json", "RUNTIME/Lazaro.png",
        isAlpha = true, sharedSet = SfSharedSet("SPRITES/PLAYER/", "lazaro", "lazaro_", flip = true),
    ),
    ESCOMBOY(
        "Estudiante", "ESCOMBOY",
        "STREETFIGHTER/DATA/escomboy.json", "STREETFIGHTER/IMAGES/EscomBoy.png",
    ),
    ESCOMGIRL(
        "Estudianta", "ESCOMGIRL",
        "STREETFIGHTER/DATA/escomgirl.json", "STREETFIGHTER/IMAGES/EscomGirl.png",
    ),
    ROBOT(
        "Robot Estudiantx", "ROBOT",
        "STREETFIGHTER/DATA/robot.json", "STREETFIGHTER/IMAGES/Robot.png",
    ),
    POLICIA_CDMX(
        "Policía CDMX", "POLICIA",
        "STREETFIGHTER/DATA/policiacdmx.json", "STREETFIGHTER/IMAGES/PoliciaCDMX.png",
    ),
    POLICIA_CDMX_HOMBRE(
        "Policía CDMX (Hombre)", "POLICIA H",
        "STREETFIGHTER/DATA/policiacdmxhombre.json", "STREETFIGHTER/IMAGES/PoliciaCDMXHombre.png",
    ),
    PARAMEDICO_CRUZ_ROJA(
        "Paramédico Cruz Roja", "PARAMED CR",
        "STREETFIGHTER/DATA/paramedicocruzroja.json", "STREETFIGHTER/IMAGES/ParamedicoCruzRoja.png",
    ),
    POLICIA_GRANADERO_HOMBRE(
        "Policía Granadero CDMX (Hombre)", "GRANADERO H",
        "STREETFIGHTER/DATA/policiagranaderohombre.json", "STREETFIGHTER/IMAGES/PoliciaGranaderoHombre.png",
    ),
    POLICIA_GRANADERO_MUJER(
        "Policía Granadero CDMX (Mujer)", "GRANADERA",
        "STREETFIGHTER/DATA/policiagranaderomujer.json", "STREETFIGHTER/IMAGES/PoliciaGranaderoMujer.png",
    ),
    GRANADERO(
        "Granadero", "GRANADERO", "STREETFIGHTER/DATA/sf_template.json", "RUNTIME/Granadero.png",
        isAlpha = true, sharedSet = SfSharedSet("SPRITES/NPC/", "Granaderos/", "gra_"),
    ),
    PARAMEDICO(
        "Paramédico", "PARAMEDICO", "STREETFIGHTER/DATA/sf_template.json", "RUNTIME/Paramedico.png",
        isAlpha = true, sharedSet = SfSharedSet("SPRITES/NPC/", "Paramedico/", "pmd_"),
    ),
}

/** Fuerza del ataque (fighter.js FighterAttackBaseData; slide ya en px/s). */
enum class SfAttackStrength(
    val damage: Int,
    val score: Int,
    val slideVelocity: Float,
    val slideFriction: Float,
    val fireballVelocity: Float,
) {
    LIGHT(12, 100, 200f, 600f, SfConstants.FIREBALL_VELOCITY_LIGHT),
    MEDIUM(20, 300, 266.7f, 600f, SfConstants.FIREBALL_VELOCITY_MEDIUM),
    HEAVY(28, 100, 366.7f, 800f, SfConstants.FIREBALL_VELOCITY_HEAVY),
}

/** Tipo de ataque (elige el sonido de impacto). */
enum class SfAttackType { PUNCH, KICK }

/**
 * 🆕 Dificultad de la CPU offline (2026-07-16). Se elige en el flujo pre-pelea
 * (peleador → rival → DIFICULTAD → mapa); online se ignora (el rival es humano).
 * - BASICA: reacciona lento, camina mucho, solo golpes ligeros; NUNCA bloquea,
 *   salta ni lanza poderes. Para aprender los controles.
 * - NORMAL: la IA clásica del port (decisiones al azar cada ~280-620 ms).
 * - AVANZADA: REACTIVA y casi imposible: bloquea tus ataques, castiga tu
 *   recuperación, anti-aéreo, esquiva hadoukens y lanza MUCHOS poderes.
 * - PESADILLA: brutal. Ataca sin parar con COMBOS muy seguidos, ESQUIVA tus golpes
 *   (salto/dash atrás), castiga durísimo y reacciona casi al instante (~50-110 ms).
 * ⚠️ El ORDEN importa (se usa ordinal para la rampa del arcade): de más fácil a más difícil.
 */
enum class SfCpuDifficulty { BASICA, NORMAL, AVANZADA, PESADILLA }

/** Zona golpeada (hurtboxes por frame: [head, body, legs]). */
enum class SfHurtArea { HEAD, BODY, LEGS }

/**
 * Máquina de estados COMPLETA del Fighter original. `jsKey` = clave de la animación
 * en el JSON (valores de FighterState del JS).
 */
enum class SfFighterState(val jsKey: String) {
    IDLE("idle"),
    WALK_FORWARD("walkForwards"),
    WALK_BACKWARD("walkBackwards"),
    JUMP_START("jumpStart"),
    JUMP_UP("jumpUp"),
    JUMP_FORWARD("jumpForwards"),
    JUMP_BACKWARD("jumpBackwards"),
    JUMP_LAND("jumpLand"),
    CROUCH("crouch"),
    CROUCH_UP("crouchUp"),
    CROUCH_DOWN("crouchDown"),
    IDLE_TURN("idleTurn"),
    CROUCH_TURN("crouchTurn"),
    LIGHT_PUNCH("lightPunch"),
    MEDIUM_PUNCH("mediumPunch"),
    HEAVY_PUNCH("heavyPunch"),
    LIGHT_KICK("lightKick"),
    MEDIUM_KICK("mediumKick"),
    HEAVY_KICK("heavyKick"),
    HURT_HEAD_LIGHT("hurtHeadLight"),
    HURT_HEAD_MEDIUM("hurtHeadMedium"),
    HURT_HEAD_HEAVY("hurtHeadHeavy"),
    HURT_BODY_LIGHT("hurtBodyLight"),
    HURT_BODY_MEDIUM("hurtBodyMedium"),
    HURT_BODY_HEAVY("hurtBodyHeavy"),
    SPECIAL_1_LIGHT("special1Light"),
    SPECIAL_1_MEDIUM("special1Medium"),
    SPECIAL_1_HEAVY("special1Heavy"),
    VICTORY("victory"),
    KO("ko"),
}

/** Estados en los que un peleador PUEDE ser golpeado (FighterHurtStates del JS). */
val SF_HURT_STATES: Set<SfFighterState> = setOf(
    SfFighterState.IDLE, SfFighterState.IDLE_TURN,
    SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD,
    SfFighterState.JUMP_START, SfFighterState.JUMP_LAND,
    SfFighterState.LIGHT_PUNCH, SfFighterState.MEDIUM_PUNCH, SfFighterState.HEAVY_PUNCH,
    SfFighterState.LIGHT_KICK, SfFighterState.MEDIUM_KICK, SfFighterState.HEAVY_KICK,
    SfFighterState.HURT_HEAD_LIGHT, SfFighterState.HURT_HEAD_MEDIUM, SfFighterState.HURT_HEAD_HEAVY,
    SfFighterState.HURT_BODY_LIGHT, SfFighterState.HURT_BODY_MEDIUM, SfFighterState.HURT_BODY_HEAVY,
    SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM, SfFighterState.SPECIAL_1_HEAVY,
    SfFighterState.CROUCH, SfFighterState.CROUCH_UP, SfFighterState.CROUCH_DOWN,
)

/** Caja alineada a ejes relativa al ancla (pies) del peleador. */
data class SfBox(val x: Float, val y: Float, val width: Float, val height: Float) {
    /** A coordenadas absolutas espejando por dirección (collisions.js getActualBoxDimensions). */
    fun toWorld(originX: Float, originY: Float, direction: SfDirection): SfBox {
        val x1 = originX + x * direction.sign
        val x2 = x1 + width * direction.sign
        return SfBox(minOf(x1, x2), originY + y, width, height)
    }

    fun overlaps(other: SfBox): Boolean =
        x < other.x + other.width &&
            x + width > other.x &&
            y < other.y + other.height &&
            y + height > other.y

    companion object {
        fun fromList(l: List<Int>?): SfBox =
            if (l == null || l.size < 4) SfBox(0f, 0f, 0f, 0f)
            else SfBox(l[0].toFloat(), l[1].toFloat(), l[2].toFloat(), l[3].toFloat())
    }
}

/** Un frame del sprite sheet: recorte + origen + cajas (del JSON). */
data class SfFrameDef(
    val src: List<Int>,                 // [x, y, w, h] en el PNG
    val origin: List<Int>,              // [originX, originY] respecto al ancla
    val push: List<Int>? = null,        // pushbox [x, y, w, h]
    val hurt: List<List<Int>>? = null,  // [head, body, legs]
    val hit: List<Int>? = null,         // hitbox del ataque (solo frames activos)
    val flipX: Boolean = false,          // corrige orientación interna (p. ej. giro al quedar tendido en KO)
)

/** Paso de animación: frame + delay en FRAMES (0 = FREEZE, -1 = TRANSITION). */
data class SfAnimFrame(val frameKey: String, val delay: Int)

/** Evento de salida del efecto especial, definido por personaje y fuerza en su JSON. */
data class SfProjectileEvent(
    val animationFrame: Int = 3,
    val offsetX: Float = 76f,
    val offsetY: Float = -57f,
    val visualScale: Float = 1f,
)

/** Frame data completo de un peleador (cargado del JSON). */
data class SfFighterData(
    val frames: Map<String, SfFrameDef>,
    val animations: Map<String, List<SfAnimFrame>>,
    val projectileEvents: Map<SfAttackStrength, SfProjectileEvent> = emptyMap(),
)

/** Snapshot inmutable de un peleador; se actualiza SIEMPRE con copy(...) en el VM. */
data class SfFighter(
    val id: SfFighterId,
    val playerIndex: Int,               // 0 = jugador, 1 = CPU
    val x: Float,
    val y: Float = SfConstants.STAGE_FLOOR,
    val velocityX: Float = 0f,
    val velocityY: Float = 0f,
    val direction: SfDirection,
    val state: SfFighterState = SfFighterState.IDLE,
    val animationFrame: Int = 0,
    val animationTimerMs: Long = 0L,    // instante en el que avanza el siguiente frame
    val hitPoints: Int = SfConstants.HEALTH_MAX_HIT_POINTS,
    val slideVelocity: Float = 0f,
    val slideFriction: Float = 0f,
    val attackStruck: Boolean = false,  // el ataque actual ya conectó
    val fireballFired: Boolean = false, // el hadouken de este special ya salió
    val victory: Boolean = false,
) {
    val isAirborne: Boolean
        get() = state == SfFighterState.JUMP_UP ||
            state == SfFighterState.JUMP_FORWARD ||
            state == SfFighterState.JUMP_BACKWARD
}

/** Estado de un hadouken en vuelo (Fireball.js). */
enum class SfFireballState(val jsKey: String) { ACTIVE("active"), COLLIDED("collided") }

data class SfFireball(
    val ownerIndex: Int,
    val x: Float,
    val y: Float,
    val direction: SfDirection,
    val strength: SfAttackStrength,
    val velocity: Float,
    val state: SfFireballState = SfFireballState.ACTIVE,
    val animationFrame: Int = 0,
    val animationTimerMs: Long = 0L,
)

/** Splash de impacto (decals.png; 4 frames, avanza cada 4 FRAME_TIME). */
data class SfHitSplash(
    val x: Float,
    val y: Float,
    val playerId: Int,                  // elige la fila de frames (color)
    val strength: SfAttackStrength,
    val animationFrame: Int = 0,
    val animationTimerMs: Long = 0L,
)

/** Entrada de UN peleador para UN tick (equivale a los control.isX del JS). */
data class SfInput(
    val up: Boolean = false,
    val down: Boolean = false,
    val forward: Boolean = false,   // relativo al encaramiento
    val backward: Boolean = false,
    val lightPunch: Boolean = false,
    val mediumPunch: Boolean = false,
    val heavyPunch: Boolean = false,
    val lightKick: Boolean = false,
    val mediumKick: Boolean = false,
    val heavyKick: Boolean = false,
    val special: SfAttackStrength? = null, // hadouken detectado (↓ ↘ → + puño)
)
