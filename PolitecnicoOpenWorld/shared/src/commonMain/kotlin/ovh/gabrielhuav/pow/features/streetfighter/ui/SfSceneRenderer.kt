package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfBox
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_BONUS_POWER_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterData
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireballState
// 🍏 Accesores compatibles de `:shared` en vez de `org.json` (de la JVM, no existe en iOS).
import ovh.gabrielhuav.pow.data.json.getDouble
import ovh.gabrielhuav.pow.data.json.getInt
import ovh.gabrielhuav.pow.data.json.powJsonObjeto
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFrameDef
import ovh.gabrielhuav.pow.features.streetfighter.data.SfTheme
import ovh.gabrielhuav.pow.platform.assets.PowAssets
import ovh.gabrielhuav.pow.platform.imagen.PowImagen
import ovh.gabrielhuav.pow.platform.imagen.decodificarReducido
import ovh.gabrielhuav.pow.shared.recursos.*

// ────────────────────────────────────────────────────────────────────────────
// 🎨 RENDER de la escena de pelea (Canvas): fondos, sprites, hitboxes y overlays
//
// Aquí vive TODO lo que se pinta durante la pelea: el fondo (animado o fijo) con su encuadre por
// mapa, los sprites de los dos peleadores desde el atlas, las hitboxes del modo desarrollador, el
// contador de FPS, la nota del combate y el overlay de CARGANDO.
// ⚠️ Es código de DIBUJO por frame: no metas aquí lógica de juego ni llamadas al ViewModel.
// ⚠️ Los assets pesados NO se decodifican aquí (ver el gotcha de 09 §12: decodificar atlas en el
// hilo de UI era el 'se traba al cargar').
//
// Extraído de StreetFighterScreen.kt (4029 líneas) en el refactor de tamaño de la Fase 5.
// Son composables/helpers TOP-LEVEL del mismo paquete: `internal` en vez de `private` para
// que la Screen los siga viendo. Sin cambios de comportamiento.
// ────────────────────────────────────────────────────────────────────────────
/**

 * 🆕 (2026-07-21) Arte PRESTADA para el placeholder ALPHA: hoja del estudiante del mismo
 * género que se usa cuando al peleador le falta la hoja de un movimiento nuevo.
 */
class AlphaFallback(
    val data: SfFighterData,
    val sheetKey: String,
    val bitmap: ImageBitmap?,
    /** 🆕 (2026-07-22) Submuestreo PROPIO del atlas ALPHA (silueta → media res gratis). */
    val sheetScale: Float = 1f,
)

/**
 * 🆕 (2026-07-22, Bloque B) Assets PESADOS de una pelea, decodificados en Dispatchers.IO
 * bajo el overlay CARGANDO: atlas por identidad (incluye ambas caras de una metamorfosis),
 * placeholder ALPHA y alturas de contenido opaco por identidad (vacío en gama baja).
 */
class SfFightAssets(
    val sheets: Map<String, ImageBitmap>,
    val alpha: AlphaFallback?,
    val contentH: Map<SfFighterId, Map<String, Int>>,
)

/** Rótulo del placeholder (fuente arcade del HUD: solo A-Z y 0-9). */
internal const val ALPHA_TAG = "ALPHA"

internal class SceneCtx(
    val scale: Float,
    val ox: Float,   // offset del letterbox en px de pantalla
    val oy: Float,
    val camX: Float,
    val camY: Float,
)

