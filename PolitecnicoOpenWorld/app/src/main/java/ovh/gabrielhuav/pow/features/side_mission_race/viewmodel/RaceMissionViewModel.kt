package ovh.gabrielhuav.pow.features.side_mission_race.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RaceMissionViewModel : ViewModel() {

    private val _state = MutableStateFlow<RaceMissionState>(RaceMissionState.Idle)
    val state: StateFlow<RaceMissionState> = _state.asStateFlow()

    fun startCountdown() {
        if (_state.value != RaceMissionState.Idle) return
        viewModelScope.launch {
            for (i in 3 downTo 1) {
                _state.value = RaceMissionState.Countdown(i)
                delay(1000L)
            }
            _state.value = RaceMissionState.Ready
        }
    }

    fun reset() {
        _state.value = RaceMissionState.Idle
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RaceMissionViewModel() as T
    }
}
