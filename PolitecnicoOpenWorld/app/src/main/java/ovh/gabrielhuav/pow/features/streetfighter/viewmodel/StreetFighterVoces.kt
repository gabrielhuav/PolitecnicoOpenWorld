package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_HURT_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_BONUS_POWER_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_BLOCK_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAnimation
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDamage
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfPhysics
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStateMachine
import ovh.gabrielhuav.pow.domain.models.streetfighter.sfUsableBonusPowerCount
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_DOWNED_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_NEW_ATTACK_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_NEW_MOVE_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_PARRY_STATES
import ovh.gabrielhuav.pow.features.streetfighter.data.SfCombo
import ovh.gabrielhuav.pow.features.streetfighter.data.SfComboAction
import ovh.gabrielhuav.pow.features.streetfighter.data.SfCombos
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfArcadeLadder
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackType
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfBox
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterData
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireball
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireballState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfHitSplash
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfHurtArea
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfInput
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfProjectileEvent
import ovh.gabrielhuav.pow.domain.models.streetfighter.bonusPowerIndex
import ovh.gabrielhuav.pow.domain.models.streetfighter.sfBonusPowerState
import ovh.gabrielhuav.pow.BuildConfig
import ovh.gabrielhuav.pow.data.auth.AuthManager
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.data.repository.SfArcadeRepository
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStageCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import ovh.gabrielhuav.pow.features.streetfighter.data.SfBtClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfFrameCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SfLanClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfLanDiscovery
import ovh.gabrielhuav.pow.features.streetfighter.data.SfLanGame
import ovh.gabrielhuav.pow.features.streetfighter.data.SfMatchClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetFireball
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetMsg
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetTransport
import ovh.gabrielhuav.pow.features.streetfighter.data.SfWebRtcClient
import ovh.gabrielhuav.pow.features.streetfighter.data.isSfLowEnd
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import kotlin.math.abs
import kotlin.random.Random

// ────────────────────────────────────────────────────────────────────────────
// PARCIAL de StreetFighterViewModel: 🔊 VOCES y SUBTÍTULOS (packs por peleador, cooldowns, escaparate de audio)
//
// Extraído de StreetFighterViewModel.kt en el refactor de tamaño de la Fase 5. Aquí vive CUÁNDO
// suena cada voz: intro, ataque, dolor, poder, victoria y derrota, con sus cooldowns y el
// subtítulo asociado.
// ⚠️ Los PACKS (sfVoicePacks, voicePhrases, los cooldowns por índice…) se quedan como campos en
// la clase: aquí solo está la lógica de emisión.
// ⚠️ En MULTIJUGADOR las voces VIAJAN por red (queueNetAudio) porque los packs eligen con
// .random(): si cada teléfono sorteara por su cuenta, cada jugador oiría un clip distinto del
// mismo evento. Ver _SESION_ACTUAL y el gotcha del audio en red.
//
// ⚠️ Son EXTENSIONES del VM, no miembros: el estado sigue viviendo en la clase. NO recrees
// estas funciones como miembros — quedarían gemelas y ganaría el miembro EN SILENCIO
// (ver 09 §0, el gotcha miembro-vs-extensión que ya costó caro en este repo).
// ────────────────────────────────────────────────────────────────────────────

/**
 * SFX del especial/bonus por peleador: `special_<sf_fighter_id_lower>.ogg`
 * (pipeline tools/scrape_sf_voices.py + pack_sf_character_sfx.py).
 * La View hace fallback a `hadouken` si falta el asset.
 */
internal fun StreetFighterViewModel.specialSfxKey(id: SfFighterId): String = "special_${id.name.lowercase()}"

/** Frases special (ES/EN + HUD) de los 21 peleadores. Lazy desde assets. */

internal fun StreetFighterViewModel.emitVoiceClip(name: String): Boolean {
    if (!sfAssetExists("STREETFIGHTER/SOUNDS/$name.ogg")) return false
    _soundEvents.tryEmit(name)
    // 🆕 (2026-07-26) Si es la voz de MI peleador en una pelea en red, se encola para que
    // el rival reproduzca EL MISMO clip (ver pendingNetAudio).
    if (netAudioCapture) queueNetAudio(name)
    return true
}