fun DrawScope.drawScene(
    theme: SfTheme,
    state: SfSceneState,
    images: Map<String, ImageBitmap>,
    playerData: SfFighterData,
    cpuData: SfFighterData,
    bg: SfStageBackground?,
    roundBannerText: String,
    fightBannerText: String,
    comboHitsLabel: String,
    showHitboxes: Boolean = false,
    playerContentH: Map<String, Int> = emptyMap(),
    cpuContentH: Map<String, Int> = emptyMap(),
    bgFile: String? = null,
    playerSilhouette: Boolean = false,
    alphaFallback: AlphaFallback? = null,
    // 🆕 (2026-07-21) Submuestreo de los atlas de peleador en gama baja (1f = completo).
    sheetScale: Float = 1f,
) {
    val framing = framingForBg(bgFile) // 🆕 zoom/anclaje por escenario (Facultad de Medicina…)
    val scale = minOf(size.width / SfConstants.SCENE_WIDTH, size.height / SfConstants.SCENE_HEIGHT)
    val ctx = SceneCtx(
        scale = scale,
        ox = (size.width - SfConstants.SCENE_WIDTH * scale) / 2f,
        oy = (size.height - SfConstants.SCENE_HEIGHT * scale) / 2f,
        camX = state.cameraX,
        camY = state.cameraY,
    )
    val stage = images[theme.stageImage] // 🆕 nullable: kenstage.png se quitó (copyright)
    val t = state.gameTimeMs

    // 🆕 (2026-07-18) PARALLAX VERTICAL DE SALTO: en los mapas con zoom (headroom de cielo
    // recortado arriba) el fondo BAJA al brincar → se ve "más arriba" del mapa. Fracción según
    // la altura del peleador MÁS ALTO (apex de salto ≈ 90 px de mundo). En mapas sin zoom no hay
    // headroom → sin efecto (los 11 confirmados quedan igual).
    val highestY = minOf(state.player.y, state.cpu.y)
    val jumpFrac = ((SfConstants.STAGE_FLOOR - highestY) / 90f).coerceIn(0f, 1f)

    if (bg is SfStageBackground.Animated) {
        // ---- FONDO POW ANIMADO (atlas de frames) con parallax de cámara ----
        drawAnimatedBackground(ctx, bg, t, framing, jumpFrac)
    } else if (bg is SfStageBackground.Static) {
        // ---- FONDO POW a pantalla completa (foto fija) con parallax de cámara ----
        drawFullBackground(ctx, bg.image, framing, jumpFrac)
    } else if (stage != null) {
        // ---- Fondo del escenario clásico (parallax por capas) ----
        val bob = theme.boatBob[((t / 366) % theme.boatBob.size).toInt()]
        drawSprite(ctx, stage, theme.stageBackground, 16f - ctx.camX / 2.157303f, -ctx.camY)
        val flag = theme.flagFrames[((t / 133) % theme.flagFrames.size).toInt()]
        drawSprite(ctx, stage, flag, 576f - ctx.camX / 2.157303f, 48f - ctx.camY)
        drawSprite(ctx, stage, theme.stageBoat, 150f - ctx.camX / 1.613445f, -3f - ctx.camY - bob)
        theme.stagePeople.forEach { prop ->
            drawSprite(ctx, stage, prop.src, prop.x - ctx.camX / 1.613445f, prop.y.toFloat() - bob - ctx.camY)
        }
        drawSprite(ctx, stage, theme.stageFloor, SfConstants.STAGE_PADDING - ctx.camX * 1.1f, 176f - ctx.camY)
        drawSprite(ctx, stage, theme.stageFloorBottom, SfConstants.STAGE_PADDING - ctx.camX * 1.1f, 232f - ctx.camY)
        drawSprite(ctx, stage, theme.ballardSmall, 468f - 92f - ctx.camX / 1.54f, 166f - ctx.camY)
        drawSprite(ctx, stage, theme.ballardSmall, 468f + 92f - ctx.camX / 1.54f, 166f - ctx.camY)
        drawSprite(ctx, stage, theme.sideBarrels, SfConstants.STAGE_PADDING + SfConstants.STAGE_WIDTH - 152f - ctx.camX, 120f - ctx.camY)
    } else {
        // Sin fondo POW ni escenario clásico (kenstage quitado) → relleno oscuro
        drawRect(
            color = Color(0xFF0E0E16),
            topLeft = Offset(ctx.ox, ctx.oy),
            size = Size(SfConstants.SCENE_WIDTH * scale, SfConstants.SCENE_HEIGHT * scale),
        )
    }

    // ---- Sombras ----
    drawShadow(ctx, theme, images.getValue(theme.shadowImage), state.player, bgFile)
    drawShadow(ctx, theme, images.getValue(theme.shadowImage), state.cpu, bgFile)

    // ---- Peleadores (sheet según el personaje del snapshot) ----
    // 🆕 (2026-07-21) PLACEHOLDER ALPHA: si al peleador le falta la hoja del movimiento en
    // curso, se dibuja con el arte del estudiante de su género en SILUETA NEGRA PIXELADA y
    // con el rótulo "ALPHA" encima (mismo lenguaje visual que los personajes bloqueados).
    listOf(
        Triple(state.player, playerData, playerContentH),
        Triple(state.cpu, cpuData, cpuContentH),
    ).forEachIndexed { side, (fighter, data, contentH) ->
        val alpha = alphaFallback?.takeIf { data.animations[fighter.state.jsKey].isNullOrEmpty() }
        if (alpha != null && alpha.bitmap != null) {
            drawFighter(
                ctx, images + (alpha.sheetKey to alpha.bitmap), alpha.data, fighter, t,
                showHitboxes, emptyMap(), silhouette = true, sheetKeyOverride = alpha.sheetKey,
                // ⚠️ El atlas ALPHA lleva SU propio submuestreo (no el global).
                sheetScale = alpha.sheetScale,
            )
            val hud = images.getValue(theme.hudImage)
            val w = ALPHA_TAG.length * 12f * 0.7f
            drawFontText(
                ctx, theme, hud, ALPHA_TAG,
                fighter.x - ctx.camX - w / 2f, fighter.y - ctx.camY - 118f, 0.7f,
            )
        } else {
            drawFighter(
                ctx, images, data, fighter, t, showHitboxes, contentH,
                silhouette = side == 0 && playerSilhouette,
                sheetScale = sheetScale,
            )
        }
    }

    // ---- 🆕 (2026-07-22) MAREO: estrellitas PROCEDURALES orbitando la cabeza ----
    // No hay sprite de estrellas: se dibujan en Canvas (círculos dorados + destello blanco)
    // sobre la pose stun-3. Órbita elíptica ~1.6 s/vuelta, 3 estrellas desfasadas 120°.
    listOf(state.player, state.cpu).forEach { f ->
        if (f.state != SfFighterState.STUN) return@forEach
        val headY = f.y - 104f
        for (i in 0 until 3) {
            val ang = t / 260f + i * 2.0944f // 2π/3 de desfase entre estrellas
            val sx = f.x + kotlin.math.cos(ang) * 16f
            val sy = headY + kotlin.math.sin(ang) * 5f
            val cxPx = ctx.ox + (sx - ctx.camX) * ctx.scale
            val cyPx = ctx.oy + (sy - ctx.camY) * ctx.scale
            drawCircle(color = Color(0xFFFFD700), radius = 2.4f * ctx.scale, center = Offset(cxPx, cyPx))
            drawCircle(color = Color(0xFFFFFDE7), radius = 1f * ctx.scale, center = Offset(cxPx, cyPx))
        }
    }

    // ---- Proyectiles especiales ----
    // Si el DUEÑO del proyectil trae sus propios frames "proj-*" en su JSON (Prankedy:
    // tanque de gas + estallido de confeti), se usan ESOS desde su sheet; si no, el
    // fireball del tema (hoy, el hadouken del clon).
    state.fireballs.forEach { fb ->
        val owner = if (fb.ownerIndex == 0) state.player else state.cpu
        val ownerData = if (fb.ownerIndex == 0) playerData else cpuData
        val ownerSheet = images[owner.id.spriteAsset.substringAfterLast('/')]
        // 🆕 (2026-07-21) PODERES DE PROYECTIL con efecto PROPIO: si el fireball viene de un
        // bonus power cuya hoja trae sus 3 cuadros de efecto (bonus-N-2 vuelo, -3 vuelo/impacto,
        // -4 disipación), se dibujan ESOS. Si no existen, cae a los proj-* compartidos.
        val bonusFly = "bonus-${fb.bonusPower}-2"
        val useBonusFx = fb.bonusPower > 0 && ownerData.frames.containsKey(bonusFly)
        if (ownerSheet != null && (useBonusFx || ownerData.frames.containsKey("proj-fly-1"))) {
            val key = if (useBonusFx) {
                if (fb.state == SfFireballState.ACTIVE) {
                    if (fb.animationFrame % 2 == 0) bonusFly else "bonus-${fb.bonusPower}-3"
                } else {
                    "bonus-${fb.bonusPower}-${(fb.animationFrame + 3).coerceIn(3, 4)}"
                }
            } else if (fb.state == SfFireballState.ACTIVE) {
                if (fb.animationFrame % 2 == 0) "proj-fly-1" else "proj-fly-2"
            } else {
                "proj-hit-${(fb.animationFrame + 1).coerceIn(1, 3)}"
            }
            ownerData.frames[key]?.let { fd ->
                val effectScale = ownerData.projectileEvents[fb.strength]?.visualScale ?: 1f
                drawSpriteAnchored(
                    ctx, ownerSheet, fd.src, fd.origin, fb.x, fb.y, fb.direction,
                    spriteScale = effectScale, sheetScale = sheetScale,
                )
            }
        }
        // (Antes había un fallback al hadouken de Ken.png; ELIMINADO por copyright — todos los
        // peleadores tienen sus propios frames proj-*. Si alguno no los trae, no se dibuja proyectil.)
    }

    // ---- Splashes de impacto ----
    state.splashes.forEach { sp ->
        val rows = theme.splashFrames.getValue(sp.strength)
        val frame = rows[sp.playerId.coerceIn(0, 1)][sp.animationFrame.coerceIn(0, 3)]
        drawSprite(ctx, images.getValue(theme.splashImage), frame.src, sp.x - ctx.camX - frame.origin[0], sp.y - ctx.camY - frame.origin[1])
    }

    // ---- Primer plano (solo con el escenario clásico) ----
    if (bg == null && stage != null) {
        drawSprite(ctx, stage, theme.ballardLarge, SfConstants.STAGE_MID_POINT + SfConstants.STAGE_PADDING - 147f - ctx.camX / 0.958f, 200f - ctx.camY)
        drawSprite(ctx, stage, theme.ballardLarge, SfConstants.STAGE_MID_POINT + SfConstants.STAGE_PADDING + 147f - ctx.camX / 0.958f, 200f - ctx.camY)
    }

    // ---- HUD ----
    drawHud(ctx, theme, images.getValue(theme.hudImage), state)

    // ---- Texto de ganador: "<PERSONAJE> WINS" con la FUENTE arcade del HUD ----
    // (funciona para CUALQUIER peleador; ya no depende de winnerText.png por filas)
    state.winnerIndex?.let { winner ->
        if (state.battleEnded) {
            val winnerFighter = if (winner == 0) state.player else state.cpu
            val text = "${winnerFighter.id.shortName} WINS"
            val sizeMul = 2f
            val textW = text.length * 12f * sizeMul
            drawFontText(ctx, theme, images.getValue(theme.hudImage), text, (SfConstants.SCENE_WIDTH - textW) / 2f, 58f, sizeMul)
        }
    }

    // ---- 🆕 (2026-07-21) MEDIDOR DE SÚPER (3rd Strike): barra bajo cada nombre ----
    // Solo se pinta si el peleador tiene el moveset nuevo (si no, nunca carga y estorbaría).
    listOf(0 to state.player, 1 to state.cpu).forEach { (side, fighter) ->
        val data = if (side == 0) playerData else cpuData
        if (data.animations["superArt"].isNullOrEmpty()) return@forEach
        val frac = (fighter.superMeter / SfConstants.SUPER_METER_MAX.toFloat()).coerceIn(0f, 1f)
        val w = 96f
        val x = if (side == 0) 32f else SfConstants.SCENE_WIDTH - 32f - w
        val y = 44f
        val full = frac >= 1f
        // 🆕 (2026-07-22) BRILLO al llenarse: halo dorado + pulso del relleno (~8 Hz).
        if (full) {
            drawRect(
                color = Color(0x66FFD700),
                topLeft = Offset(ctx.ox + (x - 2f) * ctx.scale, ctx.oy + (y - 2f) * ctx.scale),
                size = Size((w + 4f) * ctx.scale, 9f * ctx.scale),
            )
        }
        drawRect( // marco
            color = Color(0xFF202020),
            topLeft = Offset(ctx.ox + x * ctx.scale, ctx.oy + y * ctx.scale),
            size = Size(w * ctx.scale, 5f * ctx.scale),
        )
        drawRect( // relleno (dorado PULSANTE al llenarse = súper lista)
            color = when {
                !full -> Color(0xFF3AA6FF)
                (t / 120) % 2 == 0L -> Color(0xFFFFD700)
                else -> Color(0xFFFFF59D)
            },
            topLeft = Offset(ctx.ox + (x + 1f) * ctx.scale, ctx.oy + (y + 1f) * ctx.scale),
            size = Size((w - 2f) * frac * ctx.scale, 3f * ctx.scale),
        )
        // 🆕 (2026-07-22) BARRA DE MAREO: debajo de la de súper, solo si hay mareo activo
        // (naranja→roja al acercarse al stun). El estado STUN la pinta llena y roja.
        val dFrac = (fighter.dizzyMeter / SfConstants.DIZZY_METER_MAX.toFloat()).coerceIn(0f, 1f)
        val stunned = fighter.state == SfFighterState.STUN
        if (dFrac > 0f || stunned) {
            val dy = y + 7f
            drawRect(
                color = Color(0xFF202020),
                topLeft = Offset(ctx.ox + x * ctx.scale, ctx.oy + dy * ctx.scale),
                size = Size(w * ctx.scale, 4f * ctx.scale),
            )
            drawRect(
                color = if (stunned || dFrac >= 0.8f) Color(0xFFE53935) else Color(0xFFFF9800),
                topLeft = Offset(ctx.ox + (x + 1f) * ctx.scale, ctx.oy + (dy + 1f) * ctx.scale),
                size = Size((w - 2f) * (if (stunned) 1f else dFrac) * ctx.scale, 2f * ctx.scale),
            )
        }
    }

    // ---- 🆕 (2026-07-20) Contador de COMBO (3rd Strike): "N GOLPES" del lado del atacante ----
    // El VM llena/expira comboCount (>=2 = mostrar); aquí SOLO se pinta con sombra.
    if (state.comboCount >= 2 && state.comboPlayerId in 0..1) {
        val hud = images.getValue(theme.hudImage)
        val comboText = "${state.comboCount} $comboHitsLabel"
        val sizeMul = 1.2f
        val tw = comboText.length * 12f * sizeMul
        val x = if (state.comboPlayerId == 0) 16f else SfConstants.SCENE_WIDTH - tw - 16f
        val y = 64f // debajo de las barras de vida, sin tapar a los peleadores
        drawFontText(ctx, theme, hud, comboText, x + 1f, y + 1f, sizeMul) // sombra
        drawFontText(ctx, theme, hud, comboText, x, y, sizeMul)
    }

    // ---- 🆕 Banner de RONDA ("RONDA N" + "PELEA"), fuente arcade, input congelado ----
    if (state.showRoundIntro) {
        val hud = images.getValue(theme.hudImage)
        val rw = roundBannerText.length * 12f * 2f
        drawFontText(ctx, theme, hud, roundBannerText, (SfConstants.SCENE_WIDTH - rw) / 2f, 60f, 2f)
        // 🆕 (2026-07-22) Cuenta 3-2-1 GRANDE; al llegar a 0 se muestra "PELEA".
        val cd = state.roundIntroCountdown
        if (cd > 0) {
            val txt = cd.toString()
            val cw = txt.length * 12f * 4f
            drawFontText(ctx, theme, hud, txt, (SfConstants.SCENE_WIDTH - cw) / 2f, 92f, 4f)
        } else {
            val fw = fightBannerText.length * 12f * 1.8f
            drawFontText(ctx, theme, hud, fightBannerText, (SfConstants.SCENE_WIDTH - fw) / 2f, 100f, 1.8f)
        }
    }

    // ---- 🆕 Subtítulo del special (frase del personaje, fuente arcade POW, pequeño) ----
    val sub = state.specialSubtitleHud
    if (!sub.isNullOrBlank() &&
        state.specialSubtitleUntilMs > 0L &&
        state.gameTimeMs < state.specialSubtitleUntilMs
    ) {
        val hud = images.getValue(theme.hudImage)
        val sizeMul = 0.6f
        val maxChars = 24
        val maxLines = 3
        // 🆕 (2026-07-22) UN tramo '|' A LA VEZ, sincronizado con la voz: la ventana
        // [start,until] se reparte por igual entre los tramos y se pinta el tramo ACTUAL
        // (envuelto a ~24 chars si es largo). Ya NO se muestran todos a la vez.
        val segments = sub.split('|').filter { it.isNotBlank() }
        if (segments.isNotEmpty()) {
            val start = state.specialSubtitleStartMs
            val total = (state.specialSubtitleUntilMs - start).coerceAtLeast(1L)
            val elapsed = (state.gameTimeMs - start).coerceIn(0L, total - 1L)
            val segIndex = (elapsed * segments.size / total).toInt().coerceIn(0, segments.lastIndex)
            val words = segments[segIndex].split(' ').filter { it.isNotBlank() }
            val lines = ArrayList<String>(maxLines)
            val cur = StringBuilder()
            for (w in words) {
                when {
                    cur.isEmpty() -> cur.append(w)
                    cur.length + 1 + w.length <= maxChars -> cur.append(' ').append(w)
                    else -> {
                        lines.add(cur.toString()); cur.setLength(0); cur.append(w)
                        if (lines.size >= maxLines) break
                    }
                }
            }
            if (cur.isNotEmpty() && lines.size < maxLines) lines.add(cur.toString())
            val lineH = 12f * sizeMul + 3f
            val bottomBaseline = SfConstants.SCENE_HEIGHT - 18f // 🆕 (2026-07-22) subtítulo un poco más arriba
            lines.reversed().forEachIndexed { i, ln ->
                val w = ln.length * 12f * sizeMul
                val x = (SfConstants.SCENE_WIDTH - w) / 2f
                val y = bottomBaseline - i * lineH - 12f * sizeMul
                drawFontText(ctx, theme, hud, ln, x + 1f, y + 1f, sizeMul) // sombra
                drawFontText(ctx, theme, hud, ln, x, y, sizeMul)
            }
        }
    }
}

