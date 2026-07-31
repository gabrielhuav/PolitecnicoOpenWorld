package ovh.gabrielhuav.pow.features.main_menu.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxWithConstraints
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ovh.gabrielhuav.pow.domain.platform.PowModo
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.*
import ovh.gabrielhuav.pow.domain.platform.disponible
import ovh.gabrielhuav.pow.domain.platform.sePinta

@Composable
fun MainMenuScreen(
    onNavigateToMap: (isMultiplayer: Boolean, playerName: String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCollectibles: () -> Unit,
    onNavigateToStory: () -> Unit,
    onNavigateToStreetFighter: () -> Unit = {},
    /**
     * Lo que la pantalla necesita de la plataforma. Ver el patrón nº 4 (Controller) en
     * `README for IAS/10_ARQUITECTURA_SEPARACION.md` §2bis.
     */
    controller: MainMenuController,
    /**
     * Acción del botón MULTIJUGADOR. Android inyecta aquí su **gate de Google Sign-In** (que usa
     * `Intent` y `ActivityResult`, ambos inexistentes en iOS); por defecto se entra directo.
     */
    onMultiplayer: () -> Unit = { controller.onMultiplayerPressed() },
    /**
     * Chip de sesión de la esquina inferior izquierda ("Conectado: …" / "Modo local").
     * Es un **slot**: Android mete su chip de Firebase y iOS no mete nada. Así hay UNA sola
     * pantalla en vez de dos copias que se desincronizan.
     */
    chipDeCuenta: @Composable () -> Unit = {},
) {
    val state by controller.state.collectAsState()

    // Nombre de jugador recordado entre sesiones. Se prellena al abrir.
    // (2026-07-15) "HUELUM VS. GOYA" ya es PÚBLICO: el botón se muestra siempre. Lo que ahora
    // gatea el Modo Desarrollador son RYU y KEN dentro del selector (ver StreetFighterViewModel).
    LaunchedEffect(Unit) {
        if (state.playerName.isBlank()) {
            val saved = controller.nombreGuardado()
            if (saved.isNotBlank()) controller.updatePlayerName(saved)
        }
    }

    val bg = Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))

    // `LocalConfiguration` es de Android. `BoxWithConstraints` da lo mismo y es multiplataforma.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(bg)) {
        val isLandscape = maxWidth > maxHeight
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { TitleText(small = true) }

                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MenuButtonsList(
                        state = state,
                        controller = controller,
                        onNavigateToMap = onNavigateToMap,
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToCollectibles = onNavigateToCollectibles,
                        onNavigateToStory = onNavigateToStory,
                        onMultiplayerClick = onMultiplayer,
                        onNavigateToStreetFighter = onNavigateToStreetFighter
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TitleText(small = false)
                Spacer(modifier = Modifier.height(48.dp))
                MenuButtonsList(
                    state = state,
                    controller = controller,
                    onNavigateToMap = onNavigateToMap,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToCollectibles = onNavigateToCollectibles,
                    onNavigateToStory = onNavigateToStory,
                    onMultiplayerClick = onMultiplayer,
                    onNavigateToStreetFighter = onNavigateToStreetFighter
                )
            }
        }

        // ⚠️ En iOS `etiquetaVersion` es null y esto NO se pinta: la App Store no admite etiquetas
        // tipo PRE-ALPHA en una ficha publicada. En Android sigue saliendo igual que siempre.
        controller.versionName?.let { version ->
            Text(
                text = stringResource(Res.string.menu_version, version),
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }

        // Chip de estado de sesión (abajo-izquierda). Lo aporta la plataforma: Android pinta el de
        // Firebase, iOS no pinta nada porque allí no hay Google Sign-In.
        Box(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) { chipDeCuenta() }

        // ─── Diálogo de nombre del jugador (solo aparece tras warmup OK) ──
        if (state.showMultiplayerDialog) {
            AlertDialog(
                onDismissRequest = { controller.updateShowMultiplayerDialog(false) },
                title = { Text(stringResource(Res.string.menu_mp_dialog_title)) },
                text = {
                    OutlinedTextField(
                        value = state.playerName,
                        onValueChange = { controller.updatePlayerName(it) },
                        label = { Text(stringResource(Res.string.menu_mp_username_label)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            controller.updateShowMultiplayerDialog(false)
                            // "Jugador_" es un id generado de respaldo (no es texto de UI traducible).
                            val finalName = state.playerName.ifBlank { "Jugador_${(1000..9999).random()}" }
                            controller.guardarNombre(finalName)   // recuérdalo para la próxima vez
                            onNavigateToMap(true, finalName)
                        }
                    ) { Text(stringResource(Res.string.menu_mp_connect)) }
                },
                dismissButton = {
                    TextButton(onClick = { controller.updateShowMultiplayerDialog(false) }) {
                        Text(stringResource(Res.string.menu_cancel))
                    }
                }
            )
        }

        // ─── Spinner bloqueante mientras Render despierta ────────────────
        if (state.isWarmingUp) {
            WarmupDialog(
                secondsElapsed = state.warmupSeconds,
                onCancel = { controller.cancelWarmup() }
            )
        }

        // ─── Banner de error si el warmup hace timeout ───────────────────
        if (state.warmupFailed) {
            AlertDialog(
                onDismissRequest = { controller.dismissWarmupError() },
                title = { Text(stringResource(Res.string.menu_warmup_fail_title)) },
                text = { Text(stringResource(Res.string.menu_warmup_fail_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        controller.dismissWarmupError()
                        onMultiplayer() // reintenta (re-aplica el gate de sesión)
                    }) { Text(stringResource(Res.string.menu_retry)) }
                },
                dismissButton = {
                    TextButton(onClick = { controller.dismissWarmupError() }) {
                        Text(stringResource(Res.string.menu_close))
                    }
                }
            )
        }
    }
}

