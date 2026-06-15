package ovh.gabrielhuav.pow.features.interior.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.gabrielhuav.pow.domain.models.InteriorBuilding
import ovh.gabrielhuav.pow.features.interior.viewmodel.CollisionGrid
import ovh.gabrielhuav.pow.features.interior.viewmodel.InteriorViewModel

@Composable
fun Voca9Screen(onExit: () -> Unit) {
    val context = LocalContext.current
    val grid = remember { Voca9CollisionGrid }
    val viewModel: InteriorViewModel = viewModel(
        factory = InteriorViewModel.Factory(context, grid)
    )

    InteriorScreenBase(
        viewModel = viewModel,
        backgroundAssetPath = InteriorBuilding.VOCA9.backgroundAsset,
        title = InteriorBuilding.VOCA9.displayName,
        onExit = onExit
    )
}

private val Voca9CollisionGrid = CollisionGrid.emptyWithBorder()