/**
 * Fondo POW a pantalla completa: se escala para cubrir el ALTO de la escena (224) y el
 * ancho sobrante panea con la cámara (parallax 1:1 con el avance por el stage).
 */
/**
 * Fondo de escenario POW: estático (una foto) o ANIMADO (atlas de frames tipo "filmstrip"
 * generado por tools/build_map_backgrounds.py). El atlas es UN solo bitmap; se anima
 * pintando un sub-rect distinto por frame (col = i % cols, row = i / cols). minSdk=24 → NO
 * usamos WebP animado (AnimatedImageDrawable es API 28+); el atlas funciona en todas.
 */
sealed interface SfStageBackground {
    data class Static(val image: ImageBitmap) : SfStageBackground
    data class Animated(
        val atlas: ImageBitmap,
        val frameW: Int,
        val frameH: Int,
        val cols: Int,
        val rows: Int,
        val frameCount: Int,
        val fps: Float,
    ) : SfStageBackground
}

/**
 * 🆕 (2026-07-18) Encuadre por ESCENARIO: por defecto el fondo se escala para que su ALTO
 * completo entre en la escena (224). En algunos mapas eso deja a los peleadores "flotando"
 * (mucho cielo/edificio arriba y el piso muy abajo) y se ve cuadrado. [zoom] > 1 amplía el
 * fondo ANCLÁNDOLO AL PISO (recorta el cielo por arriba) → se ve panorámico y los peleadores
 * quedan sobre el suelo. [offsetY] (unidades de escena, + = baja la imagen) afina el anclaje.
 * NO regenera el asset: solo cambia cómo se dibuja.
 */