@Composable
private fun TitleText(small: Boolean) {
    AutoResizeText(
        text = "POLITÉCNICO", targetFontSize = if (small) 36.sp else 42.sp,
        fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 0.1.em,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    )
    AutoResizeText(
        text = "OPEN WORLD", targetFontSize = if (small) 22.sp else 26.sp,
        fontWeight = FontWeight.Bold, color = Color(0xFFD4AF37), letterSpacing = 0.3.em,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Texto de una sola línea que reduce su tamaño de fuente automáticamente hasta
 * caber en el ancho disponible. Garantiza que el contenido NUNCA se corte ni
 * salte de línea, sin importar el tamaño/relación de aspecto de la pantalla.
 * El letterSpacing se expresa en `em` para que escale junto con la fuente.
 */
@Composable
private fun AutoResizeText(
    text: String,
    targetFontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight,
    letterSpacing: TextUnit,
    modifier: Modifier = Modifier
) {
    var fontSize by remember(text, targetFontSize) { mutableStateOf(targetFontSize) }
    var readyToDraw by remember(text, targetFontSize) { mutableStateOf(false) }
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        letterSpacing = letterSpacing,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = modifier.drawWithContent { if (readyToDraw) drawContent() },
        onTextLayout = { result ->
            if (result.didOverflowWidth || result.lineCount > 1) {
                fontSize *= 0.92f
            } else {
                readyToDraw = true
            }
        }
    )
}

@Composable
fun MenuButtonsList(
    state: MainMenuUiState,
    controller: MainMenuController,
    onNavigateToMap: (isMultiplayer: Boolean, playerName: String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCollectibles: () -> Unit,
    onNavigateToStory: () -> Unit,
    onMultiplayerClick: () -> Unit = { controller.onMultiplayerPressed() },
    onNavigateToStreetFighter: () -> Unit = {}
) {
    // 🍏 Qué botones se pintan lo decide el catálogo de `:shared` (`PowModo.sePinta()`), no esta
    // pantalla: así la regla es UNA y se testea sin necesidad de un Mac. En Android `disponible()`
    // es true para todos → esta pantalla se ve EXACTAMENTE igual que antes.
    //
    // 🚧 Un modo `enObras()` SÍ se pinta pero NO navega: abre el aviso de abajo. Hoy eso solo pasa
    // en iOS, para poder comparar los dos menús mientras se porta el mundo abierto. Se apaga entero
    // con `MODOS_EN_OBRAS_VISIBLES = false` (ver `PowModos.kt`) antes de subir a la App Store.
    var modoEnObras by remember { mutableStateOf<PowModo?>(null) }

    // MUNDO LIBRE: el open world sin campaña (antes "Iniciar Juego"). Spawn por defecto.
    if (PowModo.MUNDO_LIBRE.sePinta()) {
        BotonDeModo(
            modo = PowModo.MUNDO_LIBRE,
            texto = stringResource(Res.string.menu_start_game),
            mostrarInsignias = controller.mostrarInsignias,
            habilitado = !state.isLoading && !state.isWarmingUp,
            alPulsarEnObras = { modoEnObras = it },
        ) {
            controller.onStartGame()
            onNavigateToMap(false, null)
        }
        Spacer(Modifier.height(16.dp))
    }

    // MODO HISTORIA: abre la pantalla de campaña (prólogo + elegir escuela + cargar partida).
    if (PowModo.MODO_HISTORIA.sePinta()) {
        BotonDeModo(
            modo = PowModo.MODO_HISTORIA,
            texto = stringResource(Res.string.menu_load_game),
            mostrarInsignias = controller.mostrarInsignias,
            habilitado = !state.isWarmingUp,
            alPulsarEnObras = { modoEnObras = it },
            alPulsar = onNavigateToStory,
        )
        Spacer(Modifier.height(16.dp))
    }

    // El botón MULTIJUGADOR dispara el warmup ANTES de mostrar el diálogo
    // de nombre. Mientras dura el warmup queda deshabilitado para evitar
    // que el usuario lance dos pings en paralelo.
    if (PowModo.MULTIJUGADOR.sePinta()) {
        BotonDeModo(
            modo = PowModo.MULTIJUGADOR,
            texto = stringResource(Res.string.menu_multiplayer),
            mostrarInsignias = controller.mostrarInsignias,
            habilitado = !state.isWarmingUp,
            alPulsarEnObras = { modoEnObras = it },
            alPulsar = onMultiplayerClick,
        )
        Spacer(Modifier.height(16.dp))
    }

    modoEnObras?.let { AvisoEnObras { modoEnObras = null } }

    MenuButton(
        text = stringResource(Res.string.menu_settings),
        onClick = onNavigateToSettings,
        enabled = !state.isWarmingUp,
        color = Color(0xFF6B1C3A)
    )
    Spacer(Modifier.height(16.dp))

    MenuButton(
        text = stringResource(Res.string.menu_collectibles),
        onClick = onNavigateToCollectibles,
        enabled = !state.isWarmingUp,
        color = Color(0xFF6B1C3A)
    )

    // 🆕 HUELUM VS. GOYA — MODO PRINCIPAL: botón DESTACADO y ANIMADO (pulso + brillo dorado que
    // barre + borde y sombra que laten) para que resalte enormemente sobre los demás modos.
    Spacer(Modifier.height(20.dp))
    WithCornerBadge(stringResource(Res.string.badge_beta), Color(0xFF1C6B4A), controller.mostrarInsignias) {
        FeaturedStreetFighterButton(
            text = stringResource(Res.string.menu_street_fighter),
            tag = stringResource(Res.string.menu_featured_tag),
            onClick = onNavigateToStreetFighter,
            enabled = !state.isWarmingUp,
        )
    }
}

/**
 * Botón ESTELAR del modo pelea: pulso de escala, barrido de brillo dorado, borde y sombra
 * doradas que laten. Diseñado para gritar "esta es la modalidad principal".
 */
@Composable
private fun FeaturedStreetFighterButton(text: String, tag: String, onClick: () -> Unit, enabled: Boolean) {
    val shape = CutCornerShape(topStart = 20.dp, bottomEnd = 20.dp)
    val gold = Color(0xFFFFD54A)
    val tr = rememberInfiniteTransition(label = "sfFeatured")
    val scale by tr.animateFloat(
        1f, 1.05f,
        infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale",
    )
    val glow by tr.animateFloat(
        0.45f, 1f,
        infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow",
    )
    val shimmer by tr.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "shimmer",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .height(76.dp)
            .graphicsLayer { scaleX = if (enabled) scale else 1f; scaleY = if (enabled) scale else 1f }
            .shadow(elevation = 18.dp, shape = shape, ambientColor = gold, spotColor = gold)
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(Color(0xFF8A0F32), Color(0xFFC4143C), Color(0xFF8A0F32))),
            )
            .border(BorderStroke(3.dp, gold.copy(alpha = if (enabled) glow else 0.5f)), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Barrido de brillo dorado (detrás del texto)
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val w = size.width
                    val hl = w * 0.30f
                    val x = -hl + (w + hl) * shimmer
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.5f to gold.copy(alpha = 0.40f),
                            1f to Color.Transparent,
                            startX = x,
                            endX = x + hl,
                        ),
                    )
                },
        )
        // 🆕 (2026-07-21) FIX S24: el botón tiene alto FIJO (76.dp); si el rótulo o el tag se
        // parten en dos líneas con la fuente del sistema grande, el contenido se recorta y la
        // etiqueta de estado parece descolgada. Una línea cada uno, siempre.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "★ $text ★",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = tag,
                color = gold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 🆕 Etiqueta de estado (ALPHA / BETA). Se monta en la esquina superior derecha del botón.
 *
 * ⚠️ (2026-07-21) Reportado en Galaxy S24: la etiqueta "se iba a otro renglón". El texto
 * NUNCA debe partirse ni escalar sin límite — con la fuente del sistema en grande, 9.sp +
 * letterSpacing crecía y la etiqueta se envolvía en dos líneas, que es lo que se veía como
 * un renglón extra. `maxLines = 1` + `softWrap = false` lo impiden en cualquier densidad.
 */
