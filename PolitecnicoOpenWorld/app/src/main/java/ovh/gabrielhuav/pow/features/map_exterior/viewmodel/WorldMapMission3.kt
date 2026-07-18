package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog
import ovh.gabrielhuav.pow.domain.models.campaign.mission3.Mission3
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// MODO HISTORIA · MISIÓN 3 "Regreso a la ENCB" (extensiones del WorldMapViewModel, sin gemelo).
//
// Fases (mission3Phase, persistida en GameSaveData.mission3Phase):
//   1 VIAJE       → ve a la ENCB (🎯). Al acercarte (~100 m) se ARMA el cordón → fase 2.
//   2 INFILTRACIÓN→ 6 granaderos patrullan un ANILLO alrededor de la ENCB; 3 paparazzi
//                   merodean afuera (escenografía + burbujas). SIGILO: a <~21 m de un granadero
//                   por >2.5 s te DETECTAN → MISIÓN FALLIDA. Cruza el cordón y llega al centro
//                   (~15 m) → entras al interior (mission3EnterEncb=true; navega la View) → fase 3.
//   3 ASALTO      → INTERIOR: cadena ENCB con ZOMBIS; recuperar la EVIDENCIA 🧪 en encb_lab1.
//                   La completa completeMission3Evidence() (callback desde ZombieGameScreen).
//   4 DONE        → recompensa: PRIMERA ARMA DE FUEGO (hasFirearm=true → modo RANGED en campaña).
//
// Igual que la Misión 2: NPCs propios en mission3Npcs (merge en uiState.npcs), ticks idempotentes
// (re-spawnean actores tras CARGAR partida), y "activa" = fase en curso Y objetivo m3_* SEGUIDO
// (registro de misiones). NOTA render: los "granaderos" usan NpcType.POLICE_COP (sprite 👮) —
// la skin GRANADERO es de interiores; la ruta de render premade en exterior sigue pendiente.
// ─────────────────────────────────────────────────────────────────────────────

private fun m3Dist(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val dLat = aLat - bLat
    val dLon = aLon - bLon
    return sqrt(dLat * dLat + dLon * dLon)
}

// ── BROTE junto a la ENCB (fase 2): ZONA DE GUERRA — granaderos/policías conteniendo zombis, que
//    además pueden CONVERTIR a Prankedy. Solo aparece MUY cerca de la ENCB (o en el interior). ──
private const val M3_BROTE_TRIGGER_DEG = 0.00035      // ~38 m: tan cerca hay que estar para armar el brote
private const val M3_ZOMBIE_COUNT = 5                 // zombis en la refriega (arte "estudiante zombi")
private const val M3_ZOMBIE_RING_DEG = 0.0003         // ~33 m: dónde nacen alrededor de la ENCB
private const val M3_ZOMBIE_SPEED = 0.0000023         // paso por tick (similar al brote de la M2)
private const val M3_ZOMBIE_CONTACT_DEG = 0.00006     // ~7 m: contacto zombi↔objetivo
private const val M3_CONVERT_MS = 2500L               // contacto sostenido con Prankedy → conversión
private const val M3_ZOMBIE_DAMAGE = 14f              // daño por contacto al jugador
private const val M3_ZOMBIE_HIT_COOLDOWN_MS = 1200L   // entre golpes de contacto al jugador
private const val M3_PRANKEDY_ZOMBIE_HP = 999999f     // "inmortal": no muere con el combate normal
// Contención: policías que PELEAN con los zombis (dan el ambiente de apocalipsis).
private const val M3_CONTAIN_COP_COUNT = 4            // policías de contención (además del cordón de sigilo)
private const val M3_CONTAIN_RING_DEG = 0.00022       // ~24 m: dónde se despliegan
private const val M3_CONTAIN_SPEED = 0.0000026        // avanzan hacia el zombi más cercano
private const val M3_CONTAIN_KILL_DEG = 0.00007       // a esta distancia el policía "somete" al zombi
private const val M3_CONTAIN_SUBDUE_CHANCE = 0.06     // prob./tick de que un zombi sometido reaparezca en el anillo (batalla perpetua)

