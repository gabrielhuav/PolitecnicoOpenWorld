package ovh.gabrielhuav.pow.features.interiores.escom.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ovh.gabrielhuav.pow.domain.models.map.InteriorBuilding
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.CollisionGrid
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.InteriorViewModel

@Composable
fun EstacionamientoScreen(onExit: () -> Unit) {
    val grid = remember { EstacionamientoCollisionGrid }
    val viewModel: InteriorViewModel = androidx.hilt.navigation.compose.hiltViewModel<InteriorViewModel, InteriorViewModel.Factory>(
        creationCallback = { factory -> factory.create(grid) }
    )

    InteriorScreenBase(
        viewModel = viewModel,
        backgroundAssetPath = InteriorBuilding.ESTACIONAMIENTO.backgroundAsset,
        title = InteriorBuilding.ESTACIONAMIENTO.displayName,
        onExit = onExit
    )
}

private val EstacionamientoCollisionGrid = CollisionGrid.emptyWithBorder()