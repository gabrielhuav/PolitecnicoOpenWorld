package ovh.gabrielhuav.pow.features.side_mission_race.viewmodel

sealed class RaceMissionState {
    object Idle : RaceMissionState()
    data class Countdown(val secondsLeft: Int) : RaceMissionState()
    object Ready : RaceMissionState()
}