internal fun WorldMapViewModel.isMission3StoryActive(): Boolean =
    inCampaign && mission3Phase >= Mission3.PHASE_TRAVEL && mission3Phase <= Mission3.PHASE_ASSAULT &&
        _uiState.value.currentObjective?.id?.startsWith(Mission3.OBJECTIVE_ID_PREFIX) == true

// ── ARRANQUE (desde el REGISTRO DE MISIONES). Requiere Misión 2 completada (lo valida el selector). ──
internal fun WorldMapViewModel.startMission3Story() {
    clearMission3Story()
    mission3Phase = Mission3.PHASE_TRAVEL
    setCampaignObjective(MissionCatalog.M3_IR_ENCB)
    _uiState.update { it.copy(
        interactionPrompt = "🚧 La ENCB está en CUARENTENA. Vuelve allá y descubre qué esconden"
    ) }
    android.util.Log.d("POW_DBG", "MISIÓN 3: seguida desde el registro (fase VIAJE)")
}

// ── REANUDAR (registro): re-fija el objetivo de la fase actual sin reiniciar. ──
internal fun WorldMapViewModel.resumeMission3Objective() {
    val obj = when (mission3Phase) {
        Mission3.PHASE_TRAVEL -> MissionCatalog.M3_IR_ENCB
        Mission3.PHASE_INFILTRATE -> MissionCatalog.M3_INFILTRARSE
        Mission3.PHASE_ASSAULT -> MissionCatalog.M3_RECUPERAR_EVIDENCIA
        else -> return
    }
    setCampaignObjective(obj)
}

// ── TICK PRINCIPAL (game loop MIEMBRO, cuando isMission3StoryActive()) ──
internal fun WorldMapViewModel.runMission3StoryTick(playerLoc: GeoPoint) {
    if (currentInteriorRoomId != null) return   // dentro de un interior: la fase 3 corre allá
    val now = System.currentTimeMillis()
    when (mission3Phase) {
        Mission3.PHASE_TRAVEL -> {
            // Al ACERCARTE a la ENCB se arma el cordón y arranca la infiltración.
            if (m3Dist(playerLoc.latitude, playerLoc.longitude, Mission3.ENCB_LAT, Mission3.ENCB_LON)
                <= Mission3.APPROACH_DEG) {
                mission3Phase = Mission3.PHASE_INFILTRATE
                mission3DetectSinceMs = 0L
                setCampaignObjective(MissionCatalog.M3_INFILTRARSE)
                _uiState.update { it.copy(
                    interactionPrompt = "🚧 Cordón de granaderos: NO dejes que te vean. Llega a la entrada"
                ) }
            }
        }
        Mission3.PHASE_INFILTRATE -> tickM3Infiltrate(playerLoc, now)
        Mission3.PHASE_ASSAULT -> {
            // La acción ocurre en el INTERIOR. Si el jugador SALIÓ sin la evidencia, puede
            // RE-ENTRAR volviendo al centro de la ENCB (sin re-hacer el sigilo). HISTÉRESIS:
            // primero hay que ALEJARSE (>2× ENTER) — evita el bucle de navegación en la puerta.
            val d = m3Dist(playerLoc.latitude, playerLoc.longitude, Mission3.ENCB_LAT, Mission3.ENCB_LON)
            if (d > Mission3.ENTER_DEG * 2) mission3ReentryArmed = true
            if (mission3ReentryArmed && !_uiState.value.mission3EnterEncb && d <= Mission3.ENTER_DEG) {
                mission3ReentryArmed = false
                _uiState.update { it.copy(mission3EnterEncb = true) }
            }
        }
    }
    updateNpcsState()
}

