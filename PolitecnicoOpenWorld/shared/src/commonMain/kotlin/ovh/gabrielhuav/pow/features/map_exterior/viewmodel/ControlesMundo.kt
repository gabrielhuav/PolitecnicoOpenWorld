package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

/**
 * 🎮 LAS ENTRADAS DEL MUNDO ABIERTO — lo que el jugador puede pulsar.
 *
 * Viven en `commonMain` porque **los controles son los MISMOS en Android y en iOS**, y la UI que
 * los dibuja (joystick + diamante A/B/X/Y) también es compartida. Antes estaban en
 * `WorldMapMultiplayerModels.kt`, que es de red y solo-Android.
 *
 * ⚠️ **Los nombres viajan por la red** (`enum.name` en `MultiplayerPlayer.action`): se pueden
 * AÑADIR entradas al final, pero **renombrar o quitar rompe la compatibilidad** con clientes viejos.
 */
enum class Direction { UP, DOWN, LEFT, RIGHT }

/**
 * Los cuatro botones del diamante, en disposición Xbox: **Y arriba · X izquierda · B derecha ·
 * A abajo**. Qué hace cada uno depende de si vas a pie o conduciendo, y lo decide el ViewModel.
 */
enum class GameAction { A, B, X, Y }