@Composable
private fun StageBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    // 🆕 (2026-07-22) El letrero "respira": transparencia pulsante entre 50 % y 75 %.
    val badgeAlpha by rememberInfiniteTransition(label = "stageBadge").animateFloat(
        initialValue = 0.5f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "stageBadgeAlpha",
    )
    Text(
        text = text,
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        modifier = modifier
            .graphicsLayer { alpha = badgeAlpha }
            .clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * 🆕 Envuelve un botón y le pega la etiqueta a CABALLO de su esquina superior derecha
 * (ni totalmente fuera ni totalmente dentro), sin ocupar un renglón extra del menú.
 *
 * ⚠️ (2026-07-21) FIX S24: antes la etiqueta salía con `offset(x = 8, y = -8)`. Ese
 * desplazamiento VERTICAL negativo la sacaba por ARRIBA del botón, invadiendo el espacio
 * del botón anterior — con fuentes grandes la etiqueta crece y aparecía pegada a la fila
 * de arriba (el "otro renglón" del reporte). Ahora solo se desplaza en HORIZONTAL, hacia
 * el margen lateral que siempre existe (los botones ocupan 85-92 % del ancho), así que no
 * puede colisionar con nada por mucho que crezca la fuente del sistema.
 * El `Box` fija su ancho con `fillMaxWidth()` para que la esquina de referencia sea
 * estable y no dependa de cómo se midió el botón de dentro.
 */
@Composable
internal fun WithCornerBadge(
    text: String,
    color: Color,
    /**
     * ⚠️ `false` en iOS: la App Store no admite etiquetas PRE-ALPHA/BETA en una ficha publicada.
     * El botón se pinta IGUAL, solo desaparece la insignia — así no hay dos layouts que mantener.
     */
    mostrar: Boolean,
    button: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        button()
        if (mostrar) {
            StageBadge(
                text = text,
                color = color,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 12.dp),
            )
        }
    }
}

