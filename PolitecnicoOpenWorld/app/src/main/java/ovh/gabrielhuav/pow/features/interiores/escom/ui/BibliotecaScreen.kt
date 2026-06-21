package ovh.gabrielhuav.pow.features.interiores.escom.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.gabrielhuav.pow.domain.models.map.InteriorBuilding
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.CollisionGrid
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.InteriorViewModel

@Composable
fun BibliotecaScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val grid = remember { BibliotecaCollisionGrid }
    val viewModel: InteriorViewModel = viewModel(
        factory = InteriorViewModel.Factory(context, grid)
    )

    InteriorScreenBase(
        viewModel = viewModel,
        backgroundAssetPath = InteriorBuilding.BIBLIOTECA.backgroundAsset,
        title = InteriorBuilding.BIBLIOTECA.displayName,
        onExit = onExit
    )
}

private val BibliotecaCollisionGrid = CollisionGrid.emptyWithBorder()