package ovh.gabrielhuav.pow.features.interiores.escom.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.gabrielhuav.pow.domain.models.InteriorBuilding
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.CollisionGrid
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.InteriorViewModel

@Composable
fun AuditorioScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val grid = remember { AuditorioCollisionGrid }
    val viewModel: InteriorViewModel = viewModel(
        factory = InteriorViewModel.Factory(context, grid)
    )

    InteriorScreenBase(
        viewModel = viewModel,
        backgroundAssetPath = InteriorBuilding.AUDITORIO.backgroundAsset,
        title = InteriorBuilding.AUDITORIO.displayName,
        onExit = onExit
    )
}

/**
 * Matriz 20×30 del auditorio.
 * Punto de partida: borde de paredes, interior caminable.
 * Reemplaza por el layout real cuando lo definas.
 */
private val AuditorioCollisionGrid = CollisionGrid.emptyWithBorder()