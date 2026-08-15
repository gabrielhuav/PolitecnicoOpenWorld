package ovh.gabrielhuav.pow.domain.models.campaign.mission3

import org.jetbrains.compose.resources.StringResource
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.obj_m3_evidencia_desc
import ovh.gabrielhuav.pow.shared.recursos.obj_m3_evidencia_title
import ovh.gabrielhuav.pow.shared.recursos.obj_m3_infiltrarse_desc
import ovh.gabrielhuav.pow.shared.recursos.obj_m3_infiltrarse_title
import ovh.gabrielhuav.pow.shared.recursos.obj_m3_ir_encb_desc
import ovh.gabrielhuav.pow.shared.recursos.obj_m3_ir_encb_title
import ovh.gabrielhuav.pow.domain.models.campaign.CampaignObjective

/**
 * MISIÓN 3 de la campaña: "Regreso a la ENCB" (contención e infiltración). La ENCB está
 * ACORDONADA tras el llamado de refuerzos de la Misión 2: granaderos en el perímetro y
 * paparazzi husmeando afuera. Guion (ver CAMPAIGN/03_MISSION_3.md):
 *
 *  1. VIAJE: vuelve a la ENCB (la zona ya es de cuarentena).
 *  2. INFILTRACIÓN: evade el CORDÓN de granaderos (sigilo, evolución de la fase "esconderse"
 *     de la Misión 2: si te quedas cerca de un granadero demasiado tiempo, te detectan →
 *     MISIÓN FALLIDA). Los paparazzi merodean afuera buscando la nota.
 *  3. ASALTO (interior): la cadena de salas de la ENCB (encb_lobby→salon1→lab1→lab2) ahora
 *     tiene ZOMBIS (primer combate interior de campaña). Recupera la EVIDENCIA del laboratorio
 *     (encb_lab1) y sal de ahí.
 *
 * RECOMPENSA: la PRIMERA ARMA DE FUEGO (desbloquea el modo RANGED en interiores de campaña).
 * La lógica viva está en WorldMapMission3.kt (exterior) y en el motor de interiores (asalto).
 * La fase se persiste en GameSaveData.mission3Phase. Diálogos/promts de historia hardcodeados
 * en español (convención); títulos de objetivos por @StringRes.
 */
object Mission3 {

    // Prefijo común de los ids de objetivo (misión fallida al morir, retry, gates genéricos).
    const val OBJECTIVE_ID_PREFIX = "m3_"

    // ─── FASES (se persiste en GameSaveData.mission3Phase) ───
    const val PHASE_NONE = 0      // aún no arranca / no seleccionada
    const val PHASE_TRAVEL = 1    // ir a la ENCB
    const val PHASE_INFILTRATE = 2 // evadir el cordón de granaderos
    const val PHASE_ASSAULT = 3   // interior ENCB con zombis: recuperar la evidencia
    const val PHASE_DONE = 4      // misión completada (arma de fuego desbloqueada)

    // ─── COORDS FIJAS (X=lon, Y=lat) — vecindario de la ENCB (ver WorldMapPrankedy: ENCB_LAT/LON) ───
    // Centro de la ENCB (punto de entrada al interior durante la infiltración).
    const val ENCB_LAT = 19.5001588
    const val ENCB_LON = -99.1450298
    // Punto de APROXIMACIÓN (fase 1 → 2): al acercarte a la ENCB arranca la infiltración.
    const val APPROACH_DEG = 0.0009        // ~100 m del centro → se arma el cordón
    // Cordón de granaderos: anillo alrededor de la ENCB.
    const val CORDON_COUNT = 6
    const val CORDON_RING_DEG = 0.00055    // ~60 m del centro
    const val CORDON_PATROL_DEG = 0.00018  // patrullan un arco corto alrededor de su puesto
    const val CORDON_SPEED = 0.0000030
    // Paparazzi merodeando afuera del cordón (escenografía + burbujas 💬).
    const val PAPARAZZI_COUNT = 3
    const val PAPARAZZI_RING_DEG = 0.00075
    // Detección (sigilo): a < DETECT de un granadero por > DETECT_MS → te descubren.
    const val DETECT_DEG = 0.00019         // ~21 m
    const val DETECT_MS = 2500L
    // Entrada: al llegar a < ENTER_DEG del centro de la ENCB, entras al interior (asalto).
    const val ENTER_DEG = 0.00014          // ~15 m
    // Checkpoint del REINTENTO: la entrada al mapa global tras el outro (junto a la ENCB).
    const val RETRY_SPAWN_LAT = 19.50102
    const val RETRY_SPAWN_LON = -99.14421
    // Zombis por sala en el ASALTO interior (cadena ENCB).
    const val ASSAULT_ZOMBIES_PER_ROOM = 4

    // ─── OBJETIVOS (radio 0 = narrativos; los cumple el tick de WorldMapMission3.kt) ───
    val IR_ENCB = CampaignObjective(
        id = "m3_ir_encb",
        titleRes = Res.string.obj_m3_ir_encb_title,
        descriptionRes = Res.string.obj_m3_ir_encb_desc,
        targetLat = ENCB_LAT,
        targetLon = ENCB_LON,
        arriveRadiusMeters = 0.0
    )
    val INFILTRARSE = CampaignObjective(
        id = "m3_infiltrarse",
        titleRes = Res.string.obj_m3_infiltrarse_title,
        descriptionRes = Res.string.obj_m3_infiltrarse_desc,
        targetLat = ENCB_LAT,
        targetLon = ENCB_LON,
        arriveRadiusMeters = 0.0
    )
    val RECUPERAR_EVIDENCIA = CampaignObjective(
        id = "m3_recuperar_evidencia",
        titleRes = Res.string.obj_m3_evidencia_title,
        descriptionRes = Res.string.obj_m3_evidencia_desc,
        targetLat = ENCB_LAT,
        targetLon = ENCB_LON,
        arriveRadiusMeters = 0.0
    )

    val objectives: List<CampaignObjective> = listOf(IR_ENCB, INFILTRARSE, RECUPERAR_EVIDENCIA)
}