/**
 * 🆕 (2026-07-26) Corre [block] capturando las voces que emita, para mandarlas por red.
 * Solo captura las del peleador LOCAL (índice 0) y solo en pelea en red: la voz del rival
 * la manda ÉL, y en un jugador no hay a quién mandársela. Reentrante-seguro (restaura el
 * valor previo) por si un emit* llama a otro (emitWinVoice → emitSpecialVoice).
 */
// ⚡ GAMA BAJA: `inline` a propósito. Se llama desde `changeState`, que corre varias veces por
// segundo y por peleador; sin inline cada llamada ASIGNA un lambda (captura nf/idx/now) y eso
// es basura para el GC en un teléfono lento. Inline = cero asignaciones.
internal inline fun StreetFighterViewModel.withNetAudioCapture(idx: Int, block: () -> Unit) {
    if (idx != 0 || !inOnlineFight) {
        block()
        return
    }
    val previous = netAudioCapture
    netAudioCapture = true
    try {
        block()
    } finally {
        netAudioCapture = previous
    }
}

/** Encola una clave de voz para el próximo PLAYER_STATE (con tope, por si no se envía). */
internal fun StreetFighterViewModel.queueNetAudio(name: String) {
    if (pendingNetAudio.size >= StreetFighterViewModel.NET_AUDIO_MAX_PER_SNAPSHOT) return
    pendingNetAudio.add(name)
}