// ── FASE 2 · INFILTRACIÓN: cordón de granaderos + paparazzi + sigilo ──
private fun WorldMapViewModel.tickM3Infiltrate(playerLoc: GeoPoint, now: Long) {
    // Spawn perezoso e idempotente (también re-arma tras CARGAR partida en esta fase).
    if (mission3Npcs.isEmpty()) {
        for (i in 0 until Mission3.CORDON_COUNT) {
            val ang = 2.0 * Math.PI * i / Mission3.CORDON_COUNT
            val id = "M3_GRANADERO_$i"
            mission3Npcs[id] = Npc(
                id = id,
                type = NpcType.POLICE_COP,
                location = GeoPoint(
                    Mission3.ENCB_LAT + sin(ang) * Mission3.CORDON_RING_DEG,
                    Mission3.ENCB_LON + cos(ang) * Mission3.CORDON_RING_DEG
                ),
                speed = Mission3.CORDON_SPEED,
                isRemote = false,
                isMoving = true,
                policeDisembarked = true,
                policeCanShoot = false
            )
        }
        for (i in 0 until Mission3.PAPARAZZI_COUNT) {
            val ang = 2.0 * Math.PI * i / Mission3.PAPARAZZI_COUNT + 0.5
            val id = "M3_PAPARAZZI_$i"
            mission3Npcs[id] = Npc(
                id = id,
                type = NpcType.PERSON,
                location = GeoPoint(
                    Mission3.ENCB_LAT + sin(ang) * Mission3.PAPARAZZI_RING_DEG,
                    Mission3.ENCB_LON + cos(ang) * Mission3.PAPARAZZI_RING_DEG
                ),
                speed = 0.0,
                isRemote = false,
                isMoving = false,
                visualConfig = m3PaparazziVisual()
            )
        }
    }

    // Prankedy te ESCOLTA durante toda la infiltración. El BROTE (zombis + contención policial)
    // solo aparece cuando llegas MUY cerca de la ENCB (zona de guerra); una vez armado, corre su tick.
    ensureM3PrankedyEscort(playerLoc, now)
    armM3BroteIfClose(playerLoc)
    if (mission3BroteArmed) tickM3Brote(playerLoc, now)

    // PATRULLA del cordón: cada granadero orbita su PUESTO del anillo (arco corto, determinista
    // por cubeta de tiempo — mismo truco sin estado que la Misión 2). Los paparazzi "reportean"
    // (burbuja 💬 intermitente), quietos.
    var nearest = Double.MAX_VALUE
    val bucket = now / 5000L
    for (npc in mission3Npcs.values.toList()) {
        if (npc.id.startsWith("M3_GRANADERO_")) {
            val i = npc.id.removePrefix("M3_GRANADERO_").toIntOrNull() ?: 0
            val baseAng = 2.0 * Math.PI * i / Mission3.CORDON_COUNT
            val rnd = java.util.Random(npc.id.hashCode() * 31L + bucket)
            val ang = baseAng + (rnd.nextDouble() - 0.5) * 0.9   // arco ±~26° alrededor del puesto
            val tLat = Mission3.ENCB_LAT + sin(ang) * (Mission3.CORDON_RING_DEG + (rnd.nextDouble() - 0.5) * Mission3.CORDON_PATROL_DEG)
            val tLon = Mission3.ENCB_LON + cos(ang) * (Mission3.CORDON_RING_DEG + (rnd.nextDouble() - 0.5) * Mission3.CORDON_PATROL_DEG)
            val a = kotlin.math.atan2(tLat - npc.location.latitude, tLon - npc.location.longitude)
            val arrived = m3Dist(npc.location.latitude, npc.location.longitude, tLat, tLon) < 0.00004
            val moved = if (arrived) npc.location else GeoPoint(
                npc.location.latitude + sin(a) * Mission3.CORDON_SPEED,
                npc.location.longitude + cos(a) * Mission3.CORDON_SPEED
            )
            mission3Npcs[npc.id] = npc.copy(location = moved, isMoving = !arrived, facingRight = cos(a) >= 0)
            val d = m3Dist(playerLoc.latitude, playerLoc.longitude, moved.latitude, moved.longitude)
            if (d < nearest) nearest = d
        } else if (npc.id.startsWith("M3_PAPARAZZI_")) {
            // Burbuja intermitente: "cazando la nota" afuera del cordón.
            if ((now / 4000L + npc.id.hashCode()) % 3L == 0L && npc.talkingUntil < now) {
                mission3Npcs[npc.id] = npc.copy(talkingUntil = now + 1200)
            }
        }
    }

    // SIGILO: ¿un granadero te tiene demasiado cerca demasiado tiempo?
    if (nearest < Mission3.DETECT_DEG) {
        if (mission3DetectSinceMs == 0L) mission3DetectSinceMs = now
        _uiState.update { it.copy(interactionPrompt = "⚠️ ¡Un granadero te está viendo! ESCÓNDETE") }
        if (now - mission3DetectSinceMs > Mission3.DETECT_MS) {
            android.util.Log.d("POW_DBG", "MISIÓN 3: DETECTADO por el cordón → misión fallida")
            mission3Npcs.clear()
            _uiState.update { it.copy(showMissionFailed = true) }
            return
        }
    } else {
        mission3DetectSinceMs = 0L
    }

    // ENTRADA: cruzaste el cordón y llegaste al centro de la ENCB → asalto interior.
    if (m3Dist(playerLoc.latitude, playerLoc.longitude, Mission3.ENCB_LAT, Mission3.ENCB_LON)
        <= Mission3.ENTER_DEG) {
        mission3Phase = Mission3.PHASE_ASSAULT
        mission3Npcs.clear()
        stopM3PrankedyEscort()   // Prankedy (o su zombi) NO entra al interior: se retira aquí
        setCampaignObjective(MissionCatalog.M3_RECUPERAR_EVIDENCIA)
        soundManager.playMisionCumplida()   // jingle: superaste el cordón
        _uiState.update { it.copy(
            mission3EnterEncb = true,
            interactionPrompt = "🧟 Dentro hay INFECTADOS. Recupera la evidencia del laboratorio"
        ) }
        android.util.Log.d("POW_DBG", "MISIÓN 3: cordón superado → ASALTO interior ENCB")
    }
}

