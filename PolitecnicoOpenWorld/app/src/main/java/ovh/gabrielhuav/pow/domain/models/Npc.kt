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
    val isDying: Boolean = false
)