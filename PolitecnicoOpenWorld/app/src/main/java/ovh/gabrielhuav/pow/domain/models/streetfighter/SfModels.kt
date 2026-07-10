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
 * Identidad del peleador; jsonAsset apunta a su frame data en assets.
 * `isAlpha` = personaje POW con poses APROXIMADAS (generadas desde su set NPC con
 * tools/gen_sf_frames_from_npc.py + pack_sf_character.py); se marca en el selector.
 */
enum class SfFighterId(
    val displayName: String,
    val shortName: String,   // para la FUENTE del HUD (tag de nombre y "<X> WINS"); solo A-Z/0-9/espacio
    val jsonAsset: String,
    val spriteAsset: String,
    val isAlpha: Boolean = false,
) {
    RYU("Ryu", "RYU", "STREETFIGHTER/DATA/ryu.json", "STREETFIGHTER/IMAGES/Ryu.png"),
    KEN("Ken", "KEN", "STREETFIGHTER/DATA/ken.json", "STREETFIGHTER/IMAGES/Ken.png"),
    // 🆕 Peleadores PROPIOS de POW. Prankedy trae frames proj-* propios (broma del tanque).
    PRANKEDY("Prankedy", "PRANKEDY", "STREETFIGHTER/DATA/prankedy.json", "STREETFIGHTER/IMAGES/Prankedy.png", isAlpha = true),
    SENOR_TIENDA("El Señor de la Tienda", "TIENDA", "STREETFIGHTER/DATA/senortienda.json", "STREETFIGHTER/IMAGES/SenorTienda.png", isAlpha = true),
    PAPARAZZI_1("Paparazzi 1", "PAPZ 1", "STREETFIGHTER/DATA/paparazzi1.json", "STREETFIGHTER/IMAGES/Paparazzi1.png", isAlpha = true),
    PAPARAZZI_5("Paparazzi 5", "PAPZ 5", "STREETFIGHTER/DATA/paparazzi5.json", "STREETFIGHTER/IMAGES/Paparazzi5.png", isAlpha = true),
    REY_GRUPERO("Rey Grupero", "GRUPERO", "STREETFIGHTER/DATA/reygrupero.json", "STREETFIGHTER/IMAGES/ReyGrupero.png", isAlpha = true),
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
)

/** Paso de animación: frame + delay en FRAMES (0 = FREEZE, -1 = TRANSITION). */
data class SfAnimFrame(val frameKey: String, val delay: Int)

/** Frame data completo de un peleador (cargado del JSON). */
data class SfFighterData(
    val frames: Map<String, SfFrameDef>,
    val animations: Map<String, List<SfAnimFrame>>,
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