internal data class SfBgFraming(val zoom: Float = 1f, val offsetY: Float = 0f)

/**
 * Ajustes por SUBSTRING del archivo de fondo (cubre las 3 luces: día/noche_1/noche_2, que
 * comparten la base `fondo_<slug>_...`). Sin entrada = sin ajuste (zoom 1, sin offset).
 */
internal val SF_BG_FRAMING: List<Pair<String, SfBgFraming>> = listOf(
    // 🆕 (2026-07-18ñ) Técnica panorámica (zoom anclado al piso + parallax de salto) en los 16
    // mapas (día/noche_1/noche_2 por substring). Peleadores SIEMPRE sobre el suelo y al saltar se
    // ve más arriba. Valores tuneables por mapa en dispositivo (subir/bajar zoom u offsetY).
    "facultad_medicina" to SfBgFraming(zoom = 1.35f),
    "fes_aragon" to SfBgFraming(zoom = 1.30f),
    "piramidesol" to SfBgFraming(zoom = 1.30f),
    "uam_cuajimalpa" to SfBgFraming(zoom = 1.30f),
    "zocalo" to SfBgFraming(zoom = 1.30f),
    // Los 11 que estaban SIN zoom (antes "aprobados"): ahora también panorámicos por pedido del dueño.
    "escom" to SfBgFraming(zoom = 1.30f),
    "queso_ipn" to SfBgFraming(zoom = 1.30f),
    "esime_azc" to SfBgFraming(zoom = 1.30f),
    "cecyt_9" to SfBgFraming(zoom = 1.30f),
    "cecyt_2" to SfBgFraming(zoom = 1.30f),
    "unam_biblioteca_cu" to SfBgFraming(zoom = 1.30f),
    "fes_acatlan" to SfBgFraming(zoom = 1.30f),
    "uam_azcapo" to SfBgFraming(zoom = 1.30f),
    "islamunecas" to SfBgFraming(zoom = 1.30f),
    "mictlan" to SfBgFraming(zoom = 1.30f),
    "campos_agave_jalisco" to SfBgFraming(zoom = 1.30f),
)

internal fun framingForBg(file: String?): SfBgFraming =
    file?.let { f -> SF_BG_FRAMING.firstOrNull { f.contains(it.first) }?.second } ?: SfBgFraming()

/**
 * Decodifica el fondo desde assets. Si el archivo termina en "_anim.webp" y existe su JSON
 * hermano, devuelve un fondo ANIMADO; si no, uno estático.
 * RGB_565 (sin alpha). En [lowEnd] usa inSampleSize=2 (~¼ de RAM de textura) y deriva
 * frameW/H del atlas real (no del JSON a full-res).
 */
fun loadStageBackground(
    imagesDir: String,
    file: String,
    lowEnd: Boolean = false,
): SfStageBackground? {
    return runCatching {
        val reduction = if (lowEnd) 4 else 1
        val bmp = decodificarReducido(PowAssets.bytes(imagesDir + file), reduction)
        val jsonName = file.substringBeforeLast('.') + ".json"
        val meta = runCatching { PowAssets.texto(imagesDir + jsonName) }.getOrNull()
        if (file.endsWith("_anim.webp") && meta != null) {
            val o = powJsonObjeto(meta)
            val cols = o.getInt("cols").coerceAtLeast(1)
            val rows = o.getInt("rows").coerceAtLeast(1)
            // Tras sample, el tamaño de celda real = atlas/grid (más fiable que frameW del JSON)
            val cellW = (bmp.width / cols).coerceAtLeast(1)
            val cellH = (bmp.height / rows).coerceAtLeast(1)
            // Gama baja: bajar fps de anim del fondo (menos “trabajo” visual; sigue vivo)
            val fps = o.getDouble("fps").toFloat().let { if (lowEnd) (it * 0.66f).coerceAtLeast(6f) else it }
            SfStageBackground.Animated(
                atlas = bmp,
                frameW = cellW,
                frameH = cellH,
                cols = cols,
                rows = rows,
                frameCount = o.getInt("frameCount").coerceAtLeast(1),
                fps = fps,
            )
        } else {
            SfStageBackground.Static(bmp)
        }
    }.getOrNull()
}

