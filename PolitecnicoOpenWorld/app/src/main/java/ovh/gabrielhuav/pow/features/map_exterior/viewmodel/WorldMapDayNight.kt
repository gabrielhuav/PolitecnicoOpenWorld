package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.update

// ─────────────────────────────────────────────────────────────────────────────
// CICLO DÍA/NOCHE del mundo abierto (extensiones del WorldMapViewModel, SIN gemelo miembro).
//
// Reloj de juego DETERMINISTA derivado del epoch (1 min real = 1 hora de juego → ciclo
// completo de 24 min; igual para todos los jugadores, no se persiste nada). El game loop
// llama updateDayNightTick() cada tick; internamente se auto-limita a ~1 Hz y solo escribe
// el estado si cambió (StateFlow dedup + sin allocs por frame — regla de gama baja, ver 09).
// La OSCURIDAD se dibuja como una capa Compose renderer-agnóstica en WorldMapScreen (velo
// azul-noche con alpha = nightAlpha), así funciona igual en OSM nativo, Google y web sin
// tocar los renderers ni el fog. De noche, los EVENTOS DINÁMICOS suben la probabilidad de
// mini-brotes (WorldMapDynamicEvents.kt) — la infección "se siente" más al anochecer.
// ─────────────────────────────────────────────────────────────────────────────

// 1 hora de juego = 1 minuto real → ciclo día/noche completo de 24 minutos.
internal const val DAY_NIGHT_CYCLE_MS = 24L * 60_000L

// Oscuridad máxima del velo nocturno (no llegar a negro: se debe seguir viendo el mapa).
internal const val MAX_NIGHT_ALPHA = 0.38f

/** Alpha del velo nocturno para una hora de juego contínua (0.0..24.0). */
internal fun nightAlphaForHour(h: Double): Float = when {
    h < 5.0 -> MAX_NIGHT_ALPHA                                   // madrugada: noche cerrada
    h < 8.0 -> (MAX_NIGHT_ALPHA * ((8.0 - h) / 3.0)).toFloat()   // amanecer: 5→8 aclara
    h < 18.0 -> 0f                                               // día pleno
    h < 21.0 -> (MAX_NIGHT_ALPHA * ((h - 18.0) / 3.0)).toFloat() // atardecer: 18→21 oscurece
    else -> MAX_NIGHT_ALPHA                                      // noche
}

/** Hora de juego contínua actual (0.0..24.0), derivada del reloj real. */
internal fun currentGameHour(now: Long = System.currentTimeMillis()): Double =
    (now % DAY_NIGHT_CYCLE_MS).toDouble() / DAY_NIGHT_CYCLE_MS * 24.0

/** Tick del ciclo (lo llama el game loop MIEMBRO). Barato: ~1 write por segundo como mucho. */
internal fun WorldMapViewModel.updateDayNightTick() {
    val now = System.currentTimeMillis()
    if (now - lastDayNightUpdateMs < 1000L) return
    lastDayNightUpdateMs = now
    val hourF = currentGameHour(now)
    val hour = hourF.toInt().coerceIn(0, 23)
    val alpha = nightAlphaForHour(hourF)
    val s = _uiState.value
    if (s.gameHour != hour || kotlin.math.abs(s.nightAlpha - alpha) > 0.004f) {
        _uiState.update { it.copy(gameHour = hour, nightAlpha = alpha) }
    }
}
