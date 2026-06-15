package ovh.gabrielhuav.pow.features.main_menu.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import ovh.gabrielhuav.pow.data.repository.StoryBoxLayout
import ovh.gabrielhuav.pow.data.repository.StoryLayoutRepository
import ovh.gabrielhuav.pow.domain.models.CampaignSchool
import ovh.gabrielhuav.pow.domain.models.StoryComicCatalog

/**
 * Intro del Modo Historia como COMIC (assets/STORY/INTRO/IntroPOW1..8.webp, imagenes
 * HORIZONTALES → la pantalla fuerza orientacion landscape mientras dura la intro). El texto de
 * la historia se dibuja sobre el RECUADRO BLANCO de cada imagen; como el recuadro esta a distinta
 * altura por panel, hay un EDITOR in-game (boton "Editar") para mover/redimensionar el cuadro
 * de texto y el tamano de letra POR PANEL, y se persiste en StoryLayoutRepository. Navegas
 * tocando derecha/izquierda; "Saltar" salta toda la intro; en el ultimo panel -> INICIAR.
 */
@Composable
fun StoryIntroScreen(
    school: CampaignSchool,
    onBegin: () -> Unit,
    onBack: () -> Unit,
    // MODO HISTORIA: si != null reproduce esa secuencia (p. ej. StoryComicCatalog.ENCB_OUTRO_ID,
    // 2ª parte de la intro) en vez del prologo de la escuela. Reusa toda la lógica del visor.
    sequenceId: String? = null
) {
    val context = LocalContext.current
    val panels = remember(school.id, sequenceId) {
        if (sequenceId != null) StoryComicCatalog.sequence(sequenceId)
        else StoryComicCatalog.forSchool(school.id)
    }
    val repo = remember { StoryLayoutRepository(context) }

    // EXPORTAR: vuelca la configuración de TODOS los paneles a un archivo JSON (para no
    // depender solo del dispositivo) y además escribe en Logcat (tag STORY_LAYOUT) las líneas
    // `ComicPanel(...)` listas para PEGAR como defaults en StoryComicCatalog.kt (repo).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val f3 = { v: Float -> "%.3f".format(java.util.Locale.US, v) }
            val json = buildString {
                append("[\n")
                panels.forEachIndexed { i, p ->
                    val l = repo.layoutFor(i, StoryBoxLayout(p.boxTopFrac, p.boxHeightFrac, p.fontSp, p.boxWidthFrac))
                    append("""  {"panel": ${i + 1}, "asset": "${p.assetPath}", "topFrac": ${f3(l.topFrac)}, "heightFrac": ${f3(l.heightFrac)}, "widthFrac": ${f3(l.widthFrac)}, "fontSp": ${"%.0f".format(java.util.Locale.US, l.fontSp)}}""")
                    append(if (i < panels.size - 1) ",\n" else "\n")
                }
                append("]\n")
            }
            val kt = buildString {
                append("// Pega estos defaults en StoryComicCatalog (uno por ComicPanel):\n")
                panels.forEachIndexed { i, p ->
                    val l = repo.layoutFor(i, StoryBoxLayout(p.boxTopFrac, p.boxHeightFrac, p.fontSp, p.boxWidthFrac))
                    append("ComicPanel(\"${p.assetPath}\", \"...\", boxTopFrac = ${f3(l.topFrac)}f, boxHeightFrac = ${f3(l.heightFrac)}f, fontSp = ${"%.0f".format(java.util.Locale.US, l.fontSp)}f, boxWidthFrac = ${f3(l.widthFrac)}f),\n")
                }
            }
            android.util.Log.d("STORY_LAYOUT", "\n$kt")
            try { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } } catch (_: Exception) {}
        }
    }

    // Las imágenes del cómic son HORIZONTALES: forzamos orientación landscape mientras se
    // ve la intro y restauramos la orientación previa al salir (entrar al mundo / volver).
    DisposableEffect(Unit) {
        val activity = context.findActivityOrNull()
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    if (panels.isEmpty()) { onBegin(); return }

    var index by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }

    val panel = panels[index]
    val isLast = index >= panels.size - 1

    // Layout del cuadro de texto del panel actual (guardado o default del catalogo).
    val saved = remember(index) {
        repo.layoutFor(index, StoryBoxLayout(panel.boxTopFrac, panel.boxHeightFrac, panel.fontSp, panel.boxWidthFrac))
    }
    var topFrac by remember(index) { mutableFloatStateOf(saved.topFrac) }
    var heightFrac by remember(index) { mutableFloatStateOf(saved.heightFrac) }
    var fontSp by remember(index) { mutableFloatStateOf(saved.fontSp) }
    var widthFrac by remember(index) { mutableFloatStateOf(saved.widthFrac) }
    // Desplazamiento (px) del panel de controles del editor: el usuario lo arrastra para que
    // no tape el cuadro de texto. Se conserva entre paneles (no depende de `index`).
    var panelOffX by remember { mutableFloatStateOf(0f) }
    var panelOffY by remember { mutableFloatStateOf(0f) }

    val image = remember(panel.assetPath) {
        try {
            context.assets.open(panel.assetPath).use {
                android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap()
            }
        } catch (_: Exception) { null }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D11))) {
        val maxH = maxHeight
        val hPx = with(LocalDensity.current) { maxH.toPx() }

        // Imagen del panel.
        if (image != null) {
            // MARCO DE AISLAMIENTO: contenedor elástico (fillMaxSize) con padding seguro para
            // que la ilustración "flote" centrada con margen y NUNCA toque los bordes físicos.
            // ContentScale.Fit garantiza que el panel COMPLETO quepa sin recortes en celulares
            // alargados/pequeños; el fondo oscuro temático del BoxWithConstraints rellena
            // limpiamente las barras laterales/superiores ("letterbox") cuando la proporción
            // de la imagen no coincide con la de la pantalla.
            Box(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))
            ))
        }

        // Navegacion por toque (solo si NO se esta editando).
        if (!editing) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { if (index > 0) index-- })
                Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { if (isLast) onBegin() else index++ })
            }
        }

        // Cuadro de texto (posicionable).
        val topDp = maxH * topFrac.coerceIn(0f, 0.95f)
        val boxDp = maxH * heightFrac.coerceIn(0.06f, 0.7f)
        val noImageBg = image == null
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(widthFrac.coerceIn(0.2f, 1f))
                .offset(y = topDp)
                .height(boxDp)
                .then(
                    if (editing) Modifier
                        .background(Color(0x553F51B5), RoundedCornerShape(8.dp))
                        .pointerInput(index) {
                            detectDragGestures { _, drag ->
                                topFrac = (topFrac + drag.y / hPx).coerceIn(0f, 0.95f)
                            }
                        }
                    else if (noImageBg) Modifier
                        .background(Color(0xF2FFFFFF), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = panel.text,
                color = Color(0xFF14141A),
                fontSize = fontSp.sp,
                lineHeight = (fontSp * 1.35f).sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Barra superior: Volver - contador - Editar - Saltar/Iniciar.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillButton("< Volver") { onBack() }
            Text(
                "${index + 1}/${panels.size}",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0x88000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            PillButton(if (editing) "Listo" else "Editar", if (editing) Color(0xAA2E7D32) else Color(0xAA37474F)) {
                editing = !editing
            }
            Box(modifier = Modifier.weight(1f))
            PillButton(if (isLast) "Iniciar" else "Saltar", Color(0xAA8C2A2A)) { onBegin() }
        }

        // Panel del EDITOR (solo en modo edicion). Es ARRASTRABLE (asa superior) para que no
        // tape el cuadro de texto; el desplazamiento se aplica con .offset.
        if (editing) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(panelOffX.roundToInt(), panelOffY.roundToInt()) }
                    .fillMaxWidth()
                    .background(Color(0xE6101015))
                    .systemBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Asa para MOVER el panel (arrástrala). Doble toque la regresa abajo.
                Text(
                    "⠿  Mover panel  (arrástrame)",
                    color = Color(0xFFFFD54F), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                        .padding(vertical = 6.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { _, drag ->
                                panelOffX += drag.x
                                panelOffY += drag.y
                            }
                        }
                        .clickable { panelOffX = 0f; panelOffY = 0f }
                )
                Text(
                    "Cuadro - top=%.2f alto=%.2f ancho=%.2f letra=%.0f".format(topFrac, heightFrac, widthFrac, fontSp),
                    color = Color(0xFFCFD8DC), fontSize = 11.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillButton("Subir", Color(0xAA37474F), Modifier.weight(1f)) { topFrac = (topFrac - 0.01f).coerceIn(0f, 0.95f) }
                    PillButton("Bajar", Color(0xAA37474F), Modifier.weight(1f)) { topFrac = (topFrac + 0.01f).coerceIn(0f, 0.95f) }
                    PillButton("Alto -", Color(0xAA37474F), Modifier.weight(1f)) { heightFrac = (heightFrac - 0.01f).coerceIn(0.06f, 0.7f) }
                    PillButton("Alto +", Color(0xAA37474F), Modifier.weight(1f)) { heightFrac = (heightFrac + 0.01f).coerceIn(0.06f, 0.7f) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillButton("Ancho -", Color(0xAA37474F), Modifier.weight(1f)) { widthFrac = (widthFrac - 0.02f).coerceIn(0.2f, 1f) }
                    PillButton("Ancho +", Color(0xAA37474F), Modifier.weight(1f)) { widthFrac = (widthFrac + 0.02f).coerceIn(0.2f, 1f) }
                    PillButton("Letra -", Color(0xAA37474F), Modifier.weight(1f)) { fontSp = (fontSp - 1f).coerceIn(8f, 40f) }
                    PillButton("Letra +", Color(0xAA37474F), Modifier.weight(1f)) { fontSp = (fontSp + 1f).coerceIn(8f, 40f) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillButton("Guardar", Color(0xAA2E7D32), Modifier.weight(1f)) {
                        repo.save(index, StoryBoxLayout(topFrac, heightFrac, fontSp, widthFrac))
                    }
                    PillButton("Todas", Color(0xAA00695C), Modifier.weight(1f)) {
                        repo.saveAll(panels.size, StoryBoxLayout(topFrac, heightFrac, fontSp, widthFrac))
                    }
                    PillButton("Exportar", Color(0xAA1565C0), Modifier.weight(1f)) {
                        // Guarda el panel actual y exporta TODO a JSON (+ Logcat con los defaults).
                        repo.save(index, StoryBoxLayout(topFrac, heightFrac, fontSp, widthFrac))
                        exportLauncher.launch("story_layout.json")
                    }
                }
            }
        } else {
            Text(
                text = if (isLast) "Toca la pantalla para INICIAR" else "Toca a la derecha para continuar",
                color = Color(0xCCFFFFFF), fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    color: Color = Color(0xAA4A1226),
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .background(color, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

// Desenvuelve el Context (puede venir envuelto por LocaleHelper.wrap) hasta la Activity,
// para poder fijar la orientación de pantalla durante la intro.
private fun Context.findActivityOrNull(): Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

