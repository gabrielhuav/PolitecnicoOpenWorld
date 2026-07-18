package ovh.gabrielhuav.pow.features.interiores.escom.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ovh.gabrielhuav.pow.domain.models.map.InteriorBuilding
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.CollisionGrid
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.InteriorViewModel

@Composable
fun EdificioScreen(onExit: () -> Unit) {
    val grid = remember { EdificioCollisionGrid }
    val viewModel: InteriorViewModel = androidx.hilt.navigation.compose.hiltViewModel<InteriorViewModel, InteriorViewModel.Factory>(
        creationCallback = { factory -> factory.create(grid) }
    )

    InteriorScreenBase(
        viewModel = viewModel,
        backgroundAssetPath = InteriorBuilding.EDIFICIO.backgroundAsset,
        title = InteriorBuilding.EDIFICIO.displayName,
        onExit = onExit
    )
}

private val EdificioCollisionGrid = CollisionGrid.emptyWithBorder()