@Composable
fun MenuButton(text: String, onClick: () -> Unit, enabled: Boolean = true, color: Color = Color(0xFF6B1C3A)) {
    val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
    Button(
        onClick = onClick, enabled = enabled, shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White, disabledContainerColor = Color(0xFF2A1C21), disabledContentColor = Color.Gray),
        modifier = Modifier.fillMaxWidth(0.85f).height(56.dp).shadow(elevation = if (enabled) 8.dp else 0.dp, shape = shape)
        // 🆕 (2026-07-21) FIX S24: el rótulo NO debe partirse en dos líneas con la fuente del
        // sistema en grande (el botón tiene alto fijo de 56.dp y la 2ª línea se recortaba).
    ) {
        Text(
            text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Diálogo modal NO descartable (solo el botón CANCELAR cierra) que muestra
 * el progreso del warmup del servidor de Render. Estilo coherente con el
 * resto del menú: gradiente vino + acento dorado, esquinas cortadas.
 */
@Composable
private fun WarmupDialog(secondsElapsed: Int, onCancel: () -> Unit) {
    Dialog(
        onDismissRequest = { /* no descartable por fuera */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(Color(0xFF1A0A10), shape = shape)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.menu_warmup_title),
                    color = Color(0xFFD4AF37),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                CircularProgressIndicator(
                    color = Color(0xFFD4AF37),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(56.dp)
                )

                Text(
                    text = stringResource(Res.string.menu_warmup_text, secondsElapsed),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Text(
                    text = stringResource(Res.string.menu_warmup_hint),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text(stringResource(Res.string.menu_cancel_caps), fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
            }
        }
    }
}
