package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfBox
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackType
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_BONUS_POWER_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_NEW_MOVE_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterData
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireballState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFrameDef
import ovh.gabrielhuav.pow.ui.components.ActionButton
import ovh.gabrielhuav.pow.ui.components.JoystickController
import ovh.gabrielhuav.pow.ui.components.PowButton
import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import ovh.gabrielhuav.pow.features.streetfighter.data.SfBtDevice
import ovh.gabrielhuav.pow.features.streetfighter.data.SfLanGame
import ovh.gabrielhuav.pow.features.streetfighter.data.SfFrameCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SfRoomSummary
import ovh.gabrielhuav.pow.features.streetfighter.data.SfSharedSheets
import ovh.gabrielhuav.pow.domain.streetfighter.SfVocesReglas
import ovh.gabrielhuav.pow.platform.audio.PowAudio
import ovh.gabrielhuav.pow.platform.audio.PowClip
import ovh.gabrielhuav.pow.platform.assets.PowAssets
import ovh.gabrielhuav.pow.platform.imagen.PowImagen
import ovh.gabrielhuav.pow.platform.imagen.decodificarReducido
import ovh.gabrielhuav.pow.features.streetfighter.data.SfTheme
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SfArcadeOutcome
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SfOnlineStatus
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SF_STOP_SPECIALS_EVENT
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.StreetFighterState
import ovh.gabrielhuav.pow.shared.recursos.*

// View Compose PURA del modo de pelea 1v1: observa el estado con collectAsState() y solo
// emite intenciones al VM (contrato MVVM, README for IAS 01/09).
//
// SEPARACIÓN DE CAPAS: esta View NO conoce recortes ni rutas de assets — todo viene del
// SfTheme (data/SfTheme.kt). Hoy usa SF_CLASSIC_THEME (assets del clon SF, temporales);
// la migración a assets propios de POW = crear otro SfTheme + JSONs de personajes, sin
// tocar esta View ni el VM. Ver README for IAS/ASSETS_STREETFIGHTER_MIGRACION.md.
//
// CONTROLES = los de POW: joystick (←→ caminar, ↑ saltar, ↓ agacharse; ↓↘→ + puño =
// especial) + el MISMO diamante Xbox A/B/X/Y de ActionButtonsController:
//   X (izq, azul) = puño ligero · Y (arriba, amarillo) = puño medio ·
//   B (der, rojo) = puño fuerte · A (abajo, verde) = patada (fuerza según el joystick:
//   neutro = ligera, adelante = media, atrás = fuerte).
// (2026-07-15) El modo es PÚBLICO; el Modo Desarrollador solo desbloquea a RYU/KEN
// (roster gateado por el VM: selectableFighters).

/**
 * Reproduce una voz o pieza larga completa; los efectos cortos van por SoundPool, que trunca los
 * archivos extensos.
 *
 * 🍏 Fase 5: la MECÁNICA (qué se interrumpe y qué no) ya NO vive aquí — está en
 * `SfVocesReglas`, en `:shared`, con tests que fijan las cuatro reglas afinadas de oído. Aquí solo
 * queda ejecutar la decisión con `PowAudio`, que funciona igual en Android y en iOS.
 *
 * ⚠️ Si vas a tocar CUÁNDO se corta una voz, el sitio es `SfVocesReglas`, no este archivo.
 */
private fun playSfSpecial(
    assetPath: String,
    activePlayers: MutableMap<String, PowClip>,
): Boolean {
    // SfVocesReglas conserva sus nombres lógicos históricos: cambiar el contenedor físico no debe
    // alterar unas reglas de interrupción afinadas de oído. El clip real sí se abre como AAC/M4A.
    val logicalPath = assetPath.removeSuffix(".m4a") + ".ogg"
    val decision = SfVocesReglas.decidir(logicalPath, activePlayers.keys.toSet())
    if (decision.saltar) return false
    decision.aDetener.forEach { clave ->
        activePlayers.remove(clave)?.let { clip ->
            // Se quita el aviso ANTES de parar: si no, `detener()` podría disparar el callback y
            // este intentaría borrar del mapa una entrada que ya no existe.
            runCatching { clip.alTerminar(null) }
            runCatching { clip.detener() }
            runCatching { clip.liberar() }
        }
    }

    val clip = PowAudio.cargarPista(assetPath) ?: return false
    // El aviso de fin es lo que MANTIENE VIVO el mapa: sin él, las voces terminadas seguirían
    // contando como "sonando" y las reglas de interrupción se degradarían poco a poco (un peleador
    // dejaría de gritar porque su intro, acabada hace rato, sigue apuntada como en curso).
    clip.alTerminar {
        if (activePlayers[logicalPath] === clip) activePlayers.remove(logicalPath)
        runCatching { clip.liberar() }
    }
    activePlayers[logicalPath] = clip
    clip.reproducir()
    return true
}

private fun releaseSfSpecials(activePlayers: MutableMap<String, PowClip>) {
    val clips = activePlayers.values.toList()
    activePlayers.clear()
    clips.forEach { clip ->
        runCatching { clip.alTerminar(null) }
        runCatching { clip.detener() }
        runCatching { clip.liberar() }
    }
}