// La View consumió la navegación al interior (evita re-disparar al recomponer).
internal fun WorldMapViewModel.consumeMission3EnterEncb() {
    _uiState.update { it.copy(mission3EnterEncb = false) }
}

// ── FASE 3 · EVIDENCIA: la completa el INTERIOR (encb_lab1) vía este callback (AppNavGraph) ──
fun WorldMapViewModel.completeMission3Evidence() {
    if (mission3Phase != Mission3.PHASE_ASSAULT) return
    mission3Phase = Mission3.PHASE_DONE
    hasFirearm = true   // 🔫 RECOMPENSA: primera arma de fuego (persistida en el guardado)
    markMissionCompleted(MissionCatalog.MISSION_3_ID)
    _uiState.update { it.copy(
        currentObjective = MissionCatalog.M3_RECUPERAR_EVIDENCIA,
        objectiveDone = true,
        interactionPrompt = "🔫 ¡MISIÓN 3 COMPLETADA! Conseguiste tu primera ARMA DE FUEGO",
        // 🆕 Cierre narrativo (2026-07-12): cómic "mission3_outro" (IntroPOW23..24) al volver
        // al mapa (AppNavGraph espera a salir del interior, igual que el intro de la M3).
        pendingMission3OutroComic = true
    ) }
    soundManager.playMisionCumplida()
    android.util.Log.d("POW_DBG", "MISIÓN 3: COMPLETADA (evidencia + arma de fuego)")
}

/** Consume la bandera del cómic de cierre de la M3 (AppNavGraph, antes de navegar). */
fun WorldMapViewModel.consumeMission3OutroComic() {
    _uiState.update { it.copy(pendingMission3OutroComic = false) }
}