/**
 * 🆕 (2026-07-25) Contador de FPS del modo pelea (Ajustes → Interfaz → "Mostrar FPS"). Mide los
 * cuadros REALES de pantalla con `withFrameNanos` (no el tick del VM) y promedia cada ~0.5 s.
 * Autocontenido: se descarta con el composable al salir de la pelea.
 */
@Composable
fun SfFpsOverlay(modifier: Modifier = Modifier) {
    var fps by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        var frames = 0
        var accumNs = 0L
        var lastNs = 0L
        while (true) {
            withFrameNanos { ns ->
                if (lastNs != 0L) {
                    accumNs += ns - lastNs
                    frames++
                    if (accumNs >= 500_000_000L) { // promedio cada 0.5 s
                        fps = ((frames * 1_000_000_000.0) / accumNs).toInt()
                        frames = 0
                        accumNs = 0L
                    }
                }
                lastNs = ns
            }
        }
    }
    Text(
        text = "FPS $fps",
        color = Color(0xFF7CFF7C),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            // Mismo motivo que la ✕ de salir: la pelea va a sangre, asi que el inset se aplica al
            // widget y no a la escena. Sin esto, en iOS el contador se pinta sobre la hora.
            .systemBarsPadding()
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Overlay CARGANDO con la fuente arcade POW (sf_hud_pow.png).
 * Se muestra al decodificar atlas/hojas en gama baja (entrada a pelea puede tardar).
 */
@Composable
fun SfLoadingOverlay(
    theme: SfTheme,
    arcadeText: String = "CARGANDO",   // glifos A-Z 0-9 (fuente del HUD)
    fallbackText: String = stringResource(Res.string.sf_loading),
    subtitle: String = stringResource(Res.string.sf_loading_sub),
) {
    val hud = remember(theme) {
        runCatching { PowImagen.deAsset(theme.imagesDir + theme.hudImage) }.getOrNull()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF0101018))
            .clickable(enabled = true, onClick = {}), // bloquea toques
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (hud != null) {
                // Dibuja el título con la fuente del HUD (glifos A-Z)
                Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    val scale = minOf(size.width / SfConstants.SCENE_WIDTH, size.height / 40f)
                    val ctx = SceneCtx(scale, (size.width - SfConstants.SCENE_WIDTH * scale) / 2f, 0f, 0f, 0f)
                    // 🆕 (2026-07-22) tw ya está en unidades de escena (12·sizeMul); centrar sin
                    // dividir entre scale (ese /scale era el que lo descuadraba).
                    val tw = arcadeText.length * 12f * 2.2f
                    drawFontText(ctx, theme, hud, arcadeText, (SfConstants.SCENE_WIDTH - tw) / 2f, 8f, 2.2f)
                }
            } else {
                Text(
                    text = fallbackText,
                    color = Color(0xFFD4AF37),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * Fondo animado: elige el frame según el tiempo de juego (el ping-pong ya viene embebido en
 * el atlas, así que basta un loop simple 0→N-1) y lo pinta con el mismo parallax que el fijo.
 */
internal fun DrawScope.drawAnimatedBackground(
    ctx: SceneCtx,
    anim: SfStageBackground.Animated,
    timeMs: Long,
    framing: SfBgFraming = SfBgFraming(),
    jumpFrac: Float = 0f,
) {
    val frameMs = (1000f / anim.fps).coerceAtLeast(1f)
    val idx = ((timeMs / frameMs).toLong() % anim.frameCount).toInt().coerceIn(0, anim.frameCount - 1)
    val col = idx % anim.cols
    val row = idx / anim.cols
    // 🆕 zoom por escenario: >1 amplía y ANCLA AL PISO (scaledH = SCENE_HEIGHT * zoom).
    val s = (SfConstants.SCENE_HEIGHT / anim.frameH.toFloat()) * framing.zoom
    val scaledW = anim.frameW * s
    val scaledH = anim.frameH * s
    val camSpan = SfConstants.STAGE_WIDTH - SfConstants.SCENE_WIDTH
    val progress = ((ctx.camX - SfConstants.STAGE_PADDING) / camSpan).coerceIn(0f, 1f)
    val offsetX = (scaledW - SfConstants.SCENE_WIDTH).coerceAtLeast(0f) * progress
    // Base de la imagen al fondo de la escena (recorta cielo arriba); offsetY afina.
    // 🆕 Salto: baja la imagen hasta 'headroom' (cielo recortado) → revela lo de arriba.
    val headroom = (scaledH - SfConstants.SCENE_HEIGHT).coerceAtLeast(0f) * ctx.scale
    val dstY = ctx.oy + (SfConstants.SCENE_HEIGHT - scaledH + framing.offsetY) * ctx.scale +
        jumpFrac * headroom
    drawImage(
        image = anim.atlas,
        srcOffset = IntOffset(col * anim.frameW, row * anim.frameH),
        srcSize = IntSize(anim.frameW, anim.frameH),
        dstOffset = IntOffset((ctx.ox - offsetX * ctx.scale).toInt(), dstY.toInt()),
        dstSize = IntSize((scaledW * ctx.scale).toInt(), (scaledH * ctx.scale).toInt()),
        filterQuality = FilterQuality.Low,
    )
}

internal fun DrawScope.drawFullBackground(
    ctx: SceneCtx,
    bg: ImageBitmap,
    framing: SfBgFraming = SfBgFraming(),
    jumpFrac: Float = 0f,
) {
    val s = (SfConstants.SCENE_HEIGHT / bg.height.toFloat()) * framing.zoom
    val scaledW = bg.width * s
    val scaledH = bg.height * s
    val camSpan = SfConstants.STAGE_WIDTH - SfConstants.SCENE_WIDTH
    val progress = ((ctx.camX - SfConstants.STAGE_PADDING) / camSpan).coerceIn(0f, 1f)
    val offsetX = (scaledW - SfConstants.SCENE_WIDTH).coerceAtLeast(0f) * progress
    val headroom = (scaledH - SfConstants.SCENE_HEIGHT).coerceAtLeast(0f) * ctx.scale
    val dstY = ctx.oy + (SfConstants.SCENE_HEIGHT - scaledH + framing.offsetY) * ctx.scale +
        jumpFrac * headroom
    drawImage(
        image = bg,
        srcOffset = IntOffset(0, 0),
        srcSize = IntSize(bg.width, bg.height),
        dstOffset = IntOffset((ctx.ox - offsetX * ctx.scale).toInt(), dstY.toInt()),
        dstSize = IntSize((scaledW * ctx.scale).toInt(), (scaledH * ctx.scale).toInt()),
        filterQuality = FilterQuality.Low, // foto: bilineal se ve mejor que None
    )
}

/** Dibuja un recorte del sheet en coords de ESCENA (sin espejo). */
internal fun DrawScope.drawSprite(ctx: SceneCtx, image: ImageBitmap, src: List<Int>, sceneX: Float, sceneY: Float) {
    drawImage(
        image = image,
        srcOffset = IntOffset(src[0], src[1]),
        srcSize = IntSize(src[2], src[3]),
        dstOffset = IntOffset((ctx.ox + sceneX * ctx.scale).toInt(), (ctx.oy + sceneY * ctx.scale).toInt()),
        dstSize = IntSize((src[2] * ctx.scale).toInt(), (src[3] * ctx.scale).toInt()),
        filterQuality = FilterQuality.None,
    )
}

/** Dibuja un recorte con tamaño destino explícito (texto de ganador). */
internal fun DrawScope.drawSpriteScaled(ctx: SceneCtx, image: ImageBitmap, src: List<Int>, sceneX: Float, sceneY: Float, dstW: Float, dstH: Float) {
    drawImage(
        image = image,
        srcOffset = IntOffset(src[0], src[1]),
        srcSize = IntSize(src[2], src[3]),
        dstOffset = IntOffset((ctx.ox + sceneX * ctx.scale).toInt(), (ctx.oy + sceneY * ctx.scale).toInt()),
        dstSize = IntSize((dstW * ctx.scale).toInt(), (dstH * ctx.scale).toInt()),
        filterQuality = FilterQuality.None,
    )
}

/** Dibuja un sprite anclado (con origen) en coords de MUNDO, espejado por dirección. */
internal fun DrawScope.drawSpriteAnchored(
    ctx: SceneCtx,
    image: ImageBitmap,
    src: List<Int>,
    origin: List<Int>,
    worldX: Float,
    worldY: Float,
    direction: SfDirection,
    shakeX: Float = 0f,
    spriteScale: Float = 1f,   // parche de escala por-frame (p. ej. HURT de peleadores ALPHA)
    silhouette: Boolean = false,
    // 🆕 (2026-07-21) 1f = atlas a resolución completa; 0.5f = atlas submuestreado en gama
    // baja. SOLO afecta al RECORTE (las coordenadas del JSON son de la hoja original); el
    // tamaño de DESTINO no cambia, así que el sprite se ve igual de grande, solo más suave.
    sheetScale: Float = 1f,
) {
    val anchorSx = ctx.ox + (worldX - ctx.camX) * ctx.scale
    val anchorSy = ctx.oy + (worldY - ctx.camY) * ctx.scale
    // El origin (ancla) se escala junto con el sprite → la figura crece/encoge alrededor de sus
    // pies (origin), sin moverse de su punto de mundo.
    val s = ctx.scale * spriteScale
    val dstX = anchorSx - origin[0] * s + shakeX * ctx.scale
    val dstY = anchorSy - origin[1] * s
    val draw: DrawScope.() -> Unit = {
        drawImage(
            image = image,
            srcOffset = IntOffset((src[0] * sheetScale).toInt(), (src[1] * sheetScale).toInt()),
            srcSize = IntSize(
                (src[2] * sheetScale).toInt().coerceAtLeast(1),
                (src[3] * sheetScale).toInt().coerceAtLeast(1),
            ),
            dstOffset = IntOffset(dstX.toInt(), dstY.toInt()),
            dstSize = IntSize((src[2] * s).toInt(), (src[3] * s).toInt()),
            filterQuality = FilterQuality.None,
            colorFilter = if (silhouette) ColorFilter.tint(Color(0xFF15151F)) else null,
        )
    }
    if (direction == SfDirection.LEFT) {
        // Espejo alrededor del ancla (equivale al context.scale(direction,1) del JS)
        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(anchorSx, 0f)) { draw() }
    } else {
        draw()
    }
}

/**
 * Altura de cuerpo OBJETIVO en px de la hoja (contenido opaco). El packer croma fija
 * poses erguidas a ~100 px; si un ataque/especial pinta más grande en la celda 256²
 * (p. ej. auras, brazos), se reescala en draw para NO "crecer" al golpear.
 * El pushbox NO bastaba: muchos JSON repiten push 78 en idle y special con contenido 100→168.
 */
internal const val TARGET_BODY_CONTENT_H = 100f

/** Estados donde el cuerpo DEBE verse más bajo/tumbado: no forzar a 100 px. */
internal fun SfFighterState.keepsNaturalHeight(): Boolean = when (this) {
    SfFighterState.CROUCH, SfFighterState.CROUCH_DOWN, SfFighterState.CROUCH_UP,
    SfFighterState.CROUCH_TURN,
    SfFighterState.KO,
    -> true
    else -> name.startsWith("HURT_") // hurt puede aplastar; hurtScale aparte
}

/**
 * Mide, por frameKey, la altura de píxeles opacos en el recorte src de la hoja.
 * Se calcula UNA vez al cargar el personaje (no por tick).
 */
fun measureFrameContentHeights(
    sheet: ImageBitmap,
    frames: Map<String, SfFrameDef>,
): Map<String, Int> {
    val out = HashMap<String, Int>(frames.size)
    val wBmp = sheet.width
    val hBmp = sheet.height
    val pixels = IntArray(wBmp * hBmp)
    sheet.readPixels(pixels, 0, 0, wBmp, hBmp)
    for ((key, fr) in frames) {
        if (key.startsWith("proj")) continue
        val src = fr.src
        if (src.size < 4) continue
        val x0 = src[0].coerceIn(0, wBmp - 1)
        val y0 = src[1].coerceIn(0, hBmp - 1)
        val x1 = (src[0] + src[2]).coerceIn(x0 + 1, wBmp)
        val y1 = (src[1] + src[3]).coerceIn(y0 + 1, hBmp)
        var minY = y1
        var maxY = y0 - 1
        // Muestreo cada 2 px (suficiente y más barato en gama baja)
        var y = y0
        while (y < y1) {
            var x = x0
            var rowHit = false
            while (x < x1) {
                if ((pixels[y * wBmp + x] ushr 24) > 16) {
                    rowHit = true
                    break
                }
                x += 2
            }
            if (rowHit) {
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            y += 2
        }
        out[key] = if (maxY >= minY) (maxY - minY + 1) else src[3]
    }
    return out
}

internal fun DrawScope.drawFighter(
    ctx: SceneCtx,
    images: Map<String, ImageBitmap>,
    data: SfFighterData,
    f: SfFighter,
    t: Long,
    showHitboxes: Boolean = false,
    contentHeights: Map<String, Int> = emptyMap(),
    silhouette: Boolean = false,
    // 🆕 (2026-07-21) Placeholder ALPHA: dibuja con la hoja de OTRO peleador (el estudiante
    // del mismo género) sin cambiar la identidad lógica del que pelea.
    sheetKeyOverride: String? = null,
    // 🆕 (2026-07-21) Submuestreo del atlas en gama baja (1f = completo, 0.5f = mitad).
    sheetScale: Float = 1f,
) {
    // 🆕 Nunca “desaparecer”: si falta hoja/anim/frame, cae a IDLE-1 o al primer frame disponible.
    val sheetKey = sheetKeyOverride ?: f.id.spriteAsset.substringAfterLast('/')
    val sheet = images[sheetKey]
        ?: images.values.firstOrNull()
        ?: return
    val anim = data.animations[f.state.jsKey]
        ?: data.animations[SfFighterState.IDLE.jsKey]
        ?: data.animations.values.firstOrNull()
        ?: return
    val frameKey = anim[f.animationFrame.coerceIn(0, anim.lastIndex)].frameKey
    val frame = data.frames[frameKey]
        ?: data.frames["idle-1"]
        ?: data.frames.values.firstOrNull()
        ?: return
    // Sacudida al recibir golpe (hurt shake del JS), solo durante el 1er frame de HURT
    val hurtState = f.state.name.startsWith("HURT_")
    val shake = if (hurtState && f.animationFrame == 0) (if ((t / 32) % 2 == 0L) 2f else -2f) else 0f
    val hurtMul = if (hurtState) f.id.hurtScale else 1f
    // 🆕 Poderes/metamorfosis: NO reescalar por contenido (auras grandes → “cambio de skin”
    // o recortes raros). Solo idle/walk/golpes normales se normalizan a ~100 px.
    val isFxPose = f.state in SF_BONUS_POWER_STATES ||
        f.state == SfFighterState.SPECIAL_1_LIGHT ||
        f.state == SfFighterState.SPECIAL_1_MEDIUM ||
        f.state == SfFighterState.SPECIAL_1_HEAVY ||
        f.metamorphosing
    val contentH = contentHeights[frameKey]?.takeIf { it > 0 } ?: frame.src.getOrElse(3) { 100 }
    val bodyMul = when {
        isFxPose || f.state.keepsNaturalHeight() || contentH <= 0 -> 1f
        else -> (TARGET_BODY_CONTENT_H / contentH.toFloat()).coerceIn(0.70f, 1.25f)
    }
    val spriteScale = (hurtMul * bodyMul).coerceIn(0.70f, 1.35f)
    val drawDirection = if (frame.flipX) f.direction.opposite() else f.direction
    // Origen seguro (pies): si el JSON trae basura, anclar al centro-bajo de la celda
    val origin = if (frame.origin.size >= 2 && frame.origin[1] > 0) {
        frame.origin
    } else {
        listOf(frame.src.getOrElse(2) { 128 } / 2, frame.src.getOrElse(3) { 256 } * 7 / 8)
    }
    drawSpriteAnchored(
        ctx, sheet, frame.src, origin, f.x, f.y, drawDirection,
        shakeX = shake, spriteScale = spriteScale, silhouette = silhouette,
        sheetScale = sheetScale,
    )

    // 🆕 HITBOXES (Ajustes → "Mostrar hitboxes"): push/hurt/hit. También se reescalan
    // visualmente con bodyMul para alinear cajas al sprite dibujado.
    if (showHitboxes) {
        val boxScale = bodyMul
        fun scaleBox(b: SfBox): SfBox = SfBox(b.x * boxScale, b.y * boxScale, b.width * boxScale, b.height * boxScale)
        SfBox.fromList(frame.push).takeIf { it.width > 0f }
            ?.let { drawWorldBox(ctx, scaleBox(it).toWorld(f.x, f.y, f.direction), Color.White) }
        frame.hurt?.forEach { row ->
            SfBox.fromList(row).takeIf { it.width > 0f }
                ?.let { drawWorldBox(ctx, scaleBox(it).toWorld(f.x, f.y, f.direction), Color(0xFF29B6F6)) }
        }
        frame.hit?.let { hb ->
            SfBox.fromList(hb).takeIf { it.width > 0f }
                ?.let { drawWorldBox(ctx, scaleBox(it).toWorld(f.x, f.y, f.direction), Color(0xFFEF5350)) }
        }
    }
}

/** Dibuja una SfBox (en coords de MUNDO) como rectángulo hueco en pantalla (hitbox de debug). */
internal fun DrawScope.drawWorldBox(ctx: SceneCtx, box: SfBox, color: Color) {
    val x = ctx.ox + (box.x - ctx.camX) * ctx.scale
    val y = ctx.oy + (box.y - ctx.camY) * ctx.scale
    drawRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(box.width * ctx.scale, box.height * ctx.scale),
        style = Stroke(width = 2f),
    )
}

internal fun DrawScope.drawShadow(ctx: SceneCtx, theme: SfTheme, shadowImg: ImageBitmap, f: SfFighter, bgFile: String?) {
    // Shadow.js: se encoge en el aire; specials/KO tienen escalas propias
    var scaleX = 1.2f
    var scaleY = 1.2f
    var offsetX = 0f
    if (f.y < SfConstants.STAGE_FLOOR) {
        val s = 1.2f - (200f - f.y) / 300f
        scaleX = s; scaleY = s
    } else when (f.state) {
        SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM, SfFighterState.SPECIAL_1_HEAVY -> {
            scaleX = 1.6f; scaleY = 1f; offsetX = 22f * f.direction.sign * -1f
        }
        SfFighterState.KO -> { scaleX = 2.4f; scaleY = 1f }
        else -> Unit
    }

    // 🆕 Sombra sobre el agua / plataforma para Isla de las Muñecas
    val isIslaMunecas = bgFile != null && bgFile.contains("islamunecas")
    if (isIslaMunecas) {
        val platW = 100f
        val platH = 8f
        val platX = ctx.ox + (f.x - ctx.camX - platW / 2f) * ctx.scale
        val platY = ctx.oy + (SfConstants.STAGE_FLOOR - ctx.camY - 2f) * ctx.scale
        drawRoundRect(
            color = Color(0xCC3E2723), // Madera oscura semi-transparente
            topLeft = Offset(platX, platY),
            size = Size(platW * ctx.scale, platH * ctx.scale),
            cornerRadius = CornerRadius(3f * ctx.scale, 3f * ctx.scale)
        )
        drawRoundRect(
            color = Color(0xFF1D0F0B), // Borde madera oscuro
            topLeft = Offset(platX, platY),
            size = Size(platW * ctx.scale, platH * ctx.scale),
            cornerRadius = CornerRadius(3f * ctx.scale, 3f * ctx.scale),
            style = Stroke(width = 1f * ctx.scale)
        )
        // Línea intermedia para simular tablones
        drawLine(
            color = Color(0x441D0F0B),
            start = Offset(platX + 4f * ctx.scale, platY + 4f * ctx.scale),
            end = Offset(platX + (platW - 4f) * ctx.scale, platY + 4f * ctx.scale),
            strokeWidth = 1f * ctx.scale
        )
        
        // Sombra más prolongada
        scaleX *= 1.8f
    }

    val src = theme.shadowFrame.src
    val originX = theme.shadowFrame.origin[0]
    val originY = theme.shadowFrame.origin[1]
    val x = ctx.ox + (f.x - ctx.camX - originX * scaleX - offsetX) * ctx.scale
    val y = ctx.oy + (SfConstants.STAGE_FLOOR - ctx.camY - originY * scaleY) * ctx.scale
    drawImage(
        image = shadowImg,
        srcOffset = IntOffset(src[0], src[1]),
        srcSize = IntSize(src[2], src[3]),
        dstOffset = IntOffset(x.toInt(), y.toInt()),
        dstSize = IntSize((src[2] * scaleX * ctx.scale).toInt(), (src[3] * scaleY * ctx.scale).toInt()),
        alpha = 0.5f,
        filterQuality = FilterQuality.None,
    )
}

internal fun DrawScope.drawHud(ctx: SceneCtx, theme: SfTheme, hud: ImageBitmap, state: SfSceneState) {
    // Barras de vida (la derecha espejada)
    drawSprite(ctx, hud, theme.healthBar, 31f, 20f)
    drawHudMirrored(ctx, hud, theme.healthBar, 353f, 20f)

    // Daño en rojo sobre la barra
    val maxHp = SfConstants.HEALTH_MAX_HIT_POINTS.toFloat()
    val damageColor = Color(0xFFF30000)
    // 🆕 Con el HP MOSTRADO (displayHp*, roll-up gradual del VM), no con el hitPoints real:
    // la barra "drena" al recibir daño en vez de saltar de golpe
    val leftDamage = (144f * (maxHp - state.displayHp0) / maxHp)
    if (leftDamage > 0f) {
        drawRect(
            color = damageColor,
            topLeft = Offset(ctx.ox + 32f * ctx.scale, ctx.oy + 21f * ctx.scale),
            size = Size(leftDamage * ctx.scale, 9f * ctx.scale),
        )
    }
    val rightDamage = (144f * (maxHp - state.displayHp1) / maxHp)
    if (rightDamage > 0f) {
        val rx = 208f + (144f * state.displayHp1 / maxHp)
        drawRect(
            color = damageColor,
            topLeft = Offset(ctx.ox + rx * ctx.scale, ctx.oy + 21f * ctx.scale),
            size = Size(rightDamage * ctx.scale, 9f * ctx.scale),
        )
    }

    // Icono KO (parpadea cuando alguien está crítico)
    val koSrc = if (state.koFlash) theme.koBlack else theme.koWhite
    drawSprite(ctx, hud, koSrc, 176f, if (state.koFlash) 17f else 18f)

    // Timer (2 dígitos, parpadea al final)
    val digits = if (state.timeFlashing) theme.timeDigitsFlash else theme.timeDigits
    val timeStr = state.displayTime.toString().padStart(2, '0')
    drawSprite(ctx, hud, digits.digit(timeStr[0] - '0'), 178f, 33f)
    drawSprite(ctx, hud, digits.digit(timeStr[1] - '0'), 194f, 33f)

    // Nombres con la FUENTE arcade (cualquier peleador; el derecho alineado a la derecha)
    drawFontText(ctx, theme, hud, state.player.id.shortName, 32f, 33f, 0.9f)
    val cpuName = state.cpu.id.shortName
    drawFontText(ctx, theme, hud, cpuName, 350f - cpuName.length * 12f * 0.9f, 33f, 0.9f)

    // 🆕 (2026-07-25) GRADO de la ronda anterior (PERFECT/COMBO/SUPER/TIME) bajo la barra del
    // GANADOR, solo durante el intro de la ronda siguiente (estilo SF III). "" = sin etiqueta.
    if (state.showRoundIntro && state.roundResultLabel.isNotEmpty() && state.roundResultWinnerIdx in 0..1) {
        val label = state.roundResultLabel
        val labelScale = 0.7f
        if (state.roundResultWinnerIdx == 0) {
            drawFontText(ctx, theme, hud, label, 32f, 44f, labelScale)
        } else {
            drawFontText(ctx, theme, hud, label, 350f - label.length * 12f * labelScale, 44f, labelScale)
        }
    }

    // Marcadores P1 / P2
    drawFontText(ctx, theme, hud, "P1", 4f, 1f)
    drawScoreNumber(ctx, theme, hud, state.playerScore, 45f)
    drawFontText(ctx, theme, hud, "P2", 269f, 1f)
    drawScoreNumber(ctx, theme, hud, state.cpuScore, 309f)

    // 🆕 RONDAS GANADAS (mejor de 3): cuadritos dorados bajo el nombre de cada lado
    val roundMark = Color(0xFFD4AF37)
    for (i in 0 until state.playerRoundWins.coerceAtMost(2)) {
        drawRect(
            color = roundMark,
            topLeft = Offset(ctx.ox + (32f + i * 11f) * ctx.scale, ctx.oy + 45f * ctx.scale),
            size = Size(7f * ctx.scale, 7f * ctx.scale),
        )
    }
    for (i in 0 until state.cpuRoundWins.coerceAtMost(2)) {
        drawRect(
            color = roundMark,
            topLeft = Offset(ctx.ox + (346f - i * 11f) * ctx.scale, ctx.oy + 45f * ctx.scale),
            size = Size(7f * ctx.scale, 7f * ctx.scale),
        )
    }
}

internal fun DrawScope.drawHudMirrored(ctx: SceneCtx, hud: ImageBitmap, src: List<Int>, sceneX: Float, sceneY: Float) {
    val anchorSx = ctx.ox + sceneX * ctx.scale
    scale(scaleX = -1f, scaleY = 1f, pivot = Offset(anchorSx, 0f)) {
        drawImage(
            image = hud,
            srcOffset = IntOffset(src[0], src[1]),
            srcSize = IntSize(src[2], src[3]),
            dstOffset = IntOffset(anchorSx.toInt(), (ctx.oy + sceneY * ctx.scale).toInt()),
            dstSize = IntSize((src[2] * ctx.scale).toInt(), (src[3] * ctx.scale).toInt()),
            filterQuality = FilterQuality.None,
        )
    }
}

/**
 * Texto con la FUENTE arcade del HUD (recortes A-Z/0-9 de `theme.letterFont`).
 * Avance fijo de 12 px por carácter (el espacio y los caracteres sin glifo dejan hueco).
 * Es la fuente "oficial" del modo: tags de nombre, marcadores y "<X> WINS".
 */
internal fun DrawScope.drawFontText(
    ctx: SceneCtx,
    theme: SfTheme,
    hud: ImageBitmap,
    text: String,
    x: Float,
    y: Float,
    sizeMul: Float = 1f,
) {
    var cx = x
    text.uppercase().forEach { ch ->
        theme.letterFont[ch]?.let { src ->
            drawSpriteScaled(ctx, hud, src, cx, y, src[2] * sizeMul, src[3] * sizeMul)
        }
        cx += 12f * sizeMul
    }
}

internal fun DrawScope.drawScoreNumber(ctx: SceneCtx, theme: SfTheme, hud: ImageBitmap, score: Int, x: Float) {
    val str = score.toString()
    val padding = 6 * 12f - str.length * 12f
    drawFontText(ctx, theme, hud, str, x + padding, 1f)
}

