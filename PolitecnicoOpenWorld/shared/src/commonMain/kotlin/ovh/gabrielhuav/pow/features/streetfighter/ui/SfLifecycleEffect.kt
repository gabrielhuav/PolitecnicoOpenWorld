package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.runtime.Composable

/** Notifica pausa/reanudación de la app sin filtrar APIs de ciclo de vida a commonMain. */
@Composable
expect fun SfLifecycleEffect(
    onPause: () -> Unit,
    onResume: () -> Unit,
)
