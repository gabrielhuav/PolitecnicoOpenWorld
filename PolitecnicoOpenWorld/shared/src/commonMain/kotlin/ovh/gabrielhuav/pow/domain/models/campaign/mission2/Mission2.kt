package ovh.gabrielhuav.pow.domain.models.campaign.mission2

import org.jetbrains.compose.resources.StringResource
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.obj_m2_esconderse_desc
import ovh.gabrielhuav.pow.shared.recursos.obj_m2_esconderse_title
import ovh.gabrielhuav.pow.shared.recursos.obj_m2_hablar_prankedy_desc
import ovh.gabrielhuav.pow.shared.recursos.obj_m2_hablar_prankedy_title
import ovh.gabrielhuav.pow.shared.recursos.obj_m2_pista_brote_desc
import ovh.gabrielhuav.pow.shared.recursos.obj_m2_pista_brote_title
import ovh.gabrielhuav.pow.shared.recursos.obj_m2_pista_rumor_desc
import ovh.gabrielhuav.pow.shared.recursos.obj_m2_pista_rumor_title
import ovh.gabrielhuav.pow.shared.recursos.obj_m2_recuperar_mochila_desc
import ovh.gabrielhuav.pow.shared.recursos.obj_m2_recuperar_mochila_title
import ovh.gabrielhuav.pow.domain.models.campaign.CampaignObjective

/**
 * MISIÓN 2 de la campaña: "El rumor". Arranca al terminar la Misión 1 (el jugador ya ENTRÓ a la
 * ESCOM huyendo de la policía) y se juega sobre el CAMPUS de la ESCOM (mapa global, zona libre)
 * salvo la última fase (interior: el salón de la mochila). Guion (ver CAMPAIGN/02_MISSION_2.md):
 *
 *  1. ESCONDERSE: la policía ENTRÓ al lobby de la ESCOM a buscarlos. Se juega DENTRO del lobby
 *     (motor de interiores): policías patrullan la sala y debes evitar que te vean de cerca
 *     hasta que se rindan y se vayan. Si te quedas cerca demasiado tiempo → MISIÓN FALLIDA.
 *     (Rediseño 2026-07-08: antes eran policías en la ENTRADA exterior del campus.)
 *  2. RUMOR: buscando pistas encuentras a 2 estudiantes platicando el RUMOR ZOMBIE (en la ENCB
 *     y en todo Zacatenco pasan "cosas raras"). Hay que quedarse a ESCUCHARLO COMPLETO.
 *  3. BROTE: presencias el primer brote público: un NPC se CONVIERTE, ataca gente, la policía lo
 *     SOMETE y por la radio piden refuerzos en la ENCB ("todo el personal"). Se lo llevan.
 *  4. PLÁTICA: Prankedy escuchó todo. Te acercas a hablar: le dices que fue su broma; te cuenta
 *     que competía con el REY GRUPERO por ser el auténtico "Rey de las Bromas" (el reto era hacer
 *     una broma ESE MISMO DÍA) y que dejó su MOCHILA escondida en un salón al huir de la policía.
 *  5. MOCHILA: el salón está EN CLASES; lanzas la LATA APESTOSA para vaciarlo y recuperas la
 *     mochila de Prankedy. → Misión 2 completada.
 *
 * La lógica viva (máquina de fases) está en WorldMapMission2.kt (exterior) y en el motor de
 * interiores (salón). La fase se persiste en el JSON de guardado (GameSaveData.mission2Phase).
 * Los DIÁLOGOS son texto de historia y van hardcodeados en español (misma convención que los
 * cómics y las frases de Prankedy). Los TÍTULOS de objetivos sí van por (widget).
 */
object Mission2 {

    // Prefijo común de los ids de objetivo de esta misión (lo usan los checks genéricos:
    // misión fallida al morir, reintento de misión, etc.).
    const val OBJECTIVE_ID_PREFIX = "m2_"

    // ─── FASES de la máquina de estados (se persiste en GameSaveData.mission2Phase) ───
    const val PHASE_NONE = 0      // aún no arranca (Misión 1 en curso)
    const val PHASE_HIDE = 1      // esconderse de la policía
    const val PHASE_RUMOR = 2     // escuchar el rumor zombie completo
    const val PHASE_BROTE = 3     // presenciar el primer brote público
    const val PHASE_TALK = 4      // platicar con Prankedy (Rey Grupero + mochila)
    const val PHASE_BACKPACK = 5  // recuperar la mochila (salón, interior)
    const val PHASE_DONE = 6      // misión completada