// ── LIMPIEZA (idempotente; NO toca mission3Phase — eso lo decide quien llama) ──
internal fun WorldMapViewModel.clearMission3Story() {
    mission3Npcs.clear()
    mission3DetectSinceMs = 0L
    mission3ReentryArmed = false
    stopM3PrankedyEscort()   // retira la escolta y re-arma el brote para el siguiente intento
    if (_uiState.value.mission3EnterEncb) consumeMission3EnterEncb()
}

// ── BROTE (fase 2): escolta de Prankedy + zombis que lo convierten ──────────────
//
// Asegura que Prankedy te ESCOLTE durante la infiltración (para que los zombis puedan atacarlo).
// Minimal a propósito: NO fija el objetivo (a diferencia de respawnPrankedyCompanionHere, que pone
// ESCOLTAR_PRANKEDY y rompería isMission3StoryActive()). runPrankedyTick (game loop) lo hace seguirte.
private fun WorldMapViewModel.ensureM3PrankedyEscort(playerLoc: GeoPoint, now: Long) {
    if (mission3PrankedyConverted) return
    val pm = prankedyManager
    if (pm.phase != ovh.gabrielhuav.pow.domain.models.ai.PrankedyPhase.HIRED || pm.location == null) {
        pm.spawnCompanion(playerLoc, roadNetwork, now)
        pm.warpTo(playerLoc)
    }
    if (!_uiState.value.prankedyEnabled || _uiState.value.prankedyLocation == null) {
        _uiState.update { it.copy(
            prankedyEnabled = true,
            prankedyLocation = pm.location,
            prankedyVisible = pm.location != null && !it.isDriving,
            prankedyPhase = pm.phase
        ) }
    }
}

// ARMA el brote SOLO cuando el jugador está MUY cerca de la ENCB (zona de guerra): siembra zombis
// (arte "estudiante zombi") + policías de CONTENCIÓN que los pelean. One-shot por intento.
private fun WorldMapViewModel.armM3BroteIfClose(playerLoc: GeoPoint) {
    if (mission3BroteArmed) return
    if (m3Dist(playerLoc.latitude, playerLoc.longitude, Mission3.ENCB_LAT, Mission3.ENCB_LON) > M3_BROTE_TRIGGER_DEG) return
    mission3BroteArmed = true
    for (i in 0 until M3_ZOMBIE_COUNT) {
        val ang = 2.0 * Math.PI * i / M3_ZOMBIE_COUNT + 0.9
        mission3Npcs["M3_ZOMBIE_$i"] = Npc(
            id = "M3_ZOMBIE_$i", type = NpcType.ZOMBIE,
            location = GeoPoint(Mission3.ENCB_LAT + sin(ang) * M3_ZOMBIE_RING_DEG,
                Mission3.ENCB_LON + cos(ang) * M3_ZOMBIE_RING_DEG),
            speed = M3_ZOMBIE_SPEED, isRemote = false, isMoving = true,
            visualConfig = null, zombieSpriteSet = "SPRITES/ZOMBIE/ESTUDIANTE"
        )
    }
    for (i in 0 until M3_CONTAIN_COP_COUNT) {
        val ang = 2.0 * Math.PI * i / M3_CONTAIN_COP_COUNT + 0.3
        mission3Npcs["M3_CONTAIN_$i"] = Npc(
            id = "M3_CONTAIN_$i", type = NpcType.POLICE_COP,
            location = GeoPoint(Mission3.ENCB_LAT + sin(ang) * M3_CONTAIN_RING_DEG,
                Mission3.ENCB_LON + cos(ang) * M3_CONTAIN_RING_DEG),
            speed = M3_CONTAIN_SPEED, isRemote = false, isMoving = true,
            policeDisembarked = true, policeCanShoot = false
        )
    }
    _uiState.update { it.copy(
        interactionPrompt = "🧟 ¡La ENCB es una ZONA DE GUERRA! Granaderos conteniendo el brote… cuida a Prankedy y CORRE a la entrada"
    ) }
}

