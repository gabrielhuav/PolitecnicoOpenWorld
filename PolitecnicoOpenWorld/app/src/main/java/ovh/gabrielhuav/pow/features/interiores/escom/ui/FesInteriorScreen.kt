package ovh.gabrielhuav.pow.features.interiores.escom.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ovh.gabrielhuav.pow.domain.models.map.InteriorBuilding
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.CollisionGrid
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.InteriorViewModel

@Composable
fun FesInteriorScreen(onExit: () -> Unit) {
    val grid = remember { FesCollisionGrid }
    val viewModel: InteriorViewModel = androidx.hilt.navigation.compose.hiltViewModel<InteriorViewModel, InteriorViewModel.Factory>(
        creationCallback = { factory -> factory.create(grid) }
    )

    InteriorScreenBase(
        viewModel = viewModel,
        backgroundAssetPath = InteriorBuilding.FES_INTERIOR.backgroundAsset,
        title = InteriorBuilding.FES_INTERIOR.displayName,
        onExit = onExit
    )
}

private val FesCollisionGrid = CollisionGrid.emptyWithBorder()