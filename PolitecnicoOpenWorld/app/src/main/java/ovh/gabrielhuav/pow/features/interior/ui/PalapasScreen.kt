package ovh.gabrielhuav.pow.features.interior.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.gabrielhuav.pow.domain.models.InteriorBuilding
import ovh.gabrielhuav.pow.features.interior.viewmodel.CollisionGrid
import ovh.gabrielhuav.pow.features.interior.viewmodel.InteriorViewModel

@Composable
fun PalapasScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val grid = remember { PalapasCollisionGrid }
    val viewModel: InteriorViewModel = viewModel(
        factory = InteriorViewModel.Factory(context, grid)
    )

    InteriorScreenBase(
        viewModel = viewModel,
        backgroundAssetPath = InteriorBuilding.PALAPAS.backgroundAsset,
        title = InteriorBuilding.PALAPAS.displayName,
        onExit = onExit
    )
}

private val PalapasCollisionGrid = CollisionGrid.emptyWithBorder()