// La REFRIEGA: zombis persiguen a Prankedy (si sigue humano) / policías de contención / al jugador;
// los policías de contención cargan contra el zombi más cercano y lo "someten" (reaparece en el
// anillo → batalla perpetua). Contacto sostenido con Prankedy = conversión; contacto con el jugador
// = daño. El "PRANKEDY zombi" es inmortal y siempre te persigue a TI.
private fun WorldMapViewModel.tickM3Brote(playerLoc: GeoPoint, now: Long) {
    val pm = prankedyManager
    val prankedyLoc = if (!mission3PrankedyConverted &&
        pm.phase == ovh.gabrielhuav.pow.domain.models.ai.PrankedyPhase.HIRED) pm.location else null
    val cops = mission3Npcs.values.filter { it.id.startsWith("M3_CONTAIN_") }

    fun nearest(from: GeoPoint, pts: List<GeoPoint>): GeoPoint? =
        pts.minByOrNull { m3Dist(from.latitude, from.longitude, it.latitude, it.longitude) }

    var zombieTouchingPrankedy = false
    for (npc in mission3Npcs.values.toList()) {
        val isPrankedyZombie = npc.id == "M3_PRANKEDY_ZOMBIE"
        val isZombie = isPrankedyZombie || npc.id.startsWith("M3_ZOMBIE_")
        if (isZombie) {
            // El Prankedy zombi SIEMPRE te caza a TI; los demás van al objetivo más cercano
            // (Prankedy humano / policía de contención / jugador) → se ve como refriega.
            val target = if (isPrankedyZombie) playerLoc
                else nearest(npc.location, listOfNotNull(prankedyLoc) + cops.map { it.location } + listOf(playerLoc)) ?: playerLoc
            val a = kotlin.math.atan2(target.latitude - npc.location.latitude, target.longitude - npc.location.longitude)
            var moved = GeoPoint(npc.location.latitude + sin(a) * M3_ZOMBIE_SPEED,
                npc.location.longitude + cos(a) * M3_ZOMBIE_SPEED)
            // CONTENCIÓN: si un policía lo tiene muy cerca, el zombi (normal) es "sometido" y
            // reaparece en el anillo (probabilístico → no cada tick). El Prankedy zombi es inmune.
            if (!isPrankedyZombie) {
                val copNear = cops.any { m3Dist(it.location.latitude, it.location.longitude, moved.latitude, moved.longitude) < M3_CONTAIN_KILL_DEG }
                if (copNear && Math.random() < M3_CONTAIN_SUBDUE_CHANCE) {
                    val ra = Math.random() * 2.0 * Math.PI
                    moved = GeoPoint(Mission3.ENCB_LAT + sin(ra) * M3_ZOMBIE_RING_DEG,
                        Mission3.ENCB_LON + cos(ra) * M3_ZOMBIE_RING_DEG)
                }
            }
            mission3Npcs[npc.id] = npc.copy(
                location = moved, isMoving = true, facingRight = cos(a) >= 0,
                health = if (isPrankedyZombie) M3_PRANKEDY_ZOMBIE_HP else npc.health,
                maxHealth = if (isPrankedyZombie) M3_PRANKEDY_ZOMBIE_HP else npc.maxHealth
            )
            if (!isPrankedyZombie && prankedyLoc != null &&
                m3Dist(moved.latitude, moved.longitude, prankedyLoc.latitude, prankedyLoc.longitude) < M3_ZOMBIE_CONTACT_DEG) {
                zombieTouchingPrankedy = true
            }
            if (m3Dist(moved.latitude, moved.longitude, playerLoc.latitude, playerLoc.longitude) < M3_ZOMBIE_CONTACT_DEG &&
                now - mission3ZombieHitCooldownMs > M3_ZOMBIE_HIT_COOLDOWN_MS) {
                mission3ZombieHitCooldownMs = now
                takeDamage(M3_ZOMBIE_DAMAGE)   // si te mata: triggerWastedSequence → MISIÓN FALLIDA (m3_*)
            }
        } else if (npc.id.startsWith("M3_CONTAIN_")) {
            // Policía de contención: carga contra el zombi más cercano (da el ambiente de batalla).
            val zpts = mission3Npcs.values.filter { it.id.startsWith("M3_ZOMBIE_") }.map { it.location }
            val z = nearest(npc.location, zpts)
            if (z != null) {
                val a = kotlin.math.atan2(z.latitude - npc.location.latitude, z.longitude - npc.location.longitude)
                val moved = GeoPoint(npc.location.latitude + sin(a) * M3_CONTAIN_SPEED,
                    npc.location.longitude + cos(a) * M3_CONTAIN_SPEED)
                mission3Npcs[npc.id] = npc.copy(location = moved, isMoving = true, facingRight = cos(a) >= 0)
            }
        }
    }

    // CONVERSIÓN de Prankedy: un zombi lo mantiene tocado por M3_CONVERT_MS.
    if (prankedyLoc != null && zombieTouchingPrankedy) {
        if (mission3PrankedyDetectSinceMs == 0L) mission3PrankedyDetectSinceMs = now
        if (now - mission3PrankedyDetectSinceMs > M3_CONVERT_MS) convertM3Prankedy(prankedyLoc)
    } else {
        mission3PrankedyDetectSinceMs = 0L
    }
}

