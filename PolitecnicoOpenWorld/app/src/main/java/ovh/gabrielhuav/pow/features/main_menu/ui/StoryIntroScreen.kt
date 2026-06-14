package ovh.gabrielhuav.pow.features.main_menu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.CampaignSchool

/**
 * Pantalla de intro de la campaña ("Listo para Iniciar"). Es un PLACEHOLDER: en el
 * futuro aquí irán los banners/sprites que narren el prólogo (la broma de Prankedy
 * en la ENCB, etc.). Por ahora confirma la escuela elegida y, al "INICIAR", arranca
 * el mundo (MainActivity guarda la partida y fija el spawn). "VOLVER" regresa al
 * menú de campaña.
 */
@Composable
fun StoryIntroScreen(
    school: CampaignSchool,
    onBegin: () -> Unit,
    onBack: () -> Unit
) {
    val bg = Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.story_intro_ready),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // Escuela de inicio (displayName es nombre propio, no se traduce).
            Text(
                text = stringResource(R.string.story_intro_school, school.displayName),
                color = Color(0xFFD4AF37),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            // PLACEHOLDER del prólogo (futuros banners/sprites de la historia).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x33000000), shape = RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.story_intro_placeholder),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(28.dp))

            MenuButton(
                text = stringResource(R.string.story_intro_begin),
                onClick = onBegin,
                color = Color(0xFF8C2A2A)
            )
            Spacer(Modifier.height(12.dp))
            MenuButton(
                text = stringResource(R.string.story_back),
                onClick = onBack,
                color = Color(0xFF4A1226)
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