    // ─── COORDS FIJAS (X=lon, Y=lat), dentro del campus ESCOM (bbox ~±111 m de
    //     19.50456,-99.14674; ver EscomBoundingBox). Para reubicar algo, cambia LA CONSTANTE. ───
    // Entrada del campus: de aquí LLEGAN los policías del brote (fase 3) y hacia acá se llevan
    // al detenido. (La fase 1 ya NO spawnea policías aquí: se juega dentro del lobby.)
    const val POLICE_SEARCH_LAT = 19.50480
    const val POLICE_SEARCH_LON = -99.14660
    // Punto del RUMOR (fase 2): 2 estudiantes platicando en la explanada sur.
    const val RUMOR_LAT = 19.50412
    const val RUMOR_LON = -99.14700
    // Punto del BROTE (fase 3): primer zombie público, lado sureste del campus.
    const val BROTE_LAT = 19.50396
    const val BROTE_LON = -99.14614
    // Punto donde REAPARECE Prankedy para la plática (fase 4): centro del campus.
    const val PRANKEDY_LAT = 19.50422
    const val PRANKEDY_LON = -99.14652
    // Checkpoint del REINTENTO de la Misión 2 = spawn canónico de ESCOM (entrada del campus).
    const val RETRY_SPAWN_LAT = 19.504603
    const val RETRY_SPAWN_LON = -99.145985

    // ─── UMBRALES / TIEMPOS (grados ≈ 111 km por grado; ms) ───
    // Fase 1 · ESCONDERSE (INTERIOR, píxeles de mundo del lobby — ver ZombieAmbientNpcs/Tick):
    // si un policía te tiene a < DETECT_PX por > DETECT_MS, te reconoce (misión fallida).
    // Aguanta HIDE_DURATION_MS y se rinden: corren a la puerta y se van (cumplido).
    const val HIDE_COP_COUNT = 4
    const val HIDE_DETECT_PX = 120f          // radio de "te está viendo" (px de la sala)
    const val HIDE_DETECT_MS = 2500L
    const val HIDE_DURATION_MS = 35_000L     // cuánto dura la búsqueda antes de que se rindan
    const val HIDE_COP_SPEED_PX = 3.4f       // patrullan caminando (px/tick, ~AMBIENT_SPEED)
    const val HIDE_SWEEP_EVERY_MS = 7_000L   // cada tanto, UN policía barre hacia tu posición
    // Tope de seguridad de la RETIRADA: si un policía se atora contra una colisión del lobby
    // (p. ej. los autos del estacionamiento) camino a la puerta, la fase quedaba SIN completar
    // para siempre ("los pierdo y no pasa nada", QA 2026-07-13). Pasado este extra tras la
    // rendición, los rezagados desaparecen y la fase se da por CUMPLIDA (mismo espíritu que el
    // tope de 12 s de la escena del brote).
    const val HIDE_EVAC_TIMEOUT_MS = 10_000L
    // Fase 2 · RUMOR: hay que estar a < LISTEN para que la conversación AVANCE (si te alejas,
    // se PAUSA y se retoma donde iba). Una línea cada CONVO_LINE_MS.
    const val LISTEN_DEG = 0.00016           // ~18 m
    const val CONVO_LINE_MS = 3400L
    // Fase 3 · BROTE: el evento arranca cuando te acercas a < TRIGGER del punto.
    const val BROTE_TRIGGER_DEG = 0.00040    // ~45 m
    const val BROTE_ZOMBIE_SPEED = 0.0000042
    const val BROTE_CIV_FLEE_SPEED = 0.0000050
    const val BROTE_COP_SPEED = 0.0000110    // llegan corriendo
    const val BROTE_ATTACK_MS = 6000L        // el zombie golpea gente este tiempo antes de que llegue la policía
    const val BROTE_SUBDUE_DEG = 0.00005     // ~5.5 m: la policía "alcanza" al zombie y lo somete
    // Fase 4 · PLÁTICA: la conversación con Prankedy arranca a < TALK_DEG de él.
    const val TALK_DEG = 0.00012             // ~13 m
    // Pausa entre "objetivo cumplido" y el objetivo de la siguiente fase.
    const val PHASE_TRANSITION_MS = 2500L

