package ovh.gabrielhuav.pow.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.*

data class TutorialPage(
    val titleRes: StringResource,
    val bodyRes: StringResource,
    val buttonLetter: String? = null,
    val buttonColor: Color = Color.Gray,
    val emoji: String = "🕹️",
)

private val BtnA = Color(0xFF43A047)
private val BtnB = Color(0xFFE53935)
private val BtnX = Color(0xFF2196F3)
private val BtnY = Color(0xFFF9A825)

fun exteriorTutorialPages(): List<TutorialPage> = listOf(
    TutorialPage(Res.string.tutorial_page_move_title, Res.string.tutorial_page_move_body),
    TutorialPage(Res.string.tutorial_page_run_title, Res.string.tutorial_page_run_body, "A", BtnA),
    TutorialPage(Res.string.tutorial_page_ext_interact_title, Res.string.tutorial_page_ext_interact_body, "X", BtnX),
    TutorialPage(Res.string.tutorial_page_ext_attack_title, Res.string.tutorial_page_ext_attack_body, "B", BtnB),
    TutorialPage(Res.string.tutorial_page_ext_vehicle_title, Res.string.tutorial_page_ext_vehicle_body, "Y", BtnY),
    TutorialPage(Res.string.tutorial_page_ext_teleport_title, Res.string.tutorial_page_ext_teleport_body, emoji = "🗺️"),
)

fun interiorTutorialPages(): List<TutorialPage> = listOf(
    TutorialPage(Res.string.tutorial_page_move_title, Res.string.tutorial_page_move_body),
    TutorialPage(Res.string.tutorial_page_run_title, Res.string.tutorial_page_run_body, "A", BtnA),
    TutorialPage(Res.string.tutorial_page_int_interact_title, Res.string.tutorial_page_int_interact_body, "X", BtnX),
    TutorialPage(Res.string.tutorial_page_int_attack_title, Res.string.tutorial_page_int_attack_body, "B", BtnB),
    TutorialPage(Res.string.tutorial_page_int_mode_title, Res.string.tutorial_page_int_mode_body, "Y", BtnY),
)

@Composable
fun ControlsTutorialOverlay(
    titleRes: StringResource,
    pages: List<TutorialPage>,
    onDismiss: () -> Unit,
) {
    var pageIndex by remember { mutableIntStateOf(0) }
    val page = pages[pageIndex.coerceIn(0, pages.lastIndex)]
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth(0.9f)
                .background(Color(0xF21A1418), RoundedCornerShape(16.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(titleRes),
                    color = Color(0xFFD4AF37),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "✕",
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            if (page.buttonLetter != null) {
                Box(
                    Modifier.size(72.dp).background(page.buttonColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(page.buttonLetter, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(page.emoji, fontSize = 56.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(page.titleRes),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(page.bodyRes),
                color = Color(0xFFCCCCCC),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.indices.forEach { index ->
                    Box(
                        Modifier.size(8.dp).background(
                            if (index == pageIndex) Color(0xFFD4AF37) else Color(0xFF4A3A40),
                            CircleShape,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pageIndex > 0) {
                    OutlinedButton(
                        onClick = { pageIndex-- },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(Res.string.tutorial_prev), color = Color.White)
                    }
                }
                Button(
                    onClick = {
                        if (pageIndex < pages.lastIndex) pageIndex++ else onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                ) {
                    Text(
                        stringResource(
                            if (pageIndex < pages.lastIndex) {
                                Res.string.tutorial_next
                            } else {
                                Res.string.tutorial_done
                            },
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun ControlsTutorialPrompt(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(Res.string.tutorial_prompt_title)) },
        text = { Text(stringResource(Res.string.tutorial_prompt_msg)) },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(Res.string.tutorial_prompt_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(Res.string.tutorial_prompt_no))
            }
        },
    )
}
