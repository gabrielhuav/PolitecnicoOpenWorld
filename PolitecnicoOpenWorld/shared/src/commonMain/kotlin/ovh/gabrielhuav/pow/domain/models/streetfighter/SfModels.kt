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
    // 🆕 (2026-07-22, Fase 2b) Límites X del peleador en el MUNDO del stage (24 px de margen
    // sobre el padding). Movidos del companion del VM para que SfPhysics.clampToStage sea puro.
    // `const` (detekt MayBeConst): son expresiones constantes de `const val` + literal.
    const val STAGE_X_MIN = STAGE_PADDING + 24f
    const val STAGE_X_MAX = STAGE_PADDING + STAGE_WIDTH - 24f
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

    // ── 🆕 (2026-07-21) MOVESET 3rd Strike ──
    /** Carrera sostenida tras el dash: entre caminar (180) y el propio dash (430). */
    const val RUN_VELOCITY = 320f
    /** Dash: mucho más rápido que caminar y de duración corta (lo corta su animación). */
    const val DASH_FORWARD_VELOCITY = 430f
    const val DASH_BACKWARD_VELOCITY = -390f
    /** Ventana del doble toque de dirección que dispara el dash. */
    const val DASH_DOUBLE_TAP_MS = 260L
    /** Medidor de súper: se llena con el daño hecho y recibido. */
    const val SUPER_METER_MAX = 100
    const val SUPER_METER_ON_HIT = 8       // al conectar un golpe
    const val SUPER_METER_ON_TAKE = 5      // al recibirlo (el que pierde también carga)
    const val SUPER_METER_ON_BLOCK = 2
    /** Daño de los golpes nuevos que no reusan una fuerza clásica. */
    const val SUPER_ART_DAMAGE = 45
    const val THROW_DAMAGE = 26
    /** Empuje del lanzamiento y de la barrida (derribo). */
    const val THROW_PUSH_VELOCITY = 420f
    /** Ventana ACTIVA del parry desde que arranca (ms). Fuera de ella no protege. */
    const val PARRY_WINDOW_MS = 260L
    /** Ventaja tras un parry exitoso: el atacante se queda en recuperación. */
    const val PARRY_ADVANTAGE_MS = 320L
    /** Alcance del agarre (px entre peleadores). */
    const val GRAB_RANGE = 62f
    // ── 🆕 (2026-08-29) CONTRAATAQUE + DERRIBO CON PODER ──
    /** Ventana ACTIVA del contraataque: más corta que la del parry (más difícil de acertar). */
    const val COUNTER_WINDOW_MS = 200L
    /** Daño del agarre gratis que premia un contraataque exitoso (entre THROW y SUPER_ART). */
    const val COUNTER_THROW_DAMAGE = 34
    /** Tramo del medidor de súper que cuesta el derribo con poder (de SUPER_METER_MAX). */
    const val POWER_THROW_METER_COST = 40
    /** Daño del derribo con poder (entre THROW y SUPER_ART: cuesta medidor, pero no todo). */
    const val POWER_THROW_DAMAGE = 42
    /** Empuje del derribo con poder: más fuerte que el del agarre normal (THROW_PUSH_VELOCITY). */
    const val POWER_THROW_PUSH_VELOCITY = 630f
    /** Daño del FATALITY (poder súper especial). Consume el medidor entero. */
    const val FATALITY_DAMAGE = 70
    /** Distancia a la que el atacante reaparece tras CRUZAR al otro lado en el fatality. */
    const val FATALITY_CROSS_OFFSET = 54f
    // 🆕 (2026-07-22, Fase 1) Escala de daño por COMBO (3rd Strike): -10% por golpe encadenado,
    // piso 50%. (Movidas del companion del VM a aquí para que SfDamage.resolvedDamage sea puro.)
    const val COMBO_DAMAGE_SCALE_STEP = 0.10f
    const val COMBO_DAMAGE_SCALE_MIN = 0.5f

    // ── 🆕 (2026-07-22) MAREO/STUN (dureza MODERADA, pedida por el dueño) ──
    /** Medidor de mareo: sube al RECIBIR golpes (proporcional al daño) y decae sin castigo. */
    const val DIZZY_METER_MAX = 100
    /** Tope de mareo que aporta UN golpe (un fatality no marea de un solo golpe). */
    const val DIZZY_HIT_CAP = 25
    /** Gracia sin recibir golpes antes de que el mareo empiece a decaer. */
    const val DIZZY_DECAY_GRACE_MS = 1500L
    /** Velocidad de decaimiento del mareo (puntos por segundo). */
    const val DIZZY_DECAY_PER_SEC = 35f
    /** Duración del aturdimiento (congelado con estrellitas). */
    const val STUN_DURATION_MS = 2000L
    /** Gracia sin CONECTAR golpes antes de que el medidor de súper decaiga (lento). */
    const val SUPER_DECAY_GRACE_MS = 4000L
    /** Decaimiento lento del súper (puntos por segundo). La barra LLENA no decae. */
    const val SUPER_DECAY_PER_SEC = 4f

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
    /** Poderes extra Grok disponibles desde el boton central P; 0 = controles clasicos. */
    val bonusPowerCount: Int = 0,
) {
    // ⚠️ COPYRIGHT (2026-07-17): RYU y KEN se ELIMINARON del juego (versión POW 100% propia).
    // Sus assets quedan en el source set debug pero YA NO hay ids en el enum: si un cliente viejo
    // manda "RYU"/"KEN" por red, SfFighterId.valueOf falla y cae a PRANKEDY (parse defensivo).
    // 🆕 Peleadores PROPIOS de POW. Prankedy trae frames proj-* propios (broma del tanque).
    PRANKEDY("Prankedy", "PRANKEDY", "STREETFIGHTER/DATA/prankedy.json", "STREETFIGHTER/IMAGES/Prankedy.webp"),
    SENOR_TIENDA(
        "El Señor de la Tienda", "TIENDA",
        "STREETFIGHTER/DATA/senortienda.json", "STREETFIGHTER/IMAGES/SenorTienda.webp",
    ),
    PAPARAZZI_1(
        "Paparazzi 1", "PAPZ 1",
        "STREETFIGHTER/DATA/paparazzi1.json", "STREETFIGHTER/IMAGES/Paparazzi1.webp",
    ),
    REY_GRUPERO(
        "Rey Grupero", "GRUPERO",
        "STREETFIGHTER/DATA/reygrupero.json", "STREETFIGHTER/IMAGES/ReyGrupero.webp",
    ),
    // ── 🆕 (2026-07-15) PELEADORES COMPARTIDOS: usan los MISMOS assets del mundo abierto
    //    (SPRITES/PLAYER/ y SPRITES/NPC/) — NO tienen sheet/JSON propio en el APK. La hoja se
    //    ARMA EN RUNTIME (SfSharedSheets, cache LRU) con cajas/timings de sf_template.json.
    //    Lázaro está dibujado a la IZQUIERDA → flip=true. (Rey de las Bromas y
    //    Pepe se ELIMINARON del juego en 2026-07-16; ya no existen como personajes.)
    //    Prankedy, Señor Tienda, ambos Paparazzi, Rey Grupero, policías y estudiantes ESCOM ya tienen arte dedicado.
    PAPARAZZI_5(
        "Paparazzi 5", "PAPZ 5",
        "STREETFIGHTER/DATA/paparazzi5.json", "STREETFIGHTER/IMAGES/Paparazzi5.webp",
    ),
    LAZARO(
        "Lázaro", "LAZARO", "STREETFIGHTER/DATA/sf_template.json", "RUNTIME/Lazaro.png",
        isAlpha = true, sharedSet = SfSharedSet("SPRITES/PLAYER/", "lazaro", "lazaro_", flip = true),
    ),
    ESCOMBOY(
        "Estudiante", "ESCOMBOY",
        "STREETFIGHTER/DATA/escomboy.json", "STREETFIGHTER/IMAGES/EscomBoy.webp",
    ),
    ESCOMGIRL(
        "Estudianta", "ESCOMGIRL",
        "STREETFIGHTER/DATA/escomgirl.json", "STREETFIGHTER/IMAGES/EscomGirl.webp",
    ),
    ROBOT(
        "Robot Estudiantx", "ROBOT",
        "STREETFIGHTER/DATA/robot.json", "STREETFIGHTER/IMAGES/Robot.webp",
    ),
    YOALLI_EHECATL(
        "Yoalli Ehécatl", "YOALLI",
        "STREETFIGHTER/DATA/yoalliehecatl.json", "STREETFIGHTER/IMAGES/YoalliEhecatl.webp",
        bonusPowerCount = 10,
    ),
    CHARRO_NEGRO(
        "El Charro Negro", "CHARRO",
        "STREETFIGHTER/DATA/charronegro.json", "STREETFIGHTER/IMAGES/CharroNegro.webp",
    ),
    LA_LLORONA(
        "La Llorona", "LLORONA",
        "STREETFIGHTER/DATA/lallorona.json", "STREETFIGHTER/IMAGES/LaLlorona.webp",
    ),
    LA_TZITZIMIME(
        "La Tzitzimime", "TZITZIMIME",
        "STREETFIGHTER/DATA/latzitzimime.json", "STREETFIGHTER/IMAGES/LaTzitzimime.webp",
        bonusPowerCount = 5,
    ),
    LA_PRESIDENTA(
        "La Presidenta", "PRESIDENTA",
        "STREETFIGHTER/DATA/lapresidenta.json", "STREETFIGHTER/IMAGES/LaPresidenta.webp",
        bonusPowerCount = 11,
    ),
    POLICIA_CDMX(
        "Policía CDMX", "POLICIA",
        "STREETFIGHTER/DATA/policiacdmx.json", "STREETFIGHTER/IMAGES/PoliciaCDMX.webp",
    ),
    POLICIA_CDMX_HOMBRE(
        "Policía CDMX (Hombre)", "POLICIA H",
        "STREETFIGHTER/DATA/policiacdmxhombre.json", "STREETFIGHTER/IMAGES/PoliciaCDMXHombre.webp",
    ),
    PARAMEDICO_CRUZ_ROJA(
        "Paramédico Cruz Roja", "PARAMED CR",
        "STREETFIGHTER/DATA/paramedicocruzroja.json", "STREETFIGHTER/IMAGES/ParamedicoCruzRoja.webp",
    ),
    POLICIA_GRANADERO_HOMBRE(
        "Policía Granadero CDMX (Hombre)", "GRANADERO H",
        "STREETFIGHTER/DATA/policiagranaderohombre.json", "STREETFIGHTER/IMAGES/PoliciaGranaderoHombre.webp",
    ),
    POLICIA_GRANADERO_MUJER(
        "Policía Granadero CDMX (Mujer)", "GRANADERA",
        "STREETFIGHTER/DATA/policiagranaderomujer.json", "STREETFIGHTER/IMAGES/PoliciaGranaderoMujer.webp",
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
    BONUS_POWER_1("bonusPower1"),
    BONUS_POWER_2("bonusPower2"),
    BONUS_POWER_3("bonusPower3"),
    BONUS_POWER_4("bonusPower4"),
    BONUS_POWER_5("bonusPower5"),
    BONUS_POWER_6("bonusPower6"),
    BONUS_POWER_7("bonusPower7"),
    BONUS_POWER_8("bonusPower8"),
    BONUS_POWER_9("bonusPower9"),
    BONUS_POWER_10("bonusPower10"),
    BONUS_POWER_11("bonusPower11"),
    VICTORY("victory"),
    KO("ko"),

    // ── 🆕 (2026-07-21) MOVESET estilo SF III 3rd Strike (hojas 20-29) ──
    // Los `jsKey` coinciden con las animaciones que escribe pack_sf_character.py.
    // ⚠️ Un peleador SIN esas hojas NO tiene estas animaciones: el motor comprueba
    // `hasAnim` antes de entrar, así que nunca cae en un estado sin arte.
    DASH_FORWARD("dashForward"),
    DASH_BACKWARD("dashBackward"),
    BLOCK_HIGH("blockHigh"),
    BLOCK_LOW("blockLow"),
    PARRY_HIGH("parryHigh"),
    PARRY_LOW("parryLow"),
    CROUCH_PUNCH("crouchPunch"),
    CROUCH_KICK("crouchKick"),
    CROUCH_HEAVY_PUNCH("crouchHeavyPunch"),
    SWEEP("sweep"),
    AIR_PUNCH("airPunch"),
    AIR_KICK("airKick"),
    LONG_KICK("longKick"),
    OVERHEAD("overhead"),
    GRAB("grab"),
    THROW("throw"),
    TAUNT("taunt"),
    THROWN("thrown"),
    GET_UP("getUp"),
    SUPER_ART("superArt"),
    HURT_CROUCH("hurtCrouch"),
    /**
     * 🆕 (2026-07-21) CARRERA: se entra sosteniendo ADELANTE al terminar un dash (como el
     * dash-run de 3rd Strike). Su arte ya venía en la hoja 03 pero se descartaba.
     */
    RUN("run"),
    /** 🆕 Pose SIN guardia de la hoja 18; se usa en la intro de ronda, antes de "PELEA". */
    IDLE_RELAXED("idleRelaxed"),
    /** 🆕 Gesticulando (hoja 18): variante de burla y presentación de ronda. */
    TALK("talk"),
    /**
     * 🆕 (2026-07-21) FATALITY / "poder súper especial": el ataque más devastador del
     * peleador. Tiene COMANDO PROPIO (medidor lleno + secuencia) y se puede lanzar en
     * cualquier momento, no es un remate de fin de ronda. Se REPRODUCE encadenando arte
     * que YA existe (súper + poder propio + proyectil), así que no espera hojas nuevas:
     * la animación se orquesta en el VM (`fatalityStep`), no es una animación única.
     */
    FATALITY("fatality"),

    /**
     * 🆕 (2026-07-22) MAREO clásico de SF (estrellitas): se entra al LLENARSE el medidor
     * de mareo (`SfFighter.dizzyMeter`, sube al RECIBIR golpes) y congela ~2 s. La pose es
     * `stun-3` (los 18 la tienen; la animación "stun" se SINTETIZA en SfFrameCatalog porque
     * los JSON empacados no la traen). ⚠️ Va AL FINAL del enum: el estado viaja por red
     * como `enum.name` con parse defensivo (un cliente viejo simplemente lo ignora).
     */
    STUN("stun"),

    /**
     * 🆕 (2026-08-29) CONTRAATAQUE: ventana activa (como el parry, pero más corta) que, si
     * conecta un golpe rival, lo anula ENTERO y pasa DIRECTO a [THROW] contra el atacante con
     * daño de bonus (ver [SfDamage.forAttack] y `applyCounterThrow` en StreetFighterCombate.kt).
     * Reutiliza el arte de THROW/THROWN/GET_UP para el pago: solo necesita su PROPIA pose de
     * "listo/guardia + recuperación" (6 cuadros).
     */
    COUNTER("counter"),
    /**
     * 🆕 (2026-08-29) DERRIBO CON PODER — intento: como [GRAB] pero telegrafiado (más cuadros)
     * y gateado por medidor (`POWER_THROW_METER_COST`). Si conecta, pasa a [POWER_THROW].
     */
    POWER_GRAB("powerGrab"),
    /** 🆕 (2026-08-29) DERRIBO CON PODER — remate: como [THROW] pero con más daño y empuje. */
    POWER_THROW("powerThrow"),
}

/**
 * 🆕 TODOS los estados de las hojas 20-29. El motor exige `hasAnim` antes de entrar a
 * cualquiera de ellos: un peleador sin esas hojas simplemente no los usa.
 */
val SF_NEW_MOVE_STATES: Set<SfFighterState> = setOf(
    SfFighterState.DASH_FORWARD, SfFighterState.DASH_BACKWARD,
    SfFighterState.BLOCK_HIGH, SfFighterState.BLOCK_LOW,
    SfFighterState.PARRY_HIGH, SfFighterState.PARRY_LOW,
    SfFighterState.CROUCH_PUNCH, SfFighterState.CROUCH_KICK,
    SfFighterState.CROUCH_HEAVY_PUNCH, SfFighterState.SWEEP,
    SfFighterState.AIR_PUNCH, SfFighterState.AIR_KICK,
    SfFighterState.LONG_KICK, SfFighterState.OVERHEAD,
    SfFighterState.GRAB, SfFighterState.THROW, SfFighterState.TAUNT,
    SfFighterState.THROWN, SfFighterState.GET_UP,
    SfFighterState.SUPER_ART, SfFighterState.HURT_CROUCH,
    SfFighterState.RUN, SfFighterState.IDLE_RELAXED, SfFighterState.TALK,
    SfFighterState.FATALITY,
    // 🆕 (2026-08-29) CONTRAATAQUE + DERRIBO CON PODER
    SfFighterState.COUNTER, SfFighterState.POWER_GRAB, SfFighterState.POWER_THROW,
)

/** 🆕 Estados de ATAQUE nuevos (los que pueden conectar un golpe). */
val SF_NEW_ATTACK_STATES: Set<SfFighterState> = setOf(
    SfFighterState.CROUCH_PUNCH, SfFighterState.CROUCH_KICK,
    SfFighterState.CROUCH_HEAVY_PUNCH, SfFighterState.SWEEP,
    SfFighterState.AIR_PUNCH, SfFighterState.AIR_KICK,
    SfFighterState.LONG_KICK, SfFighterState.OVERHEAD,
    SfFighterState.GRAB, SfFighterState.SUPER_ART,
    SfFighterState.FATALITY,
    // 🆕 (2026-08-29) Solo POWER_GRAB tiene hitbox propia; COUNTER es reactivo (como el parry)
    // y POWER_THROW es el remate sin hit-check propio (como THROW).
    SfFighterState.POWER_GRAB,
)

/** 🆕 Estados de BLOQUEO (absorben el golpe con daño reducido y sin pose de daño). */
val SF_BLOCK_STATES: Set<SfFighterState> =
    setOf(SfFighterState.BLOCK_HIGH, SfFighterState.BLOCK_LOW)

/** 🆕 Estados de PARRY (anulan el golpe por completo durante su ventana activa). */
val SF_PARRY_STATES: Set<SfFighterState> =
    setOf(SfFighterState.PARRY_HIGH, SfFighterState.PARRY_LOW)

/** 🆕 Estados en el SUELO tras un derribo: no se puede golpear ni ser golpeado. */
val SF_DOWNED_STATES: Set<SfFighterState> =
    setOf(SfFighterState.THROWN, SfFighterState.GET_UP)

val SF_BONUS_POWER_STATES: List<SfFighterState> = listOf(
    SfFighterState.BONUS_POWER_1, SfFighterState.BONUS_POWER_2, SfFighterState.BONUS_POWER_3,
    SfFighterState.BONUS_POWER_4, SfFighterState.BONUS_POWER_5, SfFighterState.BONUS_POWER_6,
    SfFighterState.BONUS_POWER_7, SfFighterState.BONUS_POWER_8, SfFighterState.BONUS_POWER_9,
    SfFighterState.BONUS_POWER_10, SfFighterState.BONUS_POWER_11,
)

fun sfBonusPowerState(index: Int): SfFighterState? = SF_BONUS_POWER_STATES.getOrNull(index - 1)
fun SfFighterState.bonusPowerIndex(): Int? =
    SF_BONUS_POWER_STATES.indexOf(this).takeIf { it >= 0 }?.plus(1)

/**
 * 🆕 (2026-07-22, Fase 1 del refactor) Poderes bonus LANZABLES por el jugador. Los poderes de
 * METAMORFOSIS son automáticos (no se eligen), así que se restan del total:
 *  - La Presidenta: P11 (metamorfosis automática → Yoalli), usable = count-1.
 *  - Yoalli Ehécatl: P10 (metamorfosis automática → La Presidenta), usable = count-1.
 *    Esos últimos poderes no deben aparecer en el menú ni usarse como proyectiles.
 */
fun sfUsableBonusPowerCount(id: SfFighterId): Int = when (id) {
    SfFighterId.LA_PRESIDENTA -> (id.bonusPowerCount - 1).coerceAtLeast(0)
    SfFighterId.YOALLI_EHECATL -> (id.bonusPowerCount - 1).coerceAtLeast(0)
    else -> id.bonusPowerCount
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
    // 🆕 (2026-07-21) Los movimientos nuevos también son golpeables (menos THROWN/GET_UP,
    // que son invulnerables en el suelo, como en el arcade original).
    SfFighterState.DASH_FORWARD, SfFighterState.DASH_BACKWARD,
    SfFighterState.BLOCK_HIGH, SfFighterState.BLOCK_LOW,
    SfFighterState.PARRY_HIGH, SfFighterState.PARRY_LOW,
    SfFighterState.CROUCH_PUNCH, SfFighterState.CROUCH_KICK,
    SfFighterState.CROUCH_HEAVY_PUNCH, SfFighterState.SWEEP,
    SfFighterState.LONG_KICK, SfFighterState.OVERHEAD,
    SfFighterState.GRAB, SfFighterState.THROW, SfFighterState.TAUNT,
    SfFighterState.SUPER_ART, SfFighterState.HURT_CROUCH,
    SfFighterState.RUN, SfFighterState.IDLE_RELAXED, SfFighterState.TALK,
    SfFighterState.FATALITY,
    // 🆕 (2026-07-22) El MAREADO es golpeable (así el rival lo castiga y lo saca del stun).
    SfFighterState.STUN,
    // 🆕 (2026-08-29) CONTRAATAQUE (igual que PARRY_*: golpeable fuera de su ventana activa) +
    // DERRIBO CON PODER (intento y remate, golpeables como GRAB/THROW).
    SfFighterState.COUNTER, SfFighterState.POWER_GRAB, SfFighterState.POWER_THROW,
) + SF_BONUS_POWER_STATES

/**
 * 🆕 Estados en los que el peleador **NO PUEDE EMPEZAR UNA ACCIÓN** porque está sufriendo un golpe
 * (hitstun) o mareado. Es lo que hay que preguntar antes de decidir "¿ataco ahora?".
 *
 * ⚠️ **NO ES [SF_HURT_STATES], y ese nombre engaña.** `SF_HURT_STATES` significa lo CONTRARIO de lo
 * que parece: es el conjunto de estados en los que el peleador **puede SER golpeado** (su hurtbox
 * está activa), e incluye IDLE, caminar, agacharse y los seis normales — o sea, casi todo lo que se
 * hace de pie. Preguntarle "¿está aturdido?" devuelve `true` estando quieto en guardia.
 *
 * Esa confusión ya costó un bug de bulto: la IA usaba `SF_HURT_STATES` como "estoy interrumpido",
 * así que al llenarse su medidor se quedaba PARALIZADA para siempre — ni súper, ni golpes, ni
 * movimiento — y la pelea se ganaba sola. Ver `cpuIsInterrupted`.
 */
val SF_HITSTUN_STATES: Set<SfFighterState> = setOf(
    SfFighterState.HURT_HEAD_LIGHT, SfFighterState.HURT_HEAD_MEDIUM, SfFighterState.HURT_HEAD_HEAVY,
    SfFighterState.HURT_BODY_LIGHT, SfFighterState.HURT_BODY_MEDIUM, SfFighterState.HURT_BODY_HEAVY,
    SfFighterState.HURT_CROUCH,
    // El mareo también inmoviliza: su handler ignora TODOS los inputs hasta que expira.
    SfFighterState.STUN,
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
    /**
     * Metamorfosis Presidenta ↔ Yoalli.
     * [metamorphosing] marca la animación invulnerable; [metamorphosed] evita un ciclo automático.
     */
    val metamorphosing: Boolean = false,
    val metamorphosed: Boolean = false,
    /**
     * 🆕 (2026-07-21) MEDIDOR DE SÚPER (0..[SfConstants.SUPER_METER_MAX]). Sube al conectar
     * y al recibir golpes; la SUPER ART lo consume entero. Es el recurso que hace que el
     * ataque máximo no se pueda repetir sin ganárselo, como en 3rd Strike.
     */
    val superMeter: Int = 0,
    /**
     * 🆕 (2026-07-22) MEDIDOR DE MAREO (0..[SfConstants.DIZZY_METER_MAX]): sube al RECIBIR
     * golpes y decae tras ~1.5 s sin recibir. Al llenarse → estado STUN (congelado ~2 s con
     * estrellitas) y el medidor se vacía. Cada peer lo calcula LOCALMENTE (online, el estado
     * STUN del rival llega por el `state` del snapshot; este campo no viaja).
     */
    val dizzyMeter: Int = 0,
    /** 🆕 Ya usó su levantada tras el derribo actual (evita re-encadenar GET_UP). */
    val downed: Boolean = false,
) {
    val isAirborne: Boolean
        get() = state == SfFighterState.JUMP_UP ||
            state == SfFighterState.JUMP_FORWARD ||
            state == SfFighterState.JUMP_BACKWARD ||
            state == SfFighterState.AIR_PUNCH ||
            state == SfFighterState.AIR_KICK

    /** 🆕 La súper está cargada al máximo (se puede ejecutar). */
    val superReady: Boolean get() = superMeter >= SfConstants.SUPER_METER_MAX
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
    // 🆕 (2026-07-21) Índice del BONUS POWER que lo lanzó (1..11; 0 = special normal).
    // Los poderes "de proyectil" traen su propio efecto dibujado en los cuadros 2-4 de
    // su hoja (`bonus-N-2/3/4`): la View los usa en vez de los `proj-*` compartidos, así
    // cada poder se ve distinto (mazo, libro, bolsa de dinero…). Ver SfModels/§bonus.
    val bonusPower: Int = 0,
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
    val bonusPower: Int? = null,           // poder extra Grok (boton P; indice 1..N)
    // ── 🆕 (2026-07-21) intenciones del moveset 3rd Strike ──
    val dashForward: Boolean = false,      // doble toque ADELANTE
    val dashBackward: Boolean = false,     // doble toque ATRÁS
    val parry: Boolean = false,            // botón PARRY (alto/bajo según agachado)
    val grab: Boolean = false,             // botón AGARRE
    val taunt: Boolean = false,            // botón BURLA
    val superArt: Boolean = false,         // botón SÚPER (requiere medidor lleno)
    // ── 🆕 (2026-08-29) CONTRAATAQUE + DERRIBO CON PODER ──
    val counter: Boolean = false,          // botón CONTRAATAQUE
    val powerThrow: Boolean = false,       // botón DERRIBO CON PODER (exige medidor parcial)
)