@Composable
fun StreetFighterScreenCommon(
    onExitToMap: () -> Unit,
    controller: StreetFighterController,
    onlineStatusContent: @Composable (
        StreetFighterState,
        SfTheme,
        Boolean,
        StreetFighterController,
    ) -> Unit = { _, _, _, _ -> },
    onlinePlatformOverlays: @Composable (
        StreetFighterState,
        Boolean,
        (Boolean) -> Unit,
        StreetFighterController,
    ) -> Unit = { _, _, _, _ -> },
) {
    val state by controller.state.collectAsState()
    val theme = remember { SF_CLASSIC_THEME }

    // ---- Bitmaps del tema + sheets de los peleadores ACTUALES (decodificados una vez) ----
    val playerId = state.player.id
    val cpuId = state.cpu.id
    // 🆕 (2026-07-21) GAMA BAJA: submuestreo de los atlas de peleador. Con las hojas 20-29
    // los atlas llegaron a 2560×7168 (≈73 MB en ARGB_8888 por peleador, ×2 en pantalla):
    // demasiado para gama baja y para GPUs con tope de textura de 2048. A 1/2 quedan en
    // ~18 MB y 1280×3584. Todas las coordenadas del JSON se dividen por este factor.
    val sheetSample = remember { if (controller.isLowEndDevice()) 2 else 1 }
    val sheetSampleScale = remember(sheetSample) { 1f / sheetSample }
    // 🆕 Hojas pesadas SOLO en pelea (no en selector → menos RAM/lag al abrir el modo).
    // En selector solo se usan thumbs de region-decoder por card.
    // Tema (HUD/sombra/splash): livianos, se decodifican una vez en composición.
    val themeImages = remember(theme) {
        theme.imageFiles.associateWith { name ->
            runCatching { PowImagen.deAsset(theme.imagesDir + name) }.getOrNull()
        }.filterValues { it != null }.mapValues { it.value!! }
    }
    val playerData = remember(playerId) { SfFrameCatalog.load(playerId) }
    val cpuData = remember(cpuId) { SfFrameCatalog.load(cpuId) }
    val lowEnd = remember { controller.isLowEndDevice() }
    // 🆕 (2026-07-22, Bloque B) IDs de la pelea INCLUYENDO ambas identidades de una posible
    // metamorfosis. Es un SET (igualdad por contenido): cuando La Presidenta se transforma
    // a media pelea el set NO cambia → NO se re-decodifica nada (antes el remember se
    // recomputaba con el id nuevo y el juego "se trababa unos segundos" al transformarse).
    val fightIds = remember(playerId, cpuId) {
        buildSet {
            add(playerId)
            add(cpuId)
            if (playerId == SfFighterId.LA_PRESIDENTA || cpuId == SfFighterId.LA_PRESIDENTA) {
                add(SfFighterId.YOALLI_EHECATL)
            }
            if (playerId == SfFighterId.YOALLI_EHECATL || cpuId == SfFighterId.YOALLI_EHECATL) {
                add(SfFighterId.LA_PRESIDENTA)
            }
        }
    }
    // 🆕 (2026-07-22, Bloque B) TODO lo PESADO de la pelea (atlas de peleadores a
    // `sheetSample`, atlas ALPHA y el escaneo de alturas de contenido) se decodifica en
    // Dispatchers.IO bajo el overlay CARGANDO. Antes iba en remember{} EN EL HILO DE UI
    // (el "se traba unos segundos al cargar") y sheetFor podía lanzar error()/OOM SIN
    // atrapar (crash P0 de La Llorona: su pelea es la ÚNICA que suma un 3er atlas ALPHA).
    var fightAssets by remember { mutableStateOf<SfFightAssets?>(null) }
    LaunchedEffect(fightIds, state.inCharacterSelect, sheetSample, lowEnd) {
        if (state.inCharacterSelect) {
            fightAssets = null
            return@LaunchedEffect
        }
        fightAssets = null
        fightAssets = withContext(kotlinx.coroutines.Dispatchers.Default) {
            // BLINDAJE P0: un atlas que no decodifica (OOM/IO) NO tumba el juego — se
            // omite y drawFighter cae a la primera hoja disponible (feo pero jugable).
            val sheets = buildMap {
                fightIds.forEach { id ->
                    runCatching { SfSharedSheets.sheetFor(id, sheetSample) }
                        .getOrNull()
                        ?.let { put(id.spriteAsset.substringAfterLast('/'), it) }
                }
            }
            // PLACEHOLDER ALPHA (2026-07-22: La Llorona YA trae PATADA LARGA/OVERHEAD propios, así
            // que hoy NINGÚN peleador dispara este 3er atlas; la red se queda por si acaso). Se pinta
            // como SILUETA NEGRA → media resolución NO se nota y evitaba el pico de RAM del 3er atlas
            // (~63 → ~16 MB en gama alta, ~4 MB en baja), que era el sospechoso del crash por OOM.
            // Lleva SU escala en sheetScale.
            val needyId = fightIds.firstOrNull { id ->
                val d = SfFrameCatalog.load(id)
                SF_NEW_MOVE_STATES.any { d.animations[it.jsKey].isNullOrEmpty() }
            }
            val alpha = needyId?.let { needy ->
                val fallbackId = controller.alphaFallbackId(needy)
                val alphaSample = if (sheetSample > 1) sheetSample * 2 else 2
                runCatching {
                    AlphaFallback(
                        data = SfFrameCatalog.load(fallbackId),
                        sheetKey = fallbackId.spriteAsset.substringAfterLast('/'),
                        bitmap = decodificarReducido(
                            PowAssets.bytes(fallbackId.spriteAsset),
                            alphaSample,
                        ),
                        sheetScale = 1f / alphaSample,
                    )
                }.getOrNull()?.takeIf { it.bitmap != null }
            }
            // Alturas de CONTENIDO opaco POR IDENTIDAD: en GAMA BAJA se OMITEN (scan caro);
            // en media/alta se miden AQUÍ (IO), ya no en el hilo de UI.
            val contentH = if (lowEnd) {
                emptyMap()
            } else {
                fightIds.associateWith { id ->
                    val sheet = sheets[id.spriteAsset.substringAfterLast('/')]
                    if (sheet != null) {
                        measureFrameContentHeights(sheet, SfFrameCatalog.load(id).frames)
                    } else {
                        emptyMap()
                    }
                }
            }
            SfFightAssets(sheets, alpha, contentH)
        }
    }
    val images = remember(themeImages, fightAssets) {
        themeImages + (fightAssets?.sheets ?: emptyMap())
    }
    val alphaFallback = fightAssets?.alpha
    val playerContentH = fightAssets?.contentH?.get(playerId) ?: emptyMap()
    val cpuContentH = fightAssets?.contentH?.get(cpuId) ?: emptyMap()

    // ---- Selección offline en 4 pasos: PELEADOR → RIVAL → DIFICULTAD → MAPA ----
    var pendingFighter by remember { mutableStateOf<SfFighterId?>(null) }
    var pendingRival by remember { mutableStateOf<SfFighterId?>(null) } // 🆕 rival elegible offline
    var pendingDifficulty by remember { mutableStateOf<SfCpuDifficulty?>(null) } // 🆕 dificultad CPU
    var chosenBgFile by remember { mutableStateOf(theme.fullBackgrounds.firstOrNull()?.file) }
    LaunchedEffect(state.inCharacterSelect) {
        if (state.inCharacterSelect) { pendingFighter = null; pendingRival = null; pendingDifficulty = null }
    }
    // Fondo del combate: AUTOJUEGO/SHOWCASE manda el hogar del peleadór en turno; ARCADE su
    // mapa (ligado al rival); ONLINE el del ANFITRIÓN; si no, el elegido offline en el selector.
    val effectiveBgFile = when {
        state.gauntletRunning && state.gauntletMapFile != null -> state.gauntletMapFile
        (state.arcadeActive || state.aiVsAi) && state.arcadeMapFile != null -> state.arcadeMapFile
        state.onlineStatus != SfOnlineStatus.OFF && state.onlineMapFile != null -> state.onlineMapFile
        else -> chosenBgFile
    }
    // Fondo del combate: ANIMADO (atlas) o estático. RGB_565; en gama baja inSampleSize=2.
    // Carga en IO + overlay CARGANDO (evita freeze de UI al decodificar ~6 MB de atlas).
    var assetsLoading by remember { mutableStateOf(false) }
    var stageBg by remember { mutableStateOf<SfStageBackground?>(null) }
    LaunchedEffect(effectiveBgFile, state.inCharacterSelect, lowEnd) {
        if (state.inCharacterSelect) {
            stageBg = null
            assetsLoading = false
            return@LaunchedEffect
        }
        assetsLoading = true
        stageBg = withContext(kotlinx.coroutines.Dispatchers.Default) {
            effectiveBgFile?.let {
                loadStageBackground(theme.imagesDir, it, lowEnd = lowEnd)
            }
        }
        assetsLoading = false
    }

    // ---- Sonidos del tema (🍏 Fase 5: efectos y música por `PowAudio`, igual en Android e iOS) ----
    // En Android `cargarEfecto` sigue siendo SoundPool por debajo (baja latencia para los golpes) y
    // `cargarPista` sigue siendo MediaPlayer. La distinción se mantiene A PROPÓSITO: fusionarlas
    // obligaría a elegir un solo motor para todo y la app de Android sonaría peor.
    //
    // Base theme SFX + special_<fighter> por los peleadores del match (y Yoalli si hay metamorfosis).
    // Faltantes se omiten; el collect cae a "hadouken" si no hay special del id.
    val soundClips = remember(theme) {
        theme.soundKeys.distinct().mapNotNull { key ->
            PowAudio.cargarEfecto("${theme.soundsDir}$key.m4a")?.let { key to it }
        }.toMap()
    }
    val activeSpecialPlayers = remember { mutableMapOf<String, PowClip>() }
    LaunchedEffect(soundClips) {
        controller.soundEvents.collect { key ->
            if (key == SF_STOP_SPECIALS_EVENT) {
                releaseSfSpecials(activeSpecialPlayers)
            } else if (key.startsWith("special_")) {
                val played = playSfSpecial(
                    assetPath = "${theme.soundsDir}$key.m4a",
                    activePlayers = activeSpecialPlayers,
                )
                // Si el especial no sonó (no existe el clip, o las reglas de voz lo saltaron), cae
                // al "hadouken" genérico para que el ataque no quede mudo.
                if (!played) soundClips["hadouken"]?.reproducir()
            } else {
                // El mapa `activeStreams` de antes YA NO HACE FALTA: `reproducir()` corta por dentro
                // el flujo previo del mismo clip (ver `EfectoSoundPool`), que es exactamente lo que
                // hacía aquí el `soundPool.stop(lastStream)` a mano.
                (soundClips[key] ?: soundClips["hadouken"])?.reproducir()
            }
        }
    }
    // 🆕 (2026-07-18) MÚSICA POR PROGRESIÓN: lobby en el selector; en pelea, la pista escala con
    // el NIVEL (arcade = avance de la escalera; práctica = dificultad de la CPU; IA vs IA = la más
    // dura). Al cambiar de pista (entrar a pelea, subir de escalón…) el MediaPlayer se recarga.
    val musicFileForState = remember(
        state.inCharacterSelect, state.aiVsAi, state.arcadeActive,
        state.arcadeStep, state.arcadeTotal, state.cpuDifficulty,
    ) {
        val battle = theme.battleMusic
        when {
            theme.lobbyMusic.isBlank() && battle.isEmpty() -> theme.musicFile // legado: pista única
            state.inCharacterSelect -> theme.lobbyMusic.ifBlank { theme.musicFile }
            battle.isEmpty() -> theme.lobbyMusic.ifBlank { theme.musicFile }
            else -> {
                val n = battle.size
                val frac = when {
                    state.aiVsAi -> 1f
                    state.arcadeActive && state.arcadeTotal > 1 ->
                        (state.arcadeStep - 1).toFloat() / (state.arcadeTotal - 1)
                    else -> when (state.cpuDifficulty) {
                        SfCpuDifficulty.BASICA -> 0f
                        SfCpuDifficulty.NORMAL -> 0.34f
                        SfCpuDifficulty.AVANZADA -> 0.67f
                        SfCpuDifficulty.PESADILLA -> 1f
                    }
                }
                // índice = round(frac*(n-1)) sin roundToInt (aritmética entera)
                battle[((frac * (n - 1)) + 0.5f).toInt().coerceIn(0, n - 1)]
            }
        }
    }
    // (Re)carga y arranca la pista cuando cambia la selección (lobby ⇄ batalla / nivel).
    //
    // ⚠️ Antes era UN solo `MediaPlayer` reutilizado con `reset()`; ahora se crea un clip por pista
    // y **el anterior se libera en el `onDispose`**. Si no se liberase, cada cambio de escalón en
    // el arcade dejaría vivo el reproductor viejo y las pistas se encimarían.
    val musicPlayer = remember { mutableStateOf<PowClip?>(null) }
    DisposableEffect(musicFileForState) {
        val clip = PowAudio.cargarPista(theme.soundsDir + musicFileForState)
        musicPlayer.value = clip
        clip?.reproducir(volumen = theme.musicVolume, bucle = true)
        onDispose {
            musicPlayer.value = null
            runCatching { clip?.detener() }
            runCatching { clip?.liberar() }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            releaseSfSpecials(activeSpecialPlayers)
            // La música la libera su propio efecto (arriba). Aquí van los efectos cortos.
            soundClips.values.forEach { runCatching { it.liberar() } }
        }
    }

    // PAUSA AUTOMÁTICA al bloquear el celular / minimizar: pausa + guarda sesión arcade
    // (forcePause → putString async, sin lag). Al volver, música reanuda; pelea sigue en PAUSA.
    SfLifecycleEffect(
        onPause = {
            controller.forcePause()
            runCatching { musicPlayer.value?.detener() }
            releaseSfSpecials(activeSpecialPlayers)
        },
        onResume = {
            // `detener()` rebobina, así que al volver la pista arranca desde el principio.
            runCatching { musicPlayer.value?.reproducir(theme.musicVolume, bucle = true) }
        },
    )

    // 🆕 Retomar pelea arcade a medias (si saliste / minimizaste)
    var showResumeDialog by remember { mutableStateOf(controller.hasArcadeSession()) }

    // Textos del banner de RONDA (i18n; la fuente arcade solo tiene A-Z/0-9)
    val roundBannerText = stringResource(Res.string.sf_round_banner, state.roundNumber)
    val fightBannerText = stringResource(Res.string.sf_fight_banner)
    // 🆕 (2026-07-20) Etiqueta del contador de COMBO ("GOLPES"/"HITS")
    val comboHitsLabel = stringResource(Res.string.sf_combo_hits)
    // 🆕 (2026-07-21) ¿El peleador elegido tiene el moveset nuevo? (botones extra)
    val hasNewMoves = remember(state.player.id) { controller.playerHasNewMoves() }
    // 🆕 Ajustes → "Mostrar hitboxes" (se lee al entrar al modo)
    val showHitboxes = remember { controller.showHitboxes() }
    // 🆕 (2026-07-25) Ajustes → "Mostrar FPS (pelea)" (contador de cuadros por segundo)
    val showSfFps = remember { controller.showSfFps() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // ---- Escena completa (mundo + HUD) en un Canvas ----
        // En selector no hace falta el Canvas de pelea (ahorra GPU en gama baja).
        // 🆕 (2026-07-22) Y tampoco se dibuja hasta tener los atlas de peleador (fightAssets):
        // sin ellos drawFighter caería a la hoja del HUD (basura visual bajo el overlay).
        if (!state.inCharacterSelect && fightAssets != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawScene(
                    theme, state, images, playerData, cpuData, stageBg,
                    roundBannerText, fightBannerText, comboHitsLabel, showHitboxes,
                    playerContentH, cpuContentH, effectiveBgFile,
                    playerSilhouette = !controller.isFighterActuallyUnlocked(state.player.id),
                    alphaFallback = alphaFallback,
                    sheetScale = sheetSampleScale,
                )
            }
        }
        // 🆕 Pantalla CARGANDO (fuente POW del HUD) mientras se decodifican atlas/hojas
        // (fondo Y ahora también los atlas de peleador, ver fightAssets arriba).
        val fightLoading = !state.inCharacterSelect &&
            (assetsLoading || fightAssets == null || stageBg == null && effectiveBgFile != null)
        // 🆕 (2026-07-22) Avisa al VM para CONGELAR el reloj de juego mientras carga: así el
        // 3-2-1 y el arranque se ven al terminar (antes se gastaban ocultos tras el CARGANDO,
        // sobre todo en gama baja e IA vs IA que decodifica dos atlas).
        LaunchedEffect(fightLoading) { controller.setAssetsLoadingUi(fightLoading) }
        // 🆕 (2026-07-25) Contador de FPS del modo pelea (Ajustes → Interfaz). Mide los cuadros
        // REALES de pantalla (withFrameNanos), no el tick del VM. Solo durante la pelea.
        if (showSfFps && !state.inCharacterSelect && !fightLoading) {
            SfFpsOverlay(modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 6.dp))
        }
        if (fightLoading) {
            SfLoadingOverlay(theme = theme)
        } else if (state.waitingForOpponentReady) {
            // 🆕 (2026-07-25) BARRERA "AMBOS LISTOS": ya cargué mis atlas; espero a que el rival
            // termine de cargar (PLAYER_READY) para arrancar la ronda sincronizada (punto 2).
            SfLoadingOverlay(
                theme = theme,
                arcadeText = "ESPERANDO",
                fallbackText = stringResource(Res.string.sf_waiting_opponent),
                subtitle = stringResource(Res.string.sf_waiting_opponent_sub),
            )
        }

        // ---- Controles de POW: joystick + diamante Xbox (ocultos en selección y en IA vs IA) ----
        // IA vs IA: ambos los controla la CPU → solo botón "Salir" al menú (sin joystick/botones).
        if (!state.inCharacterSelect && !state.aiVsAi) {
            // 🆕 (2026-07-22) TUTORIAL: botón físico que pide el PASO ACTUAL (para el glow) y,
            // si el paso lleva JOYSTICK, la dirección/gesto a marcar (para explicar el combo).
            val tutorialStepLabel = if (state.tutorialActive && !state.tutorialCompleted) {
                state.tutorialSteps.getOrNull(state.tutorialStepIndex)
            } else {
                null
            }
            val tutorialButton = tutorialStepLabel?.let(::sfButtonForLabel)
            val tutorialJoystick = tutorialStepLabel?.let(::sfJoystickHintForLabel)
            JoystickController(
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                onRelease = controller::onJoystickRelease,
                // 🆕 (2026-07-25) Respuesta INMEDIATA al toque: en la pelea los controles deben
                // responder al instante (agacharse/caminar). Con el arrastre de siempre un tap se
                // perdía y tocar-y-mantener no registraba hasta cruzar el touch-slop.
                respondToTouchDown = true,
                onMove = controller::onJoystickMove,
            )
            // 🆕 (2026-07-22) Guía de JOYSTICK del tutorial: gesto/dirección a marcar (↓, →, dash,
            // hadouken…), pulsando ENCIMA del joystick para que se sepa qué mover (p.ej. la Barrida
            // = ↓ + patada: antes solo brillaba el botón y el ↓ no se explicaba).
            if (tutorialJoystick != null) {
                SfJoystickHint(
                    text = tutorialJoystick,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 30.dp, bottom = 150.dp),
                )
            }
            // OJO: padding-end grande a propósito — en landscape la barra de navegación/gestos
            // del sistema vive en el borde DERECHO y se comía los toques del botón B (los combos
            // "no salían" porque esos taps nunca llegaban a la app). Separado del borde, todos
            // los toques caen dentro del juego.
            FighterXboxButtons(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 64.dp, bottom = 16.dp),
                onPunch = { strength -> controller.onAttackPressed(strength, SfAttackType.PUNCH) },
                onKick = controller::onKickPressed,
                bonusPowerCount = state.player.id.bonusPowerCount,
                onBonusPower = controller::onBonusPowerPressed,
                highlight = tutorialButton,
            )
            // 🆕 (2026-07-21) Botones del moveset 3rd Strike. Solo se muestran si el
            // peleador TIENE ese arte (los compartidos/ALPHA no los tienen).
            if (hasNewMoves) {
                // 🆕 (2026-07-22) Rediseño "Neón Arcade": gatillos L (izquierda, encima del
                // joystick) = L1 Parry · L2 Burla.
                FighterShoulderButtons(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 185.dp),
                    isLeft = true,
                    superReady = state.player.superReady,
                    onParry = controller::onParryPressed,
                    onGrab = controller::onGrabPressed,
                    onTaunt = controller::onTauntPressed,
                    onSuper = controller::onSuperArtPressed,
                    highlight = tutorialButton,
                )
                // Gatillos R (derecha, encima del diamante) = R1 Agarre · R2 Súper.
                FighterShoulderButtons(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 190.dp),
                    isLeft = false,
                    superReady = state.player.superReady,
                    onParry = controller::onParryPressed,
                    onGrab = controller::onGrabPressed,
                    onTaunt = controller::onTauntPressed,
                    onSuper = controller::onSuperArtPressed,
                    highlight = tutorialButton,
                )
            }
            // 🆕 (2026-07-21) TUTORIAL: HUD guía encima de la pelea (el jugador usa los
            // controles normales; el rival es un muñeco inerte).
            if (state.tutorialActive) {
                SfTutorialOverlay(
                    lesson = state.tutorialLesson,
                    total = state.tutorialTotal,
                    title = state.tutorialTitle,
                    hint = state.tutorialHint,
                    steps = state.tutorialSteps,
                    stepIndex = state.tutorialStepIndex,
                    flash = state.tutorialFlash,
                    error = state.tutorialError,
                    completed = state.tutorialCompleted,
                    onSkip = controller::tutorialSkipLesson,
                    onRestart = controller::tutorialRestartLesson,
                    // `exitTutorial` devuelve el estado a selección de personaje; el
                    // LaunchedEffect(inCharacterSelect) reabre el flujo normal del modo.
                    onExit = controller::exitTutorial,
                )
            }
            // 🆕 (2026-07-22) El hint de controles al fondo se QUITÓ (pedido del dueño): estorbaba
            // y además citaba los botones viejos (P/G/S). Los controles se aprenden en la hoja de
            // combos + el tutorial; los botones ya se rotularon L1/L2/R1/R2.
        }
        // IA vs IA: botón "Salir" al menú de modos (sin controles táctiles de pelea).
        // Durante el AUTOJUEGO se oculta: ahí manda el botón DETENER de abajo.
        if (!state.inCharacterSelect && state.aiVsAi && !state.showEndMenu && !state.gauntletRunning) {
            PowButton(
                text = stringResource(Res.string.sf_exit),
                onClick = controller::backToCharacterSelect,
                color = Color(0xFF8B1538),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .fillMaxWidth(0.36f),
            )
        }

        // 🆕 AUTOJUEGO (gauntlet): progreso + DETENER mientras corre; reporte de assets al terminar.
        if (state.gauntletRunning && !state.gauntletFinished) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "AUTOJUEGO ${state.gauntletProgress}",
                    color = Color(0xFFFFD54A),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                PowButton(
                    text = stringResource(Res.string.sf_gauntlet_stop),
                    onClick = controller::stopGauntlet,
                    color = Color(0xFF8B1538),
                    modifier = Modifier.fillMaxWidth(0.32f),
                )
                // SALTAR (solo showcase): termina el peleador actual y avanza sin esperar el timer.
                if (state.showcaseRunning) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PowButton(
                            text = stringResource(Res.string.sf_showcase_prev_animation),
                            onClick = controller::goToPreviousShowcaseAnimation,
                            color = Color(0xFF1B5E20),
                            modifier = Modifier.width(156.dp),
                        )
                        PowButton(
                            text = stringResource(Res.string.sf_showcase_next_animation),
                            onClick = controller::skipToNextShowcaseAnimation,
                            color = Color(0xFF1B5E20),
                            modifier = Modifier.width(156.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PowButton(
                            text = stringResource(Res.string.sf_showcase_next_fighter),
                            onClick = controller::skipShowcaseFighter,
                            color = Color(0xFF7B3F00),
                            modifier = Modifier.width(156.dp),
                        )
                        PowButton(
                            text = stringResource(
                                Res.string.sf_showcase_speed,
                                "${state.showcaseSpeed.toInt()}x",
                            ),
                            onClick = controller::cycleShowcaseSpeed,
                            color = Color(0xFF1565C0),
                            modifier = Modifier.width(156.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PowButton(
                            text = stringResource(Res.string.sf_showcase_replay_audio),
                            onClick = controller::replayCurrentShowcaseAudio,
                            color = Color(0xFF6A1B9A),
                            modifier = Modifier.width(156.dp),
                        )
                    }
                } else {
                    // 🆕 Para el Autojuego todos contra todos y Auditoría de campañas, mostramos el control de velocidad
                    Spacer(modifier = Modifier.height(6.dp))
                    PowButton(
                        text = stringResource(
                            Res.string.sf_showcase_speed,
                            "${state.showcaseSpeed.toInt()}x",
                        ),
                        onClick = controller::cycleShowcaseSpeed,
                        color = Color(0xFF1565C0),
                        modifier = Modifier.width(156.dp),
                    )
                }
            }
        }
        if (state.gauntletFinished) {
            GauntletReportOverlay(
                progress = state.gauntletProgress,
                report = state.gauntletReport,
                path = state.gauntletReportPath,
                onClose = controller::dismissGauntletReport,
            )
        }

        // ---- Selección pre-pelea: paso 1 PELEADOR, paso 2 MAPA (offline y online) ----
        var showOnlineMenu by remember { mutableStateOf(false) }
        // 🆕 ARCADE es el modo POR DEFECTO al entrar (se ven los personajes, más llamativo).
        var arcadeSetup by remember { mutableStateOf(true) }
        // Menú de MODOS (PRÁCTICA / IA VS IA / MULTIJUGADOR) = SECUNDARIO, se abre con "Otros modos".
        var sfMenu by remember { mutableStateOf(false) }
        // 🆕 Flujo IA vs IA: elige peleador A → peleaador B → startAiVsAi (PESADILLA, sin mapa).
        var aiVsAiSetup by remember { mutableStateOf(false) }
        // 🆕 (2026-07-21) HOJA DE COMBOS: lista de controles y combos + "PROBAR" (tutorial).
        var showComboSheet by remember { mutableStateOf(false) }
        var comboFighter by remember { mutableStateOf<SfFighterId?>(null) }
        // 🆕 (2026-07-22) MEMORIA DEL MODO: al salir de una pelea se vuelve al selector DEL
        // MISMO modo (antes SIEMPRE forzaba el selector de Arcade). Se registra al LANZAR
        // cada modo; "menu" = gauntlet/showcase (no tienen selector propio) → menú de modos.
        var lastLaunchedMode by remember { mutableStateOf("arcade") }
        LaunchedEffect(state.inCharacterSelect) {
            if (state.inCharacterSelect) {
                arcadeSetup = false
                sfMenu = false
                aiVsAiSetup = false
                when (lastLaunchedMode) {
                    "arcade" -> arcadeSetup = true
                    "aivsai" -> aiVsAiSetup = true
                    "practice" -> Unit // el selector de práctica es la rama default
                    "combos" -> showComboSheet = true // vuelve a la hoja del peleador probado
                    else -> sfMenu = true
                }
            }
        }
        if (state.inCharacterSelect) {
            if (state.onlineStatus != SfOnlineStatus.OFF) {
                onlineStatusContent(state, theme, lowEnd, controller)
            } else when (state.onlineStatus) {
                /* Android: vive en SfOnlineOverlays.kt y entra por onlineStatusContent.
                SfOnlineStatus.CONNECTING -> OnlineInfoOverlay(
                    title = stringResource(Res.string.sf_mp_connecting_title),
                    // BT/LAN en 2 etapas para que se ENTIENDA qué pasa: conectando → verificando
                    subtitle = stringResource(
                        when {
                            (state.btMode || state.lanMode) && state.btHandshaking -> Res.string.sf_bt_handshake_sub
                            state.lanMode -> Res.string.sf_lan_connecting_sub
                            state.btMode -> Res.string.sf_bt_connecting_sub
                            // 🆕 (2026-07-26) El aviso de "hasta un minuto" SOLO si el servidor
                            // estaba dormido de verdad. Antes salía siempre y asustaba de gratis:
                            // con el servicio despierto la conexión es inmediata.
                            state.onlineWaking -> Res.string.sf_mp_connecting_sub
                            else -> Res.string.sf_mp_connecting_fast
                        },
                    ),
                    onCancel = { controller.cancelOnline() },
                )
                SfOnlineStatus.WAITING_OPPONENT -> if (state.lanMode) {
                    // SERVIDOR LOCAL: mostrar TODAS las IPs a compartir (misma red Wi-Fi/hotspot);
                    // el rival prueba la alcanzable si el host tiene varias interfaces.
                    val ips = state.lanLocalIps.ifEmpty { listOfNotNull(state.lanLocalIp) }
                    OnlineInfoOverlay(
                        title = stringResource(Res.string.sf_lan_host_title),
                        subtitle = if (ips.isNotEmpty()) {
                            stringResource(Res.string.sf_lan_host_sub, ips.joinToString("  •  "))
                        } else {
                            stringResource(Res.string.sf_lan_no_ip)
                        },
                        onCancel = { controller.cancelOnline() },
                    )
                } else if (state.btMode) {
                    // ANFITRIÓN Bluetooth: visible + esperando que el rival conecte
                    OnlineInfoOverlay(
                        title = stringResource(Res.string.sf_bt_host_title),
                        subtitle = stringResource(Res.string.sf_bt_host_sub),
                        onCancel = { controller.cancelOnline() },
                    )
                } else if (state.roomCode != null) {
                    OnlineInfoOverlay(
                        title = stringResource(Res.string.sf_mp_room, state.roomCode!!),
                        subtitle = stringResource(Res.string.sf_mp_waiting_sub),
                        onCancel = { controller.cancelOnline() },
                    )
                } else {
                    // SALA PÚBLICA (lista de espera): resumen + salas activas como tarjetas;
                    // tocar una en 'waiting' SOLICITA unirse (el anfitrión decide, estilo AoE2)
                    PublicQueueOverlay(
                        rooms = state.activeRooms,
                        queueCount = state.queueCount,
                        awaitingHost = state.awaitingJoinOk,
                        notice = state.queueNotice,
                        onJoinRoom = controller::requestJoinRoom,
                        onCancel = { controller.cancelOnline() },
                    )
                }
                SfOnlineStatus.SELECTING -> CharacterSelectOverlay(
                    fighters = controller.selectableFighters(),
                    lockedFighters = controller.lockedFighters(),
                    subtitle = stringResource(Res.string.sf_mp_pick_sub, state.roomCode ?: ""),
                    onSelect = controller::selectCharacter,
                    lowEnd = lowEnd,
                    isActuallyUnlocked = { controller.isFighterActuallyUnlocked(it) },
                )
                SfOnlineStatus.WAITING_MAP -> if (state.isHost) {
                    StageSelectOverlay(
                        theme = theme,
                        unlockedMaps = if (controller.devUnlockAll()) null else controller.unlockedMaps(),
                        onSelect = controller::chooseMapOnline,
                        onBack = null,
                        lowEnd = lowEnd,
                    )
                } else {
                    OnlineInfoOverlay(
                        title = stringResource(Res.string.sf_mp_room, state.roomCode ?: ""),
                        subtitle = stringResource(Res.string.sf_mp_host_choosing_map),
                        onCancel = { controller.cancelOnline() },
                    )
                }
                SfOnlineStatus.COUNTDOWN -> Unit // el número gigante se dibuja abajo
                */
                else -> {
                    val fighter = pendingFighter
                    val rival = pendingRival
                    val difficulty = pendingDifficulty
                    when {
                        // 🆕 (2026-07-21) HOJA DE COMBOS + TUTORIAL. Si aún no hay peleador
                        // elegido, primero se elige de entre los DESBLOQUEADOS.
                        showComboSheet && comboFighter == null -> CharacterSelectOverlay(
                            fighters = controller.selectableFighters(),
                            subtitle = stringResource(Res.string.sf_combos_pick_fighter),
                            lockedFighters = controller.lockedFighters(),
                            isActuallyUnlocked = { controller.isFighterActuallyUnlocked(it) },
                            lowEnd = lowEnd,
                            onSelect = { comboFighter = it },
                            onBack = { showComboSheet = false; sfMenu = true },
                        )
                        showComboSheet -> SfComboSheetOverlay(
                            fighterId = comboFighter!!,
                            combos = remember(comboFighter) { controller.comboSheet(comboFighter!!) },
                            onTry = {
                                showComboSheet = false
                                lastLaunchedMode = "combos" // 🆕 al salir del tutorial: la hoja
                                controller.startTutorial(comboFighter!!)
                            },
                            onChangeFighter = { comboFighter = null },
                            onBack = {
                                showComboSheet = false
                                comboFighter = null
                                sfMenu = true
                            },
                        )
                        // 🆕 MENÚ DE MODOS (estilo POW): ARCADE principal, PRÁCTICA, IA VS IA, MULTIJUGADOR
                        sfMenu -> SfModeMenuOverlay(
                            devMode = controller.devUnlockAll(),
                            audioShowcaseRunning = state.audioShowcaseRunning,
                            audioShowcaseIndex = state.audioShowcaseIndex,
                            audioShowcaseTotal = state.audioShowcaseTotal,
                            audioShowcaseFighter = state.audioShowcaseFighter,
                            audioShowcasePhrase = state.audioShowcasePhrase,
                            onArcade = {
                                controller.stopAudioShowcase()
                                sfMenu = false
                                arcadeSetup = true
                                aiVsAiSetup = false
                                pendingFighter = null
                                pendingRival = null
                                pendingDifficulty = null
                            },
                            onPractice = {
                                controller.stopAudioShowcase()
                                sfMenu = false
                                arcadeSetup = false
                                aiVsAiSetup = false
                                pendingFighter = null
                                pendingRival = null
                                pendingDifficulty = null
                            },
                            onAiVsAi = {
                                controller.stopAudioShowcase()
                                sfMenu = false
                                arcadeSetup = false
                                aiVsAiSetup = true
                                pendingFighter = null
                                pendingRival = null
                                pendingDifficulty = null
                            },
                            onCombos = {
                                controller.stopAudioShowcase()
                                showComboSheet = true
                            },
                            onMultiplayer = {
                                controller.stopAudioShowcase()
                                showOnlineMenu = true
                            },
                            onGauntletAll = {
                                controller.stopAudioShowcase()
                                sfMenu = false
                                lastLaunchedMode = "menu" // 🆕 sin selector propio → menú
                                controller.startGauntletRoundRobin()
                            },
                            onGauntletArcade = {
                                controller.stopAudioShowcase()
                                sfMenu = false
                                lastLaunchedMode = "menu"
                                controller.startGauntletArcade()
                            },
                            onGauntletShowcase = {
                                controller.stopAudioShowcase()
                                sfMenu = false
                                lastLaunchedMode = "menu"
                                controller.startShowcase()
                            },
                            onAudioShowcaseStop = controller::stopAudioShowcase,
                            onBack = onExitToMap,
                        )
                        // ARCADE: peleadór → Fácil/Medio/Difícil (día / noche / apocalipsis)
                        arcadeSetup && fighter == null -> CharacterSelectOverlay(
                            fighters = controller.selectableFighters(),
                            lockedFighters = controller.lockedFighters(),
                            subtitle = stringResource(Res.string.sf_arcade_pick_you),
                            onSelect = { pendingFighter = it },
                            onBack = { arcadeSetup = false; sfMenu = true },
                            backText = stringResource(Res.string.sf_other_modes),
                            lowEnd = lowEnd,
                            isActuallyUnlocked = { controller.isFighterActuallyUnlocked(it) },
                        )
                        arcadeSetup && difficulty == null -> ArcadeDifficultyOverlay(
                            onSelect = { d ->
                                val p = fighter ?: return@ArcadeDifficultyOverlay
                                lastLaunchedMode = "arcade" // 🆕 volver = selector de Arcade
                                controller.startArcade(p, d)
                                pendingFighter = null
                                pendingDifficulty = null
                                arcadeSetup = false
                            },
                            onBack = { pendingFighter = null },
                        )
                        // 🆕 IA VS IA: dos peleadores (CPU vs CPU a PESADILLA) → startAiVsAi
                        // Roster = selectableFighters() (completo si Modo Desarrollador activo).
                        aiVsAiSetup && fighter == null -> CharacterSelectOverlay(
                            fighters = controller.selectableFighters(),
                            lockedFighters = controller.lockedFighters(),
                            subtitle = stringResource(Res.string.sf_ai_vs_ai_pick_a),
                            onSelect = { pendingFighter = it },
                            onBack = { aiVsAiSetup = false; sfMenu = true },
                            lowEnd = lowEnd,
                            isActuallyUnlocked = { controller.isFighterActuallyUnlocked(it) },
                        )
                        aiVsAiSetup && rival == null -> CharacterSelectOverlay(
                            fighters = controller.selectableFighters(),
                            lockedFighters = controller.lockedFighters(),
                            subtitle = stringResource(Res.string.sf_ai_vs_ai_pick_b),
                            allyId = fighter,        // 🆕 flecha AZUL sobre el P1 elegido
                            showPickArrow = true,    // 🆕 flecha ROJA sobre el P2 resaltado
                            onSelect = { b ->
                                val a = fighter!!
                                // Fondo al azar entre mapas DESBLOQUEADOS (o todos en Modo Dev)
                                if (chosenBgFile == null) {
                                    val unlocked = controller.unlockedMaps()
                                    val pool = if (controller.devUnlockAll()) {
                                        theme.fullBackgrounds.map { it.file }
                                    } else {
                                        theme.fullBackgrounds.map { it.file }.filter { it in unlocked }
                                    }
                                    chosenBgFile = pool.randomOrNull()
                                        ?: theme.fullBackgrounds.firstOrNull()?.file
                                }
                                lastLaunchedMode = "aivsai" // 🆕 volver = selector de IA vs IA
                                controller.startAiVsAi(a, b)
                                pendingFighter = null
                                pendingRival = null
                                aiVsAiSetup = false
                            },
                            onBack = { pendingFighter = null },
                            lowEnd = lowEnd,
                            isActuallyUnlocked = { controller.isFighterActuallyUnlocked(it) },
                        )
                        // PRÁCTICA (versus): peleador → RIVAL → DIFICULTAD → mapa
                        fighter == null -> CharacterSelectOverlay(
                            fighters = controller.selectableFighters(),
                            lockedFighters = controller.lockedFighters(),
                            subtitle = state.onlineError,
                            onSelect = { pendingFighter = it },
                            onBack = { sfMenu = true },
                            lowEnd = lowEnd,
                            isActuallyUnlocked = { controller.isFighterActuallyUnlocked(it) },
                        )
                        rival == null -> CharacterSelectOverlay(
                            fighters = controller.selectableFighters(),
                            lockedFighters = controller.lockedFighters(),
                            subtitle = stringResource(Res.string.sf_choose_rival),
                            allyId = fighter,        // 🆕 flecha AZUL sobre el P1 elegido
                            showPickArrow = true,    // 🆕 flecha ROJA sobre el P2 resaltado
                            onSelect = { pendingRival = it },
                            onBack = { pendingFighter = null },
                            lowEnd = lowEnd,
                            isActuallyUnlocked = { controller.isFighterActuallyUnlocked(it) },
                        )
                        difficulty == null -> DifficultySelectOverlay(
                            onSelect = { pendingDifficulty = it },
                            onBack = { pendingFighter = null; pendingRival = null },
                        )
                        else -> StageSelectOverlay(
                            theme = theme,
                            unlockedMaps = if (controller.devUnlockAll()) null else controller.unlockedMaps(),
                            onSelect = { file ->
                                val unlocked = controller.unlockedMaps()
                                val pool = if (controller.devUnlockAll()) {
                                    theme.fullBackgrounds.map { it.file }
                                } else {
                                    theme.fullBackgrounds.map { it.file }.filter { it in unlocked }
                                }
                                chosenBgFile = file
                                    ?: pool.randomOrNull()
                                    ?: theme.fullBackgrounds.firstOrNull()?.file
                                lastLaunchedMode = "practice" // 🆕 volver = selector de práctica
                                controller.selectCharacter(fighter, rival, difficulty)
                            },
                            onBack = { pendingFighter = null; pendingRival = null; pendingDifficulty = null },
                            lowEnd = lowEnd,
                        )
                    }
                }
            }
        }

        onlinePlatformOverlays(state, showOnlineMenu, { showOnlineMenu = it }, controller)

        /* Android: permisos/menús BT-LAN viven en SfOnlineOverlays.kt.
        // ---- Permisos BT runtime (solo Android 12+; en ≤11 son permisos normales y el
        // discovery usa la ubicación que la app YA tiene por los mapas — no se pide nada) ----
        // CADENA COMPLETA de "listo para BT": permisos → BT ENCENDIDO → acción. Si el BT está
        // apagado SIEMPRE se pide encenderlo (diálogo del sistema); si el jugador lo niega, el
        // SIGUIENTE intento (tocar de nuevo / REINTENTAR) lo vuelve a pedir — igual los permisos.
        var pendingBtAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        val btEnableLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val act = pendingBtAction
            pendingBtAction = null
            if (result.resultCode == Activity.RESULT_OK) act?.invoke() // BT encendido → sigue
            // denegado: no hacemos nada; el próximo toque vuelve a pedirlo
        }
        val whenBtEnabled: (() -> Unit) -> Unit = { action ->
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            when {
                adapter == null -> Unit // hardware sin Bluetooth: no hay nada que encender
                adapter.isEnabled -> action()
                else -> {
                    pendingBtAction = action
                    runCatching { btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                }
            }
        }
        val btPermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            // OJO: capturar y limpiar ANTES de invocar — la acción puede re-encolar
            // pendingBtAction (paso "encender BT") y un null posterior lo rompería
            val act = pendingBtAction
            pendingBtAction = null
            if (grants.values.all { it }) act?.invoke()
        }
        val withBtPerms: (Array<String>, () -> Unit) -> Unit = { perms, action ->
            val granted = Build.VERSION.SDK_INT < 31 || perms.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (granted) {
                whenBtEnabled(action)
            } else {
                pendingBtAction = { whenBtEnabled(action) }
                btPermLauncher.launch(perms)
            }
        }

        // Menú de multijugador (crear sala / unirse con código / Bluetooth local)
        if (showOnlineMenu && state.onlineStatus == SfOnlineStatus.OFF) {
            OnlineMenuOverlay(
                onCreate = {
                    showOnlineMenu = false
                    controller.startOnline(create = true)
                },
                onJoin = { code ->
                    showOnlineMenu = false
                    controller.startOnline(create = false, code = code)
                },
                onQuickMatch = {
                    showOnlineMenu = false
                    controller.startOnlineQuick()
                },
                onBtHost = {
                    showOnlineMenu = false
                    withBtPerms(btHostPerms()) {
                        // Hacerse VISIBLE por Bluetooth (diálogo del sistema) + aceptar rivales.
                        // 300 s: margen de sobra para que el rival escanee/empareje (con 120 s
                        // el host dejaba de ser visible a media búsqueda; emparejados conectan igual).
                        runCatching {
                            context.startActivity(
                                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300),
                            )
                        }
                        controller.startBtHost()
                    }
                },
                onBtScan = {
                    showOnlineMenu = false
                    withBtPerms(btScanPerms()) { controller.startBtScan() }
                },
                // SERVIDOR LOCAL (LAN): sin permisos nuevos — directo al VM
                onLanHost = {
                    showOnlineMenu = false
                    controller.startLanHost()
                },
                onLanJoin = { ip ->
                    showOnlineMenu = false
                    controller.connectLanHost(ip)
                },
                lanDiscovered = state.lanDiscovered,
                onLanScanStart = controller::startLanDiscovery,
                onLanScanStop = controller::stopLanDiscovery,
                onDismiss = { showOnlineMenu = false },
            )
        }

        // Selector de anfitrión Bluetooth (emparejados + discovery)
        if (state.btPicking) {
            BtDevicePickerOverlay(
                devices = state.btDevices,
                onPick = controller::connectBtDevice,
                onCancel = controller::cancelBtScan,
            )
        }

        // (HOST) Solicitud de unión pendiente: ACEPTAR / RECHAZAR (lobby estilo AoE2)
        if (state.joinRequestPending) {
            JoinRequestOverlay(
                onAccept = { controller.respondJoin(true) },
                onReject = { controller.respondJoin(false) },
            )
        }

        // Falla de conexión BT → overlay BLOQUEANTE con REINTENTAR: elegiste jugar por BT,
        // así que NUNCA se cae en silencio al selector (nada de pelear contra la IA sin
        // conexión); reintentar VUELVE A PEDIR los permisos si hicieran falta.
        if (state.btError != null && state.onlineStatus == SfOnlineStatus.OFF) {
            BtRetryOverlay(
                titleRes = if (state.lanMode) Res.string.sf_lan_error_title else Res.string.sf_bt_error_title,
                error = state.btError!!,
                hintRes = if (state.lanMode) Res.string.sf_lan_error_hint else Res.string.sf_bt_error_hint,
                onRetry = {
                    val lanAddr = state.lanHostAddress
                    val addr = state.btRetryAddress
                    when {
                        // LAN: repite exactamente lo que hacías (unirte a esa IP u hostear)
                        state.lanMode && lanAddr != null -> controller.connectLanHost(lanAddr)
                        state.lanMode -> controller.startLanHost()
                        addr != null -> withBtPerms(btScanPerms()) { controller.connectBtDevice(addr) }
                        else -> withBtPerms(btHostPerms()) {
                            runCatching {
                                context.startActivity(
                                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                        .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300),
                                )
                            }
                            controller.startBtHost()
                        }
                    }
                },
                onCancel = controller::dismissBtError,
            )
        }
        */

        // Countdown 3-2-1 sincronizado por el servidor
        if (state.onlineStatus == SfOnlineStatus.COUNTDOWN) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.onlineCountdown.toString(),
                    color = Color(0xFFFFD54F),
                    fontSize = 110.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        // Botón de salida.
        //
        // ⚠️ `systemBarsPadding()` va SOLO en este botón, no en la pantalla: el combate se dibuja a
        // sangre hasta los bordes a propósito, y meter el padding arriba lo encogería. Sin esto, en
        // iOS la ✕ se cuela DEBAJO de la barra de estado (medido en el simulador: quedaba encima
        // del icono de batería y era casi imposible de pulsar).
        TextButton(
            onClick = controller::requestExit,
            modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(2.dp),
        ) {
            Text("✕", color = Color.White, fontSize = 18.sp)
        }

        // 🆕 Fin de pelea en ARCADE: ganar (CONTINUAR) / perder (REINTENTAR) / campeón. Reemplaza
        // el menú normal mientras haya una escalera en curso.
        if (state.showEndMenu && state.arcadeActive) {
            ArcadeResultOverlay(
                outcome = state.arcadeOutcome,
                step = state.arcadeStep,
                total = state.arcadeTotal,
                grade = state.matchGrade,
                onContinue = controller::arcadeContinue,
                onRetry = controller::arcadeRetry,
                onExit = controller::arcadeExit,
            )
        }

        // Menú de fin de pelea (VERSUS / online; en arcade lo sustituye ArcadeResultOverlay)
        if (state.showEndMenu && !state.arcadeActive) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(top = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 🆕 (2026-07-25) NOTA del combate estilo SF III (solo si el jugador ganó)
                if (state.matchGrade.isNotEmpty()) {
                    SfGradeBadge(state.matchGrade)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                // Aviso online: el rival ya pidió revancha
                if (state.opponentWantsRematch && state.onlineStatus == SfOnlineStatus.FIGHTING) {
                    Text(
                        text = stringResource(Res.string.sf_mp_opp_wants_rematch),
                        color = Color(0xFFFFB74D), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Online: la revancha se PIDE (arranca cuando la pidan los dos); el rival
                    // que abandonó (OPPONENT_LEFT) ya no puede aceptar → sin botón de revancha.
                    if (state.onlineStatus != SfOnlineStatus.OPPONENT_LEFT) {
                        PowButton(text = stringResource(Res.string.sf_rematch), onClick = controller::restartBattle)
                    }
                    if (state.onlineStatus == SfOnlineStatus.OFF) {
                        PowButton(text = stringResource(Res.string.sf_change_character), onClick = controller::backToCharacterSelect)
                    } else {
                        PowButton(text = stringResource(Res.string.sf_mp_leave_room), onClick = { controller.cancelOnline() })
                    }
                    PowButton(text = stringResource(Res.string.sf_back_to_menu), onClick = onExitToMap)
                }
            }
        }

        // Overlay de PAUSA (auto al bloquear/minimizar; "Continuar" reanuda)
        if (state.isPaused && !state.inCharacterSelect) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xB3000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.sf_paused),
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                    )
                    // 🆕 (2026-07-22) En vez de los controles (desactualizados): logo + título.
                    Spacer(modifier = Modifier.height(16.dp))
                    Image(
                        painter = painterResource(Res.drawable.logo_pow),
                        contentDescription = null,
                        modifier = Modifier.size(128.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Huelum vs. Goya",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Politécnico Open World",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        letterSpacing = 2.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PowButton(text = stringResource(Res.string.sf_continue), onClick = controller::togglePause)
                }
            }
        }

        // 🆕 Retomar pelea arcade guardada (tras minimizar / salir a medias)
        if (showResumeDialog && state.inCharacterSelect) {
            AlertDialog(
                onDismissRequest = {
                    controller.discardArcadeSession()
                    showResumeDialog = false
                },
                containerColor = Color(0xFF1A1016),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f),
                title = { Text(stringResource(Res.string.sf_resume_title)) },
                text = { Text(stringResource(Res.string.sf_resume_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        if (controller.resumeArcadeSession()) showResumeDialog = false
                        else showResumeDialog = false
                    }) { Text(stringResource(Res.string.sf_resume_yes), color = Color(0xFFD4AF37)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        controller.discardArcadeSession()
                        showResumeDialog = false
                    }) { Text(stringResource(Res.string.sf_resume_no), color = Color.White.copy(alpha = 0.7f)) }
                },
            )
        }

        // Diálogo de salida
        if (state.showExitDialog) {
            // Diálogo alineado al tema vino/dorado del modo (no el M3 default)
            AlertDialog(
                onDismissRequest = controller::dismissExitDialog,
                containerColor = Color(0xFF1A1016),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f),
                title = { Text(stringResource(Res.string.sf_exit_title)) },
                text = { Text(stringResource(Res.string.sf_exit_message)) },
                confirmButton = {
                    // Al salir a menú con arcade activo, forcePause ya guardó; aquí re-guarda por si acaso
                    TextButton(onClick = {
                        controller.forcePause()
                        onExitToMap()
                    }) { Text(stringResource(Res.string.sf_exit_confirm), color = Color(0xFFD4AF37)) }
                },
                dismissButton = { TextButton(onClick = controller::dismissExitDialog) { Text(stringResource(Res.string.sf_keep_fighting), color = Color.White.copy(alpha = 0.7f)) } },
            )
        }
    }
}