/** Solo A-Z/0-9 para la fuente pixel del HUD (acentos/ñ/puntuación → simplificados). */
internal fun StreetFighterViewModel.sfHudSanitize(s: String): String = s.uppercase(java.util.Locale.ROOT)
    .replace('Á', 'A').replace('É', 'E').replace('Í', 'I').replace('Ó', 'O').replace('Ú', 'U')
    .replace('Ñ', 'N').replace('Ü', 'U')
    .replace(Regex("[^A-Z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

/** Fija el subtítulo (frase) por un tiempo proporcional a su longitud. */
// 🆕 (2026-07-22) SUBTÍTULOS: voice_phrases.json curado (64 `es` + track `en`). El delimitador
// '|' separa tramos sincronizados con la voz; ver setVoiceSubtitle.
// 🆕 (2026-07-25) Ahora son OPTATIVOS desde Ajustes → Interfaz (default APAGADO, decisión del
// dueño). Se lee una vez al crear el VM (se reentra al modo para aplicar un cambio).

internal fun StreetFighterViewModel.setVoiceSubtitle(phrase: String, now: Long) {
    if (!voiceSubtitlesEnabled) return
    // 🆕 (2026-07-22) TRAMOS '|' SECUENCIADOS: se sanea CADA tramo por separado (sfHudSanitize
    // borraría el '|') y se re-une con '|'. La Screen muestra UN tramo A LA VEZ, avanzando con
    // el tiempo: la ventana [start,until] se reparte por igual entre los tramos (sincronía con
    // la voz). La duración total ~ largo del texto, con un mínimo legible por tramo.
    val segments = phrase.split('|')
        .map { sfHudSanitize(it) }
        .filter { it.isNotBlank() }
    if (segments.isEmpty()) return
    val hud = segments.joinToString("|")
    val perSegment = 900L // mínimo legible por tramo (ms)
    val total = maxOf(1600L + hud.length * 70L, segments.size * perSegment).coerceIn(2000L, 9000L)
    _state.update {
        it.copy(
            specialSubtitleHud = hud,
            specialSubtitleStartMs = now,
            specialSubtitleUntilMs = now + total,
        )
    }
}

// 🆕 (2026-07-20) Catálogo de frases POR CLIP (voice_phrases.json): el "lugar único"
// donde el dueño cura la frase de cada audio. Si un clip tiene frase curada, MANDA
// sobre la inline de StreetFighterViewModel.SfVoiceLine. Se muestra en el HUD desde el 2026-07-22.

internal fun StreetFighterViewModel.emitVoiceLines(lines: List<StreetFighterViewModel.SfVoiceLine>, now: Long): Boolean {
    if (lines.isEmpty()) return false
    val line = lines.random()
    if (!emitVoiceClip(line.file)) return false
    val phrase = voicePhrases[line.file]?.textForLang(java.util.Locale.getDefault().language)
        ?: line.phrase
    if (phrase.isNotBlank()) setVoiceSubtitle(phrase, now)
    return true
}

/** Voz de VICTORIA del peleadór (pack WIN; si no tiene, su voz de special). */
internal fun StreetFighterViewModel.emitWinVoice(id: SfFighterId, now: Long) {
    val p = sfVoicePacks[id]
    if (p != null && emitVoiceLines(p.win, now)) return
    emitSpecialVoice(id, now)
}

/** 🆕 Voz de DERROTA del peleadór (pack LOSS; si no tiene, silencio — no hay fallback). */
internal fun StreetFighterViewModel.emitLossVoice(id: SfFighterId, now: Long) {
    val p = sfVoicePacks[id] ?: return
    emitVoiceLines(p.loss, now)
}

// Anti-spam de voces por índice (no repetir en menos del intervalo).

internal fun StreetFighterViewModel.emitHurtVoice(id: SfFighterId, idx: Int, hitPoints: Int, now: Long) {
    val i = idx.coerceIn(0, 1)
    val pack = sfVoicePacks[id] ?: return

    // 25% de 200 HP = 50 HP. Prioriza lowHp si no se ha disparado aún.
    if (hitPoints <= 50 && pack.lowHp.isNotEmpty() && !lowHpVoiceTriggered[i]) {
        if (emitVoiceLines(pack.lowHp, now)) {
            lowHpVoiceTriggered[i] = true
            lastHurtVoiceMs[i] = now
            return
        }
    }

    val lines = pack.hurt
    if (lines.isEmpty()) return
    if (now - lastHurtVoiceMs[i] < hurtVoiceCooldownMs) return
    if (emitVoiceLines(lines, now)) lastHurtVoiceMs[i] = now
}

/** Voz de ATAQUE (pack ATTACK, grito + frase al golpear) con cooldown por índice.
 * Si el peleadór NO tiene voz de ataque propia y es HOMBRE, a veces suena el grito por
 * defecto (enemigo = más seguido; jugador = raro). */
internal fun StreetFighterViewModel.emitAttackVoice(id: SfFighterId, idx: Int, now: Long) {
    val i = idx.coerceIn(0, 1)
    val isPoliceMale = (id == SfFighterId.POLICIA_CDMX_HOMBRE || id == SfFighterId.POLICIA_GRANADERO_HOMBRE || id == SfFighterId.GRANADERO)
    if (isPoliceMale) {
        // "special_pol_h_attack" solo debe sonar ciertas veces (30% de chance) y no se repite seguido
        val rolledPhrase = Random.nextFloat() < 0.30f
        val elapsed = now - lastAttackVoiceMs[i]
        if (rolledPhrase && elapsed >= attackVoiceCooldownMs) {
            val lines = sfVoicePacks[id]?.attack.orEmpty()
            if (emitVoiceLines(lines, now)) {
                lastAttackVoiceMs[i] = now
                return
            }
        }
        // Fallback por defecto: grito genérico que sí puede sonar repetido (no bloquea/actualiza lastAttackVoiceMs)
        emitVoiceClip(maleGruntClip)
        return
    }

    // Si tiene sus propios especiales/clips de ataque (como Charro Negro, Llorona, Señor de la Tienda, etc.),
    // SIEMPRE los usa (se sobreponen a los genéricos) y no se bloquean por el cooldown largo de frase.
    val lines = sfVoicePacks[id]?.attack.orEmpty()
    if (lines.isNotEmpty()) {
        if (emitVoiceLines(lines, now)) {
            lastAttackVoiceMs[i] = now
        }
        return
    }

    if (now - lastAttackVoiceMs[i] < attackVoiceCooldownMs) return
    // 🆕 (2026-07-18u) Grito masculino por defecto (solo hombres sin voz de ataque propia).
    if (id in sfMaleFighters) {
        val chance = if (i == 0) 0.10f else 0.35f // jugador raro / enemigo más seguido
        if (Random.nextFloat() < chance && emitVoiceClip(maleGruntClip)) {
            lastAttackVoiceMs[i] = now
        }
    }
}

/** Voz de PRESENTACIÓN al arrancar la pelea (solo si el pack tiene intro). */
internal fun StreetFighterViewModel.emitIntroVoice(id: SfFighterId, now: Long) {
    emitVoiceLines(sfVoicePacks[id]?.intro.orEmpty(), now)
}

/** Emite SFX + subtítulo del special (pack POWER si lo tiene; si no, su special_<id> + frase JSON). */
internal fun StreetFighterViewModel.emitSpecialVoice(id: SfFighterId, now: Long) {
    val p = sfVoicePacks[id]
    if (p != null && emitVoiceLines(p.power, now)) return
    _soundEvents.tryEmit(specialSfxKey(id))
    // 🆕 (2026-07-26) El grito del special también viaja: aunque `specialSfxKey` sea
    // derivable del id, se emite desde 4 transiciones distintas y capturarlo aquí evita
    // duplicar esa lógica en el receptor (y que suene dos veces si me equivoco).
    if (netAudioCapture) queueNetAudio(specialSfxKey(id))
    val phrase = specialPhrases[id] ?: return
    // 🆕 (2026-07-21g) Usaba phraseEs FIJO: la traducción `phrase_en` del catálogo no se
    // mostraba nunca, ni con el juego en inglés. Mismo criterio que emitVoiceLines.
    setVoiceSubtitle(phrase.textForLang(java.util.Locale.getDefault().language), now)
}

/** Reproduce otra vez la voz completa del peleador visible en el showcase. */
fun StreetFighterViewModel.replayCurrentShowcaseAudio() {
    if (!gauntletActive || !showcaseMode) return
    emitSpecialVoice(_state.value.player.id, gameNow)
}

/** Recorre las 21 voces completas respetando la duración real de cada OGG. */
fun StreetFighterViewModel.startAudioShowcase() {
    if (audioShowcaseJob?.isActive == true) return
    val fighters = SfArcadeLadder.ALL_PARTICIPANTS.filter { it in specialPhrases }
    _soundEvents.tryEmit(SF_STOP_SPECIALS_EVENT)
    audioShowcaseJob = viewModelScope.launch {
        try {
            fighters.forEachIndexed { index, id ->
                val phrase = specialPhrases.getValue(id)
                _state.update {
                    it.copy(
                        audioShowcaseRunning = true,
                        audioShowcaseIndex = index + 1,
                        audioShowcaseTotal = fighters.size,
                        audioShowcaseFighter = id,
                        audioShowcasePhrase = phrase.phraseEs,
                    )
                }
                _soundEvents.emit(specialSfxKey(id))
                delay(specialAudioDurationMs(id) + AUDIO_SHOWCASE_GAP_MS)
            }
        } finally {
            _state.update {
                it.copy(
                    audioShowcaseRunning = false,
                    audioShowcaseIndex = 0,
                    audioShowcaseTotal = 0,
                    audioShowcaseFighter = null,
                    audioShowcasePhrase = "",
                )
            }
        }
    }
}

/** Detiene el recorrido auditivo y también el MediaPlayer que esté hablando. */
fun StreetFighterViewModel.stopAudioShowcase() {
    audioShowcaseJob?.cancel()
    audioShowcaseJob = null
    _soundEvents.tryEmit(SF_STOP_SPECIALS_EVENT)
}

internal fun StreetFighterViewModel.specialAudioDurationMs(id: SfFighterId): Long {
    val fallbackMs = specialPhrases[id]?.subtitleMs ?: AUDIO_SHOWCASE_FALLBACK_MS
    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            appContext.assets.openFd("STREETFIGHTER/SOUNDS/${specialSfxKey(id)}.ogg").use { fd ->
                retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: fallbackMs
        } finally {
            retriever.release()
        }
    }.getOrDefault(fallbackMs)
}

// 🆕 Progreso del ARCADE (guardado LOCAL). Define qué peleadores/mapas están desbloqueados.

