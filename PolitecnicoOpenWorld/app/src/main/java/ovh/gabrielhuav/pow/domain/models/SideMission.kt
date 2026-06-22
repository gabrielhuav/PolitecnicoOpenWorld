package ovh.gabrielhuav.pow.domain.models

enum class MissionStatus { IDLE, ACTIVE, COMPLETED, FAILED }

data class SideMission(
    val id: String,
    val title: String,
    val description: String,
    val status: MissionStatus = MissionStatus.IDLE,
    val rewardCollectibleId: String? = null
)