// ------------------------------------------------------------------
// 🆕 Menú de MODOS del modo pelea (estilo POW): al entrar, en vez del selector directo,
// se muestra ARCADE (principal), PRÁCTICA y MULTIJUGADOR. Arcade = solo eliges peleador.
// ------------------------------------------------------------------

/* Portado a SfCharacterCard.kt en commonMain.
private fun pixelateBitmapAndroidLegacy(src: ImageBitmap, targetW: Int): ImageBitmap {
    val bmp = src.asAndroidBitmap()
    val w = targetW.coerceAtLeast(1)
    val h = (w.toFloat() * bmp.height / bmp.width).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bmp, w, h, false).asImageBitmap()
}

@Composable
private fun CharacterCardAndroidLegacy(
    id: SfFighterId,
    onSelect: (SfFighterId) -> Unit,
    locked: Boolean = false,
    /** true = animar idle+walk; false = un solo frame estático (default en gama baja). */
    animate: Boolean = false,
    selected: Boolean = false,
    // 🆕 (2026-07-18) Recuadro translúcido + borde del color de la flecha (azul P1 / rojo P2)
    // para que se note quién es tu peleador y quién el rival. null = card normal.
    highlightColor: Color? = null,
    silhouette: Boolean = false, // 🆕 (2026-07-19)
    // 🆕 (2026-07-19) REVELAR a color: solo con sesión Google en Firebase + Modo Desarrollador.
    reveal: Boolean = false,
) {
    val preview = rememberFighterPreviewAndroidLegacy(id, animate = animate && (reveal || (!locked && !silhouette)))
    // 🆕 BLOQUEADO / SILUETA: silueta pixelada negra (siempre estática). Con `reveal` se ve normal.
    val obscure = (locked || silhouette) && !reveal
    val shown = if (obscure && preview != null) {
        remember(preview) { pixelateBitmapAndroidLegacy(preview, 12) }
    } else {
        preview
    }
    val shape = RoundedCornerShape(10.dp)
    // El recuadro del color de la flecha manda sobre el fondo/borde normales.
    val cardBg = highlightColor?.copy(alpha = 0.28f) ?: Color(0xFF23233A)
    val cardBorder = when {
        highlightColor != null -> highlightColor
        selected && !locked -> Color(0xFFD4AF37)
        else -> null
    }
    Column(
        modifier = Modifier
            .clip(shape)
            .background(cardBg)
            .then(if (cardBorder != null) Modifier.border(3.dp, cardBorder, shape) else Modifier)
            .clickable(enabled = !locked) { onSelect(id) }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(86.dp), contentAlignment = Alignment.BottomCenter) {
            if (shown != null) {
                Image(
                    bitmap = shown,
                    contentDescription = if (locked) "???" else id.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                    colorFilter = if (obscure) ColorFilter.tint(Color(0xFF15151F)) else null,
                )
            } else {
                Text("?", color = Color.White, fontSize = 40.sp)
            }
            // 🆕 Candado del ARCADE en la esquina superior (motiva a desbloquear jugando)
            if (locked) {
                Text(
                    text = "🔒",
                    fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            if (id.isAlpha && !locked) {
                Text(
                    text = "ALPHA",
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFFB300))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (locked && !reveal) "???" else id.displayName, // oculta la identidad hasta desbloquear
            color = if (locked && !reveal) Color(0xFFFFD54A) else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.width(90.dp),
        )
    }
}

// Selector de MAPA: vive en SfStageSelectOverlay.kt (miniatura estática + preview animado
// SOLO del focused, un frame a la vez; confirmación explícita). No mezclar con el draw loop.

/**
 * Preview animado del selector de PERSONAJE. Para sheets dedicados recorta solo las regiones
 * de Idle (nunca decodifica la hoja completa); los compartidos usan el Idle del mundo.
 */
private data class FighterPreviewAnimation(
    val frames: List<ImageBitmap>,
    val delaysMs: List<Long>,
)

// 🆕 Ritmo del PREVIEW del selector (más lento que la pelea, para que no "vibre").
private const val PREVIEW_SLOWDOWN = 1.8f  // factor sobre los delays del JSON (dedicados)
private const val PREVIEW_MIN_MS = 95L     // mínimo por frame
private const val PREVIEW_SHARED_MS = 170L // frame fijo para peleadores compartidos (runtime)

/**
 * Preview del selector de personaje.
 * - animate=false (default / no focused / gama baja): 1 frame IDLE estático (barato).
 * - animate=true (solo el focused en media/alta): idle+walk en loop.
 * Nunca decodifica la hoja completa: BitmapRegionDecoder o previewFramesFor.
 */
@Composable
private fun rememberFighterPreviewAndroidLegacy(id: SfFighterId, animate: Boolean): ImageBitmap? {
    val context = LocalContext.current
    val animation = remember(id, animate) {
        runCatching {
            val shared = id.sharedSet
            if (shared != null) {
                // 🍏 Fase 5: `previewFramesFor` ya devuelve ImageBitmap (vive en `:shared`), así
                // que el recorte usa `PowImagen` en vez del `trimTransparent` de Bitmap.
                val raw = SfSharedSheets.previewFramesFor(shared)
                val frames = if (animate) {
                    raw.map { PowImagen.recortarAOpaco(it) }
                } else {
                    listOfNotNull(raw.firstOrNull()?.let { PowImagen.recortarAOpaco(it) })
                }
                FighterPreviewAnimation(frames, List(frames.size) { PREVIEW_SHARED_MS })
            } else {
                val data = SfFrameCatalog.load(id)
                val decoder = context.assets.open(id.spriteAsset).use { ins ->
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(ins, false)
                } ?: return@runCatching null
                try {
                    fun decodeState(stateKey: String, maxFrames: Int = Int.MAX_VALUE): Pair<List<ImageBitmap>, List<Long>> {
                        val steps = data.animations[stateKey].orEmpty().filter { it.delay > 0 }.take(maxFrames)
                        val fr = steps.mapNotNull { step ->
                            val src = data.frames[step.frameKey]?.src ?: return@mapNotNull null
                            decoder.decodeRegion(
                                Rect(src[0], src[1], src[0] + src[2], src[1] + src[3]),
                                null,
                            )?.let { trimTransparent(it).asImageBitmap() }
                        }
                        val dl = steps.take(fr.size).map {
                            (it.delay * SfConstants.FRAME_TIME_MS * PREVIEW_SLOWDOWN).toLong()
                                .coerceAtLeast(PREVIEW_MIN_MS)
                        }
                        return fr to dl
                    }
                    if (!animate) {
                        // Solo 1 frame idle (estático)
                        val (idleF, idleD) = decodeState(SfFighterState.IDLE.jsKey, maxFrames = 1)
                        FighterPreviewAnimation(idleF, idleD.ifEmpty { listOf(PREVIEW_MIN_MS) })
                    } else {
                        val (idleF, idleD) = decodeState(SfFighterState.IDLE.jsKey)
                        val (walkF, walkD) = decodeState(SfFighterState.WALK_FORWARD.jsKey)
                        FighterPreviewAnimation(idleF + walkF, idleD + walkD)
                    }
                } finally {
                    decoder.recycle()
                }
            }
        }.getOrNull()?.takeIf { it.frames.isNotEmpty() }
    }
    var frameIndex by remember(id, animation, animate) { mutableStateOf(0) }
    LaunchedEffect(id, animation, animate) {
        frameIndex = 0
        val anim = animation ?: return@LaunchedEffect
        if (!animate || anim.frames.size <= 1) return@LaunchedEffect
        while (true) {
            delay(anim.delaysMs.getOrElse(frameIndex) { 100L })
            frameIndex = (frameIndex + 1) % anim.frames.size
        }
    }
    return animation?.frames?.getOrNull(frameIndex)
}

/** Recorta el bitmap a su contenido opaco (los peleadores POW vienen en celdas 256² con aire). */
private fun trimTransparent(bmp: Bitmap): Bitmap {
    val w = bmp.width
    val h = bmp.height
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    var minX = w; var minY = h; var maxX = -1; var maxY = -1
    for (y in 0 until h) {
        for (x in 0 until w) {
            if ((px[y * w + x] ushr 24) != 0) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    if (maxX < 0) return bmp
    return Bitmap.createBitmap(bmp, minX, minY, maxX - minX + 1, maxY - minY + 1)
}
*/

// ------------------------------------------------------------------
// Diamante Xbox de POW (mismas letras/colores/posiciones que
// ActionButtonsController: Y arriba · X izquierda · B derecha · A abajo).
// Solo cambia QUÉ HACE cada botón en este modo (como en conducción).
// ------------------------------------------------------------------

@Composable
private fun FighterXboxButtons(
    modifier: Modifier = Modifier,
    onPunch: (SfAttackStrength) -> Unit,
    onKick: () -> Unit,
    bonusPowerCount: Int,
    onBonusPower: () -> Unit,
    // 🆕 (2026-07-22) TUTORIAL: letra del botón que TOCA presionar (brilla/pulsa) o null.
    highlight: String? = null,
) {
    Box(
        modifier = modifier
            .size(180.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Y arriba — PUÑO MEDIO (amarillo)
            SfTutorialButtonGlow(active = highlight == "Y") {
                ActionButton(text = "Y", color = Color(0xFFF1C40F), onHoldEvent = { pressed ->
                    if (pressed) onPunch(SfAttackStrength.MEDIUM)
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // X izquierda — PUÑO LIGERO (azul)
                SfTutorialButtonGlow(active = highlight == "X") {
                    ActionButton(text = "X", color = Color(0xFF3498DB), onHoldEvent = { pressed ->
                        if (pressed) onPunch(SfAttackStrength.LIGHT)
                    })
                }
                Spacer(modifier = Modifier.size(48.dp))
                // B derecha — PUÑO FUERTE (rojo)
                SfTutorialButtonGlow(active = highlight == "B") {
                    ActionButton(text = "B", color = Color(0xFFE74C3C), onHoldEvent = { pressed ->
                        if (pressed) onPunch(SfAttackStrength.HEAVY)
                    })
                }
            }
            // A abajo — PATADA (verde; fuerza según joystick: neutro/adelante/atrás)
            SfTutorialButtonGlow(active = highlight == "A") {
                ActionButton(text = "A", color = Color(0xFF2ECC71), onHoldEvent = { pressed ->
                    if (pressed) onKick()
                })
            }
        }
        if (bonusPowerCount > 0) {
            // Centro del diamante: recorre P1..PN. El siguiente toque avanza al poder
            // siguiente; Yoalli tiene 9 y La Tzitzimime 5.
            ActionButton(text = "P", color = Color(0xFF8E44AD), onHoldEvent = { pressed ->
                if (pressed) onBonusPower()
            })
        }
    }
}

/**
 * 🆕 (2026-07-22) RESALTADO del botón que pide el paso ACTUAL del tutorial: pulsa de tamaño
 * y lleva un aro amarillo. Con active=false es transparente (no cambia el layout del botón).
 */
@Composable
private fun SfTutorialButtonGlow(active: Boolean, content: @Composable () -> Unit) {
    if (!active) {
        content()
        return
    }
    val pulse by rememberInfiniteTransition(label = "sfTutorialPulse").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(340), RepeatMode.Reverse),
        label = "sfTutorialPulseF",
    )
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .border(3.dp, Color(0xFFFFF176), CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * 🆕 (2026-07-22) Botón físico que corresponde a la etiqueta de un paso del tutorial
 * (mismo truco de substring que chipColor en SfTutorialOverlay). null = va con el joystick.
 */
private fun sfButtonForLabel(label: String): String? = when {
    label.contains("FATALITY") || label.contains("SÚPER") -> "R2"
    label.contains("PARRY") -> "L1"
    label.contains("AGARRE") -> "R1"
    label.contains("BURLA") -> "L2"
    label.contains("PUÑO LIGERO") -> "X"
    label.contains("PUÑO MEDIO") -> "Y"
    label.contains("PUÑO FUERTE") -> "B"
    label.contains("PUÑO") -> "X" // ↓ + puño / puño en el aire / especial (remate con puño)
    label.contains("PATADA") -> "A"
    else -> null
}

/**
 * 🆕 (2026-07-22) Gesto de JOYSTICK del paso del tutorial (para EXPLICAR el combo, no solo el
 * botón). null = el paso no lleva joystick. Se deduce de la etiqueta (que ya trae las flechas).
 */
private fun sfJoystickHintForLabel(label: String): String? {
    // Gestos completos primero.
    when {
        label.contains("↓ ↘ →") -> return "↓ ↘ →"                            // especial (hadouken)
        label.contains("DOBLE TOQUE →") -> return "→ →"                        // dash
        label.contains("DOBLE TOQUE ←") -> return "← ←"                        // backdash
        label.contains("SOSTÉN →") || label.contains("CORRE") -> return "→ →"  // correr / fatality
        label.contains("SALTA") || label.contains("SALTAR") -> return "↑"
    }
    val down = label.contains("↓")
    // Dirección horizontal: la EXPLÍCITA de la etiqueta manda. Si no hay flecha, la fuerza de la
    // PATADA la da el joystick (fuerte = ATRÁS ←, media = ADELANTE →), porque el botón es uno solo
    // → así la BARRIDA (↓ + patada fuerte) se muestra como ↓ ← y no como un simple ↓.
    val horiz = when {
        label.contains("→") -> "→"
        label.contains("←") || label.contains("ATRÁS") -> "←"
        label.contains("PATADA FUERTE") -> "←"
        label.contains("PATADA MEDIA") -> "→"
        else -> null
    }
    return when {
        down && horiz != null -> "↓ $horiz"
        down -> "↓"
        label.contains("↑") -> "↑"
        else -> horiz
    }
}

/**
 * 🆕 (2026-07-22) Indicador de JOYSTICK del tutorial: la dirección/gesto a marcar, en una burbuja
 * que pulsa, encima del joystick, para que se sepa QUÉ mover (p.ej. la Barrida = ↓ + patada).
 */
@Composable
private fun SfJoystickHint(text: String, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "sfJoyHint").animateFloat(
        initialValue = 0.86f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "sfJoyHintF",
    )
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xE6152A4A))
            .border(2.dp, Color(0xFF5B6ACD), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "🕹 ", fontSize = 14.sp)
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
    }
}

// ------------------------------------------------------------------
// 🆕 (2026-07-22) MOVESET 3rd Strike como GATILLOS neón (rediseño "Alt 1 · Neón Arcade" del
// dueño): IZQUIERDA L1 Parry · L2 Burla; DERECHA R1 Agarre · R2 Súper. El diamante Y/X/B/A no
// cambia. La SÚPER solo se ve encendida con el medidor lleno.
//
// 🎨 RESERVA DE PALETA (NO borrar — para expansión futura, "Alt 3 · Gema/Elementos"):
//   Esmeralda #2D6A4F (borde #1B4332) · Rubí #9B2226 (borde #641220)
//   Zafiro    #003566 (borde #001D3D) · Amatista #5A189A (borde #3C096C)
// ------------------------------------------------------------------

@Composable
private fun FighterShoulderButtons(
    modifier: Modifier = Modifier,
    isLeft: Boolean,
    superReady: Boolean,
    onParry: () -> Unit,
    onGrab: () -> Unit,
    onTaunt: () -> Unit,
    onSuper: () -> Unit,
    // 🆕 (2026-07-22) TUTORIAL: letra del botón que TOCA presionar (brilla/pulsa) o null.
    highlight: String? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (isLeft) {
            // L1 · Parry (cian neón): desvía el golpe si se aprieta a tiempo
            SfNeonButton("L1", Color(0xFF00F2FE), Color(0xFF00ADB5), highlight == "L1", onParry)
            Spacer(modifier = Modifier.size(6.dp))
            // L2 · Burla (rosa neón, sin efecto en combate)
            SfNeonButton("L2", Color(0xFFFF007F), Color(0xFFC5005E), highlight == "L2", onTaunt)
        } else {
            // R1 · Agarre (violeta neón): lanza al rival pegado, atraviesa la guardia
            SfNeonButton("R1", Color(0xFF7928CA), Color(0xFF56149F), highlight == "R1", onGrab)
            Spacer(modifier = Modifier.size(6.dp))
            // R2 · Súper (naranja neón; apagado si el medidor no está lleno)
            SfNeonButton(
                label = "R2",
                fill = if (superReady) Color(0xFFFF7B00) else Color(0xFF555555),
                border = if (superReady) Color(0xFFC85A00) else Color(0xFF3A3A3A),
                highlighted = highlight == "R2",
                onPress = onSuper,
            )
        }
    }
}

/** Botón SF con relleno neón + aro de borde, reutilizando ActionButton (hold + feedback). */
@Composable
private fun SfNeonButton(
    label: String,
    fill: Color,
    border: Color,
    highlighted: Boolean,
    onPress: () -> Unit,
) {
    SfTutorialButtonGlow(active = highlighted) {
        Box(
            modifier = Modifier.border(2.dp, border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ActionButton(text = label, color = fill, onHoldEvent = { pressed -> if (pressed) onPress() })
        }
    }
}

