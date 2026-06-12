package ovh.gabrielhuav.pow.features.interior.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.gabrielhuav.pow.features.interior.viewmodel.CollisionGrid
import ovh.gabrielhuav.pow.features.interior.viewmodel.InteriorViewModel

@Composable
fun DeportivoBeisScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val grid = remember { DeportivoBeisCollisionGrid }
    val viewModel: InteriorViewModel = viewModel(
        factory = InteriorViewModel.Factory(context, grid)
    )

    InteriorScreenBase(
        viewModel = viewModel,
        backgroundAssetPath = "LUGARES/deportivomiguelaleman/campo_beis.webp",
        title = "Campo de Béisbol",
        onExit = onExit
    )
}

private val DeportivoBeisCollisionGrid = CollisionGrid.emptyWithBorder()
