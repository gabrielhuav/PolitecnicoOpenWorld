package ovh.gabrielhuav.pow.features.campaign.ui

import android.content.Context
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.data.repository.StoryBoxLayout
import ovh.gabrielhuav.pow.data.repository.StoryLayoutRepository
import ovh.gabrielhuav.pow.domain.models.campaign.CampaignSchool
import ovh.gabrielhuav.pow.domain.models.campaign.StoryComicCatalog

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

    val soundManager = remember { ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(context) }

    // ¿Es la secuencia de INTRO (prólogo IntroPOW1..8) y no el OUTRO (IntroPOW9..11)?
    // La música de fondo del cómic solo suena en la intro.
    val isIntroSequence = sequenceId != StoryComicCatalog.ENCB_OUTRO_ID

    // Las imágenes del cómic son HORIZONTALES: forzamos orientación landscape mientras se
    // ve la intro y restauramos la orientación previa al salir (entrar al mundo / volver).
    DisposableEffect(Unit) {
        soundManager.stopWalk()
        soundManager.stopRun()
        // Corta cualquier SFX previo (p. ej. el jingle de "misión cumplida") al ABRIR el cómic,
        // para que no siga sonando durante toda la secuencia.
        soundManager.stopAllStorySounds()
        // Música de fondo del cómic de la intro (IntroPOW1..8): suena en bucle TODA la secuencia
        // (los SFX por panel usan SoundPool y no la cortan). Se detiene al salir de la pantalla.
        if (isIntroSequence) soundManager.playPrankedyRemixMusic()
        // La orientación (SIEMPRE landscape en cómics/juego) la gestiona MainActivity por destino
        // de navegación; aquí solo manejamos el audio.
        onDispose {
            soundManager.stopAllStorySounds()
            soundManager.stopPrankedyRemixMusic()
        }
    }

    if (panels.isEmpty()) { onBegin(); return }

    var index by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    // Modo Desarrollador: el botón "Editar" (editor del cuadro de texto del cómic) solo se muestra
    // con el Modo Desarrollador activo; apagado, queda oculto para los jugadores.
    val developerMode = remember { ovh.gabrielhuav.pow.data.repository.SettingsRepository(context).getDeveloperMode() }

    val panel = panels[index]

    LaunchedEffect(panel.assetPath) {
        val assetName = panel.assetPath.substringAfterLast("/")
        when (assetName) {
            "IntroPOW1.webp" -> {
                soundManager.stopAllStorySounds()
                soundManager.playFlash()
            }
            "IntroPOW2.webp" -> {
                soundManager.stopAllStorySounds()
                soundManager.playStoryRunning(loop = true)
                soundManager.playCrystal()
                soundManager.playQueTeTraes()
            }
            "IntroPOW3.webp" -> {
                soundManager.stopAllStorySounds()
                soundManager.playHitWall()
                soundManager.playContestame()
            }
            "IntroPOW4.webp" -> {
                soundManager.stopAllStorySounds()
                soundManager.playStoryRunning(loop = true)
                soundManager.playParale()
            }
            "IntroPOW5.webp" -> {
                soundManager.stopAllStorySounds()
                soundManager.playStoryRunning(loop = true)
                soundManager.playPuerquito()
            }
            "IntroPOW6.webp" -> {
                soundManager.stopAllStorySounds()
                soundManager.playStoryRunning(loop = true)
                soundManager.playPolice1()
                delay(3000)
                soundManager.playPolice2(loop = true)
            }
            "IntroPOW7.webp" -> {
                soundManager.stopStoryRunning()
                soundManager.playPolice2(loop = true)
                soundManager.playBottleFalling()
            }
            "IntroPOW8.webp" -> {
                soundManager.playPolice2(loop = true)
                soundManager.playCrystal()
                soundManager.playZombiesAreComing()
            }
            "IntroPOW9.webp" -> {
                soundManager.stopAllStorySounds()
                soundManager.playDoorOpen()
            }
            "IntroPOW10.webp" -> {
                soundManager.stopAllStorySounds()
                soundManager.playScare()
            }
            "IntroPOW11.webp" -> {
                soundManager.stopAllStorySounds()
            }
        }
    }
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

    // SKIN: ciertos paneles del cómic (IntroPOW9/10/11/15) cambian según la skin elegida en
    // "Cambiar Skin": por defecto IntroPOW9.webp (Lázaro/hombre); con la mujer IntroPOW9Girl.webp;
    // con el robot IntroPOW9Robot.webp. El resto de paneles NO cambian. Si la variante no existe,
    // cae al panel por defecto.
    val comicSuffix = remember {
        ovh.gabrielhuav.pow.data.repository.SettingsRepository(context).getPlayerSkin().comicSuffix
    }
    val image = remember(panel.assetPath, comicSuffix) {
        loadComicPanel(context, panel.assetPath, comicSuffix)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D11))) {
        val maxH = maxHeight
        val density = LocalDensity.current
        val hPx = with(density) { maxH.toPx() }
        val wPx = with(density) { maxWidth.toPx() }
        // RECTÁNGULO REAL donde ContentScale.Fit dibuja la imagen (dentro del marco de 20.dp).
        // Anclamos el cuadro de texto a ESTE rectángulo —no a la pantalla completa— para que quede
        // SIEMPRE dentro del recuadro blanco del cómic en cualquier tamaño/relación de pantalla (el
        // "letterbox" cambia según el dispositivo). Sin imagen, se usa la pantalla completa.
        val padPx = with(density) { 20.dp.toPx() }
        val availW = (wPx - 2f * padPx).coerceAtLeast(1f)
        val availH = (hPx - 2f * padPx).coerceAtLeast(1f)
        val imgW = (image?.width ?: 0).toFloat().coerceAtLeast(1f)
        val imgH = (image?.height ?: 0).toFloat().coerceAtLeast(1f)
        val fitScale = if (image != null) minOf(availW / imgW, availH / imgH) else 1f
        val dispW = if (image != null) imgW * fitScale else wPx
        val dispH = if (image != null) imgH * fitScale else hPx
        val imgTopPx = (hPx - dispH) / 2f   // imagen centrada vertical (padding simétrico)

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

        // Cuadro de texto (posicionable) ANCLADO al rectángulo real de la imagen (no a la pantalla),
        // para que no se salga del recuadro blanco al cambiar el tamaño/relación de pantalla.
        val topDp = with(density) { (imgTopPx + dispH * topFrac.coerceIn(0f, 0.95f)).toDp() }
        val boxDp = with(density) { (dispH * heightFrac.coerceIn(0.06f, 0.7f)).toDp() }
        val boxWDp = with(density) { (dispW * widthFrac.coerceIn(0.2f, 1f)).toDp() }
        val noImageBg = image == null
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = topDp)
                .width(boxWDp)
                .height(boxDp)
                .then(
                    if (editing) Modifier
                        .background(Color(0x553F51B5), RoundedCornerShape(8.dp))
                        .pointerInput(index) {
                            detectDragGestures { _, drag ->
                                topFrac = (topFrac + drag.y / dispH).coerceIn(0f, 0.95f)
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
            PillButton(stringResource(R.string.story_intro_back)) { onBack() }
            Text(
                "${index + 1}/${panels.size}",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0x88000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            if (developerMode) {
                PillButton(if (editing) stringResource(R.string.story_ed_done) else stringResource(R.string.story_ed_edit), if (editing) Color(0xAA2E7D32) else Color(0xAA37474F)) {
                    editing = !editing
                }
            }
            Box(modifier = Modifier.weight(1f))
            PillButton(if (isLast) stringResource(R.string.story_intro_start) else stringResource(R.string.story_intro_skip), Color(0xAA8C2A2A)) { onBegin() }
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
                    stringResource(R.string.story_ed_move),
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
                    stringResource(R.string.story_ed_box, topFrac, heightFrac, widthFrac, fontSp),
                    color = Color(0xFFCFD8DC), fontSize = 11.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillButton(stringResource(R.string.story_ed_up), Color(0xAA37474F), Modifier.weight(1f)) { topFrac = (topFrac - 0.01f).coerceIn(0f, 0.95f) }
                    PillButton(stringResource(R.string.story_ed_down), Color(0xAA37474F), Modifier.weight(1f)) { topFrac = (topFrac + 0.01f).coerceIn(0f, 0.95f) }
                    PillButton(stringResource(R.string.story_ed_h_minus), Color(0xAA37474F), Modifier.weight(1f)) { heightFrac = (heightFrac - 0.01f).coerceIn(0.06f, 0.7f) }
                    PillButton(stringResource(R.string.story_ed_h_plus), Color(0xAA37474F), Modifier.weight(1f)) { heightFrac = (heightFrac + 0.01f).coerceIn(0.06f, 0.7f) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillButton(stringResource(R.string.story_ed_w_minus), Color(0xAA37474F), Modifier.weight(1f)) { widthFrac = (widthFrac - 0.02f).coerceIn(0.2f, 1f) }
                    PillButton(stringResource(R.string.story_ed_w_plus), Color(0xAA37474F), Modifier.weight(1f)) { widthFrac = (widthFrac + 0.02f).coerceIn(0.2f, 1f) }
                    PillButton(stringResource(R.string.story_ed_f_minus), Color(0xAA37474F), Modifier.weight(1f)) { fontSp = (fontSp - 1f).coerceIn(8f, 40f) }
                    PillButton(stringResource(R.string.story_ed_f_plus), Color(0xAA37474F), Modifier.weight(1f)) { fontSp = (fontSp + 1f).coerceIn(8f, 40f) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillButton(stringResource(R.string.common_save), Color(0xAA2E7D32), Modifier.weight(1f)) {
                        repo.save(index, StoryBoxLayout(topFrac, heightFrac, fontSp, widthFrac))
                    }
                    PillButton(stringResource(R.string.story_ed_all), Color(0xAA00695C), Modifier.weight(1f)) {
                        repo.saveAll(panels.size, StoryBoxLayout(topFrac, heightFrac, fontSp, widthFrac))
                    }
                    PillButton(stringResource(R.string.story_ed_export), Color(0xAA1565C0), Modifier.weight(1f)) {
                        // Guarda el panel actual y exporta TODO a JSON (+ Logcat con los defaults).
                        repo.save(index, StoryBoxLayout(topFrac, heightFrac, fontSp, widthFrac))
                        exportLauncher.launch("story_layout.json")
                    }
                }
            }
        } else {
            Text(
                text = if (isLast) stringResource(R.string.story_tap_start) else stringResource(R.string.story_tap_continue),
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

// Paneles del cómic que CAMBIAN según la skin (Cambiar Skin). Para estos, si la skin tiene
// `comicSuffix` (p. ej. "Girl"/"Robot"), se intenta primero IntroPOW9Girl.webp / IntroPOW9Robot.webp;
// si no existe, se usa el panel por defecto (IntroPOW9.webp = Lázaro/hombre).
private val SKIN_VARIANT_PANELS = setOf(
    "STORY/INTRO/IntroPOW9.webp",
    "STORY/INTRO/IntroPOW10.webp",
    "STORY/INTRO/IntroPOW11.webp",
    "STORY/INTRO/IntroPOW15.webp"
)

// Carga el bitmap del panel, eligiendo la variante de skin cuando aplica (con fallback al default).
private fun loadComicPanel(
    context: Context,
    assetPath: String,
    comicSuffix: String
): androidx.compose.ui.graphics.ImageBitmap? {
    val candidates = buildList {
        if (comicSuffix.isNotEmpty() && assetPath in SKIN_VARIANT_PANELS) {
            add(assetPath.removeSuffix(".webp") + comicSuffix + ".webp")   // variante de skin
        }
        add(assetPath)   // panel por defecto / fallback
    }
    for (path in candidates) {
        val bmp = try {
            context.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() }
        } catch (_: Exception) { null }
        if (bmp != null) return bmp
    }
    return null
}


