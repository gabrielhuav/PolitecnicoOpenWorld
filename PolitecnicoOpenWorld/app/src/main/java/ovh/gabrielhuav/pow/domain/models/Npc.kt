package ovh.gabrielhuav.pow.domain.models

import org.osmdroid.util.GeoPoint
import java.util.UUID

enum class CarModel(val dirName: String, val prefix: String) {
    SEDAN("WHITE_SEDAN", "White_SEDAN_CLEAN_All_"),
    SPORT("WHITE_SPORT", "White_SPORT_CLEAN_All_"),
    SUPERCAR("WHITE_SUPERCAR", "White_SUPERCAR_CLEAN_All_"),
    SUV("WHITE_SUV", "White_SUV_CLEAN_All_"),
    VAN("WHITE_VAN", "White_VAN_CLEAN_All_"),
    WAGON("WHITE_WAGON", "White_WAGON_CLEAN_All_")
}

enum class NpcNavState {
    MACRO_OSM,      // Usa calles reales
    MICRO_LANDMARK, // Usa el dibujo del asset
    PARKED          // Detenido en un cajón de estacionamiento
}

// PERSONALIDAD del NPC (asignada al spawn, peso aleatorio). Un único campo que
// decide CÓMO reacciona ante el jugador (robo de coche, golpes). Casi gratis en CPU.
//  - PASSIVE: no reacciona (la mayoría).
//  - COWARD: huye (entra en estado de miedo) al ser provocado.
//  - AGGRESSIVE: contraataca (entra en estado de embestida hacia el jugador).
enum class NpcTrait { PASSIVE, COWARD, AGGRESSIVE }
data class Npc(
    val id: String = UUID.randomUUID().toString(),
    val type: NpcType,
    var location: GeoPoint,
    var rotationAngle: Float = 0f,
    val speed: Double,
    val currentWay: MapWay? = null,
    var targetNodeIndex: Int = 0,
    var moveDirection: Int = 1,
    val carColor: Int = 0xFFFFFFFF.toInt(),
    val carModel: CarModel = CarModel.SEDAN,
    val isRemote: Boolean = false, // Indica si el servidor nos mandó este NPC
    val ownerId: String? = null,    // El ID del jugador que controla la IA de este NPC


    var isMoving: Boolean = false,
    var facingRight: Boolean = true,
    var visualConfig: CharacterVisualConfig? = null, // Nullable para que no exploten los autos
    val displayName: String? = null,
    val isFirstTimeBoarded: Boolean = true,
    val health: Float = 100f,
    val isDying: Boolean = false,

    // Navegación por landmarks / vías locales (rama de navegación):
    val navState: NpcNavState = NpcNavState.MACRO_OSM,
    val currentLocalWay: ovh.gabrielhuav.pow.domain.models.ai.LocalWay? = null,
    val currentLandmark: Landmark? = null,

    // ─── Estado transitorio de IA (vive SOLO en el host que simula; NO se serializa
    //     en MultiplayerNpc). El comportamiento se manifiesta como movimiento/rotación,
    //     así que los demás clientes lo ven sin cambiar el formato del cable.) ───
    // MIEDO AL COMBATE: mientras now < fearUntil el NPC huye (acelera y se aleja
    // del punto fearFrom). Se activa al ver un PLAYER_DAMAGE o un ataque cercano.
    val fearUntil: Long = 0L,
    val fearFromLat: Double = 0.0,
    val fearFromLon: Double = 0.0,
    // CHARLAS: mientras now < chatUntil el peatón se detiene mirando a su pareja.
    val chatUntil: Long = 0L,
    val chatPartnerId: String? = null,

    // PERSONALIDAD + EMBESTIDA (transitorio, solo en el host; NO se serializa):
    //  - trait: personalidad fija asignada al spawn.
    //  - aggroUntil: mientras now < aggroUntil el NPC (AGGRESSIVE) persigue al
    //    jugador en línea recta y le hace daño por contacto.
    val trait: NpcTrait = NpcTrait.PASSIVE,
    val aggroUntil: Long = 0L,

    // ─── POLICÍA (transitorio, lo simula el dueño del nivel de búsqueda) ──────
    //  - policeDisembarked: la patrulla ya llegó y soltó a los policías (se queda
    //    detenida). Los POLICE_COP nacen con este flag en true.
    //  - policeCanShoot: a 2+ estrellas los policías pueden dispararte.
    val policeDisembarked: Boolean = false,
    val policeCanShoot: Boolean = false,
    // Id de la patrulla a la que pertenece este policía (para volver a subirse a ella).
    val policeCarId: String? = null,
    // El policía va corriendo de regreso a su patrulla (porque te subiste a un coche).
    val policeReturning: Boolean = false,

    // Mientras now < callingUntil, el NPC muestra un 📞 (está "llamando a la policía"),
    // p. ej. el conductor al que le robaste el coche.
    val callingUntil: Long = 0L
)