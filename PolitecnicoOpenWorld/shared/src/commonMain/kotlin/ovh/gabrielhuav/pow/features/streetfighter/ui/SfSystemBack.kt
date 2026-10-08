package ovh.gabrielhuav.pow.features.streetfighter.ui

import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SfOnlineStatus

/**
 * Funcionamiento para cuando el jugador pulsa el botón/gesto ATRÁS del sistema en HUELUM VS. GOYA
 *
 * Lógica pura (sin Compose) para poder probarla en commonTest, se decide la acción
 */
enum class SfSystemBack {
    /** Fuera del combate: se deja el comportamiento del sistema */
    SISTEMA,

    /** En combate sin diálogo: abrir el diálogo de salida */
    ABRIR_DIALOGO,

    /** Con el diálogo abierto: cerrarlo y seguir peleando */
    CERRAR_DIALOGO,
}

fun sfSystemBackAction(inCharacterSelect: Boolean, showExitDialog: Boolean): SfSystemBack = when {
    inCharacterSelect -> SfSystemBack.SISTEMA
    showExitDialog -> SfSystemBack.CERRAR_DIALOGO
    else -> SfSystemBack.ABRIR_DIALOGO
}

/** "Cambiar personaje" solo se ofrece offline: en línea abandonaría la sala del rival */
fun sfCanChangeCharacterFromExit(onlineStatus: SfOnlineStatus): Boolean =
    onlineStatus == SfOnlineStatus.OFF