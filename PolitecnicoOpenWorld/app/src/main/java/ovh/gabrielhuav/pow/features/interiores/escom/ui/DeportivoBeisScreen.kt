package ovh.gabrielhuav.pow.features.interiores.escom.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.CollisionGrid
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.InteriorViewModel

@Composable
fun DeportivoBeisScreen(onExit: () -> Unit) {
    val grid = remember { DeportivoBeisCollisionGrid }
    val viewModel: InteriorViewModel = androidx.hilt.navigation.compose.hiltViewModel<InteriorViewModel, InteriorViewModel.Factory>(
        creationCallback = { factory -> factory.create(grid) }
    )

    InteriorScreenBase(
        viewModel = viewModel,
        backgroundAssetPath = "PLACES/deportivo_miguel_aleman/campo_beis.webp",
        title = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.escom_baseball_field),
        onExit = onExit
    )
}

private val DeportivoBeisCollisionGrid = CollisionGrid.emptyWithBorder()