    // ─── OBJETIVOS (todos con arriveRadiusMeters = 0: se cumplen por NARRATIVA en el tick de
    //     WorldMapMission2.kt, no por llegada — igual que INGRESAR_ESCOM; ver checkObjectiveProgress). ───
    val ESCONDERSE_POLICIA = CampaignObjective(
        id = "m2_esconderse_policia",
        titleRes = Res.string.obj_m2_esconderse_title,
        descriptionRes = Res.string.obj_m2_esconderse_desc,
        // La fase se juega DENTRO del lobby: el 🎯 apunta a la puerta de la ESCOM para guiarte
        // a ENTRAR si sigues la misión desde el mapa global (dentro no hay waypoint exterior).
        targetLat = 19.50490,
        targetLon = -99.14674,
        arriveRadiusMeters = 0.0
    )
    val PISTA_RUMOR = CampaignObjective(
        id = "m2_pista_rumor",
        titleRes = Res.string.obj_m2_pista_rumor_title,
        descriptionRes = Res.string.obj_m2_pista_rumor_desc,
        targetLat = RUMOR_LAT,
        targetLon = RUMOR_LON,
        arriveRadiusMeters = 0.0
    )
    val PISTA_BROTE = CampaignObjective(
        id = "m2_pista_brote",
        titleRes = Res.string.obj_m2_pista_brote_title,
        descriptionRes = Res.string.obj_m2_pista_brote_desc,
        targetLat = BROTE_LAT,
        targetLon = BROTE_LON,
        arriveRadiusMeters = 0.0
    )
    val HABLAR_PRANKEDY = CampaignObjective(
        id = "m2_hablar_prankedy",
        titleRes = Res.string.obj_m2_hablar_prankedy_title,
        descriptionRes = Res.string.obj_m2_hablar_prankedy_desc,
        targetLat = PRANKEDY_LAT,
        targetLon = PRANKEDY_LON,
        arriveRadiusMeters = 0.0
    )
    val RECUPERAR_MOCHILA = CampaignObjective(
        id = "m2_recuperar_mochila",
        titleRes = Res.string.obj_m2_recuperar_mochila_title,
        descriptionRes = Res.string.obj_m2_recuperar_mochila_desc,
        // La mochila está en un SALÓN de la ESCOM: el waypoint apunta a la puerta del edificio.
        targetLat = 19.50490,
        targetLon = -99.14674,
        arriveRadiusMeters = 0.0
    )

    // Objetivos de la Misión 2 en orden.
    val objectives: List<CampaignObjective> = listOf(
        ESCONDERSE_POLICIA, PISTA_RUMOR, PISTA_BROTE, HABLAR_PRANKEDY, RECUPERAR_MOCHILA
    )

    // ─── DIÁLOGOS (texto de historia, en español — misma convención que los cómics) ───
    // Cada línea = (quién habla, qué dice). Se muestran como SUBTÍTULOS (storyConvo* del estado).

    // Fase 2 · el RUMOR ZOMBIE (2 estudiantes; hay que escucharlo COMPLETO).
    val RUMOR_LINES: List<Pair<String, String>> = listOf(
        "Estudiante A" to "¿Ya supiste? Dicen que en la ENCB están pasando cosas MUY raras.",
        "Estudiante B" to "¿Raras cómo? ¿Otra fuga en un laboratorio?",
        "Estudiante A" to "No… gente que se pone agresiva de la nada. Como si estuvieran… enfermos.",
        "Estudiante B" to "Mi primo estudia allá. Dice que ayer un compañero MORDIÓ a alguien en el laboratorio.",
        "Estudiante A" to "¿¡Lo mordió!? Eso suena a película de zombies, jajaja.",
        "Estudiante B" to "Ríete, pero en todo Zacatenco se rumora lo mismo. Yo que tú no me acercaba a la ENCB.",
        "Estudiante A" to "Bueno… igual y solo es estrés de parciales. ¿O no?"
    )

    // Fase 3 · grito al iniciar el brote + RADIO de la policía al someter al zombie.
    const val BROTE_SCREAM_SPEAKER = "Estudiante"
    const val BROTE_SCREAM_TEXT = "¡¿Qué le pasa?! ¡AUXILIO, está atacando a la gente!"
    val RADIO_LINES: List<Pair<String, String>> = listOf(
        "📻 Radio policial" to "Central a todas las unidades: código rojo en la ENCB.",
        "📻 Radio policial" to "Las personas se están poniendo MUY violentas. Solicitamos refuerzos.",
        "📻 Radio policial" to "Se requiere a TODO el personal disponible en la ENCB. Repito: TODO el personal.",
        "📻 Radio policial" to "10-4. Nos llevamos a un detenido para investigación… esto no es normal."
    )

    // Fase 4 · plática con Prankedy: la broma, el REY GRUPERO y la mochila del salón.
    val PRANKEDY_LINES: List<Pair<String, String>> = listOf(
        "Tú" to "Prankedy… ¿escuchaste eso? Esto lo causó TU broma en la ENCB.",
        "Prankedy" to "Relax, relax… okey, sí. Puede que haya sido mi culpa.",
        "Tú" to "¿Por qué hiciste esa broma?",
        "Prankedy" to "Estaba compitiendo contra el REY GRUPERO para ver quién es el auténtico \"Rey de las Bromas\".",
        "Prankedy" to "El reto era hacer una broma ESE MISMO DÍA… y pues improvisé en la ENCB.",
        "Prankedy" to "¡Vámonos a la v... wey! …perdón, se me sale lo Rey Grupero.",
        "Prankedy" to "Escucha: cuando me escondía de la policía dejé mi MOCHILA en un salón de la ESCOM.",
        "Prankedy" to "Ahí está la evidencia de la broma. Recupérala ANTES de que la encuentre la policía.",
        "Tú" to "El salón está en clases…",
        "Prankedy" to "Toma esta LATA APESTOSA. Lánzala y saldrán TODOS. Confía."
    )
}