// Prankedy es alcanzado → se convierte en el "PRANKEDY zombi" INMORTAL que te persigue hasta
// matarte. Quita al compañero y lo reemplaza por un NPC zombi de misión con su arte propio.
private fun WorldMapViewModel.convertM3Prankedy(atLoc: GeoPoint) {
    if (mission3PrankedyConverted) return
    mission3PrankedyConverted = true
    mission3PrankedyDetectSinceMs = 0L
    prankedyManager.deactivate()
    _uiState.update { it.copy(
        prankedyEnabled = false,
        prankedyVisible = false,
        prankedyLocation = null,
        prankedyProjectileActive = false,
        interactionPrompt = "🧟 ¡Los infectados alcanzaron a Prankedy! Ahora te persigue… ¡CORRE!"
    ) }
    mission3Npcs["M3_PRANKEDY_ZOMBIE"] = Npc(
        id = "M3_PRANKEDY_ZOMBIE",
        type = NpcType.ZOMBIE,
        location = atLoc,
        speed = M3_ZOMBIE_SPEED,
        isRemote = false,
        isMoving = true,
        visualConfig = null,
        zombieSpriteSet = "SPRITES/ZOMBIE/PRANKEDY",
        health = M3_PRANKEDY_ZOMBIE_HP,
        maxHealth = M3_PRANKEDY_ZOMBIE_HP
    )
}

// Retira la escolta y RE-ARMA el brote para el siguiente intento (retry / entrar al interior /
// dejar de seguir la misión). Idempotente.
private fun WorldMapViewModel.stopM3PrankedyEscort() {
    mission3PrankedyDetectSinceMs = 0L
    mission3PrankedyConverted = false
    mission3ZombieHitCooldownMs = 0L
    mission3BroteArmed = false
    if (prankedyManager.phase == ovh.gabrielhuav.pow.domain.models.ai.PrankedyPhase.HIRED) {
        prankedyManager.deactivate()
        _uiState.update { it.copy(
            prankedyEnabled = false,
            prankedyVisible = false,
            prankedyLocation = null,
            prankedyProjectileActive = false
        ) }
    }
}

// Look civil del paparazzi (chaleco caqui + cámara imaginaria; render modular del exterior).
private fun m3PaparazziVisual(): ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig =
    ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig(
        bodyFolder = "npc_walk_1",
        bodyPrefix = "npc_walk_1_",
        hairId = (1..5).random(),
        hairColor = androidx.compose.ui.graphics.Color.DarkGray,
        shirtColor = androidx.compose.ui.graphics.Color(0xFFC2B280),
        pantsColor = androidx.compose.ui.graphics.Color(0xFF37474F)
    )
