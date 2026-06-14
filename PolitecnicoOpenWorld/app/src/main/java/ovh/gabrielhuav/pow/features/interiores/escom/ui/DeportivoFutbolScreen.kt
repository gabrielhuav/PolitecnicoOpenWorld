package ovh.gabrielhuav.pow.features.interiores.escom.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.CollisionGrid
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.InteriorViewModel

@Composable
fun DeportivoFutbolScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val grid = remember { DeportivoFutbolCollisionGrid }
    val viewModel: InteriorViewModel = viewModel(
        factory = InteriorViewModel.Factory(context, grid)
    )

    InteriorScreenBase(
        viewModel = viewModel,
        backgroundAssetPath = "PLACES/deportivo_miguel_aleman/Campo_futbol.webp",
        title = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.escom_football_field),
        onExit = onExit
    )
}

private val DeportivoFutbolCollisionGrid = CollisionGrid.emptyWithBorder()
