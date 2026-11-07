package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import ovh.gabrielhuav.pow.data.json.jsonOf

// ───────────────────────────────────────────────────────────────────────────────────
// PARCIAL del WorldMapViewModel: COMBATE (melee del jugador, atropello estilo
// Midnight Club, daño por contacto de NPCs/zombis, provocación de la policía del
// apocalipsis y atacante IMPLACABLE). Extraído de WorldMapViewModel.kt en el refactor
// de tamaño. El ESTADO (lastAttackTime, npcHitStreak, relentlessNpcs, npcContactCooldowns,
// lastZombieBiteMs y las constantes ATTACK_*/RUN_OVER_*/NPC_CONTACT_*/DODGE_*) sigue en el
// ViewModel; aquí solo hay lógica. NO duplicar estos nombres como miembros (gana el
// miembro y la extensión queda muerta — ver archivo 09).
// ───────────────────────────────────────────────────────────────────────────────────

import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.map.CarModel
import ovh.gabrielhuav.pow.domain.models.map.VehicleFootprint
import ovh.gabrielhuav.pow.domain.models.map.offsetMeters
import ovh.gabrielhuav.pow.domain.models.map.resolveTrafficImpact
import ovh.gabrielhuav.pow.domain.models.map.TrafficImpactResolution
import kotlin.math.cos
import kotlin.math.sin

fun WorldMapViewModel.performPlayerAttack() {
    val now = System.currentTimeMillis()
    if (now - lastAttackTime < ATTACK_COOLDOWN_MS) return
    lastAttackTime = now
    soundManager.playPunch()
    viewModelScope.launch(Dispatchers.Default) {
        delay(300L)
        val playerLoc = _uiState.value.currentLocation ?: return@launch
        // PRANKEDY hostil: el jugador puede defenderse golpeándolo. Si lo mata, desaparece
        // y reaparece tras un tiempo (lo gestiona PrankedyManager; el render lo oculta al
        // quedar su location en null).
        // En el MODO HISTORIA (escolta, fase HIRED) Prankedy es tu acompañante: TÚ NO le
        // puedes pegar (solo la policía puede dañarlo). Fuera de la escolta (Prankedy hostil)
        // sí puedes golpearlo para defenderte.
        if (prankedyManager.phase != ovh.gabrielhuav.pow.domain.models.ai.PrankedyPhase.HIRED) {
            prankedyManager.location?.let { pkLoc ->
                if (distance(playerLoc, pkLoc) <= ATTACK_RADIUS) {
                    prankedyManager.takeDamage(PLAYER_PUNCH_DAMAGE)
                    viewModelScope.launch(Dispatchers.Main) { fireImpactEffect() }
                }
            }
        }
        // MIEDO AL COMBATE (SP y host MP): cada golpe asusta a los civiles cercanos,
        // CONECTE O NO. Así huyen cuando los atacas, aunque falles el puñetazo.
        if (isServerDelegatedHost) {
            npcAiManager.triggerFear(playerLoc.latitude, playerLoc.longitude)
        }
        // También puedes golpear a los POLICÍAS a pie cercanos (mueren si llegan a 0).
        val deadCops = policeManager.playerHitPolice(
            playerLoc.latitude, playerLoc.longitude, ATTACK_RADIUS, PLAYER_PUNCH_DAMAGE
        )
        if (deadCops.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.Main) { fireImpactEffect(); updateNpcsState() }
            webSocketManager?.let { ws ->
                viewModelScope.launch(Dispatchers.IO) {
                    deadCops.forEach { pid ->
                        try { ws.sendMessage(jsonOf(mapOf("type" to "POLICE_DESTROY", "npcId" to pid))) } catch (_: Exception) {}
                    }
                }
            }
        }
        // MODO HISTORIA: los policías de la CAMPAÑA (escolta de Misión 1 / persecución de Misión 2)
        // viven en `campaignEscortPolice`, NO en policeManager ni en remoteEntities, así que antes
        // no se les podía golpear (R6/R8). Aquí sí los dañamos (mueren si llegan a 0).
        val deadCampaignCops = campaignEscortPolice.playerHitCops(
            playerLoc.latitude, playerLoc.longitude, ATTACK_RADIUS, PLAYER_PUNCH_DAMAGE
        )
        if (deadCampaignCops.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.Main) { fireImpactEffect(); updateNpcsState() }
        }
        // APOCALIPSIS: golpear a un policía CAZADOR (en remoteEntities, POLICE_COP) lo daña y lo
        // PROVOCA — a él y a los cercanos → te persiguen. (La policía del sistema de delitos está
        // en policeManager y no corre en apocalipsis.)
        if (_uiState.value.globalZombieMode) {
            val nowP = System.currentTimeMillis()
            var hitCop = false
            remoteEntities.entries.toList().forEach { (id, n) ->
                if (n.type == ovh.gabrielhuav.pow.domain.models.map.NpcType.POLICE_COP &&
                    distance(playerLoc, n.location) <= ATTACK_RADIUS) {
                    val nh = (n.health - PLAYER_PUNCH_DAMAGE).coerceAtLeast(0f)
                    if (nh <= 0f) {
                        remoteEntities.remove(id)
                        try { webSocketManager?.sendMessage(jsonOf(mapOf("type" to "NPC_DESTROY", "npcId" to id))) } catch (_: Exception) {}
                    } else {
                        remoteEntities[id] = n.copy(health = nh, aggroUntil = nowP + NpcAiManager.AGGRO_DURATION_MS)
                    }
                    hitCop = true
                }
            }
            if (hitCop) {
                provokeApocalypsePolice(playerLoc)
                viewModelScope.launch(Dispatchers.Main) { fireImpactEffect(); updateNpcsState() }
            }
        }
        val targetNpcEntry = remoteEntities.entries
            .filter {
                !it.value.isDying &&
                        (it.value.type == NpcType.PERSON || it.value.type == ovh.gabrielhuav.pow.domain.models.map.NpcType.ZOMBIE) &&
                        distance(playerLoc, it.value.location) <= ATTACK_RADIUS
            }
            .minByOrNull { distance(playerLoc, it.value.location) }
        if (targetNpcEntry != null) {
            val npcId = targetNpcEntry.key
            val currentNpc = targetNpcEntry.value
            val isRemotePlayer = !currentNpc.displayName.isNullOrBlank()
            if (isRemotePlayer) {
                try {
                    webSocketManager?.sendMessage(
                        jsonOf(
                            mapOf(
                                "type" to "PLAYER_DAMAGE",
                                "targetId" to npcId,
                                "damage" to PLAYER_PUNCH_DAMAGE
                            )
                        )
                    )
                    // FIX: Feedback visual para el atacante al golpear a otro jugador
                    viewModelScope.launch(Dispatchers.Main) { fireImpactEffect() }
                } catch (e: Exception) { Log.e("Combat", "Error enviando PLAYER_DAMAGE: ${e.message}") }
            } else {
                // DELITO: golpear a un civil sube el nivel de búsqueda (como en GTA).
                if (currentNpc.type == NpcType.PERSON) raiseWantedLevel(1)
                // APOCALIPSIS: agredir a un CIVIL provoca a la policía que esté en tu fog (te ven).
                if (_uiState.value.globalZombieMode && currentNpc.type == NpcType.PERSON) {
                    provokeApocalypsePolice(playerLoc)
                }
                val damage = PLAYER_PUNCH_DAMAGE
                val newHealth = (currentNpc.health - damage).coerceAtLeast(0f)
                if (newHealth <= 0f) {
                    npcHitStreak.remove(npcId)
                    relentlessNpcs.remove(npcId)
                    remoteEntities[npcId] = currentNpc.copy(health = 0f, isDying = true)
                    updateNpcsState()
                    delay(1000L)
                    remoteEntities.remove(npcId)
                    try {
                        webSocketManager?.sendMessage(
                            jsonOf(mapOf("type" to "NPC_DESTROY", "npcId" to npcId))
                        )
                    } catch (e: Exception) { Log.e("Combat", "Error enviando NPC_DESTROY para npcId=$npcId", e) }
                    updateNpcsState()
                } else {
                    // CONTRAATAQUE: si el NPC golpeado sobrevive y es AGGRESSIVE, entra
                    // en estado de embestida hacia el jugador (lo persigue, visual) Y
                    // te DEVUELVE el golpe de forma garantizada poco después.
                    val retaliate = currentNpc.trait == ovh.gabrielhuav.pow.domain.models.map.NpcTrait.AGGRESSIVE
                    // KNOCKBACK: al golpear un ZOMBI que sobrevive, lo empujamos hacia atrás
                    // (alejándolo del jugador) para que se note el golpe, como en el minijuego.
                    // El Host lo retoma desde remoteEntities en el siguiente tick (recoil visible).
                    val knockedLoc = if (currentNpc.type == ovh.gabrielhuav.pow.domain.models.map.NpcType.ZOMBIE) {
                        val dLat = currentNpc.location.latitude - playerLoc.latitude
                        val dLon = currentNpc.location.longitude - playerLoc.longitude
                        val d = kotlin.math.sqrt(dLat * dLat + dLon * dLon)
                        if (d > 1e-9) {
                            val kb = 0.00007 // ~7-8 m de empuje
                            GeoPoint(currentNpc.location.latitude + (dLat / d) * kb, currentNpc.location.longitude + (dLon / d) * kb)
                        } else currentNpc.location
                    } else currentNpc.location
                    remoteEntities[npcId] = currentNpc.copy(
                        location = knockedLoc,
                        health = newHealth,
                        aggroUntil = if (retaliate) System.currentTimeMillis() + NpcAiManager.AGGRO_DURATION_MS else currentNpc.aggroUntil
                    )
                    updateNpcsState()
                    if (retaliate) {
                        // Racha de golpes seguidos contra este NPC.
                        val streak = (npcHitStreak[npcId] ?: 0) + 1
                        npcHitStreak[npcId] = streak

                        // Golpe de vuelta DETERMINISTA: tras un breve "windup", si el NPC
                        // sigue vivo y el jugador continúa a su alcance, le pega y lo
                        // hace notar (vida baja + destello + 💥). No depende de la
                        // detección de contacto del bucle (que podía no dispararse).
                        viewModelScope.launch(Dispatchers.Main) {
                            delay(450L)
                            val pl = _uiState.value.currentLocation
                            val attacker = remoteEntities[npcId]
                            if (pl != null && attacker != null && !attacker.isDying &&
                                !_uiState.value.isDriving &&
                                distance(pl, attacker.location) <= ATTACK_RADIUS) {
                                takeDamage(NPC_CONTACT_DAMAGE)
                            }
                        }

                        // IMPLACABLE: si lo golpeas RELENTLESS_HIT_STREAK veces o más, ya
                        // no para de pegarte hasta matarte (o hasta morir él).
                        if (streak >= RELENTLESS_HIT_STREAK && relentlessNpcs.add(npcId)) {
                            startRelentlessAttacker(npcId)
                        }
                    }
                }
            }
        }
    }
}

// ATROPELLO: estando al volante, los peatones dentro de RUN_OVER_RADIUS reciben
// daño proporcional a la velocidad; mueren si llegan a 0 y, en cualquier caso, los
// testigos se asustan. Solo el host simula NPCs, así que solo él aplica esto.
internal fun WorldMapViewModel.runOverNpcs(
    previousPlayerLoc: GeoPoint,
    playerLoc: GeoPoint,
    speed: Double,
): TrafficImpactResolution? {
    // Colisión coche-coche: TODOS los clientes (un no-host no debe atravesar coches). Atropello de
    // peatones/zombis, desplazar tráfico y miedo: solo el host, que es quien simula NPCs.
    val isHost = isServerDelegatedHost
    val spd = kotlin.math.abs(speed)
    val damage = (spd / MAX_SPEED).toFloat().coerceIn(0f, 1f) * 120f
    val extreme = spd >= RUN_OVER_EXTREME_SPEED
    val now = System.currentTimeMillis()
    // Vector de avance del coche y su perpendicular (para esquivar/empujar a un lado).
    val heading = Math.toRadians(_uiState.value.vehicleRotation.toDouble())
    val hdLat = cos(heading); val hdLon = sin(heading)
    val perpLat = -hdLon; val perpLon = hdLat
    val playerFootprint = VehicleFootprint(
        center = playerLoc,
        headingDegrees = _uiState.value.vehicleRotation,
        dimensions = (_uiState.value.currentVehicleModel ?: CarModel.SEDAN).collisionDimensions,
    )

    fun killOrHurt(id: String, npc: Npc, giveStar: Boolean = false) {
        val newHealth = (npc.health - damage).coerceAtLeast(0f)
        if (newHealth <= 0f) {
            remoteEntities[id] = npc.copy(health = 0f, isDying = true)
            if (giveStar) raiseWantedLevel(1)
            viewModelScope.launch { delay(1000L); remoteEntities.remove(id); updateNpcsState() }
            try { webSocketManager?.sendMessage(jsonOf(mapOf("type" to "NPC_DESTROY", "npcId" to id))) } catch (_: Exception) {}
        } else remoteEntities[id] = npc.copy(health = newHealth)
    }
    var changed = false
    var impactWorthy = false   // 💥 solo en atropello real o choque de auto, no al esquivar
    var trafficImpact: TrafficImpactResolution? = null
    var vehicleDisplaced = false
    vehicleContactLatch.prune(now)
    for ((id, npc) in remoteEntities) {
        if (!isHost) break
        if (!npc.displayName.isNullOrEmpty()) continue
        if (npc.isDying) continue
        val d = distance(playerLoc, npc.location)
        when (npc.type) {
            NpcType.PERSON -> {
                if (spd < RUN_OVER_MIN_SPEED) continue
                if (extreme && d <= RUN_OVER_RADIUS * 0.4) {
                    // Vas casi A FONDO (>= RUN_OVER_EXTREME_SPEED) → los reflejos no alcanzan, lo atropellas.
                    // Hitbox reducida (0.4) para que sea más difícil darles. Si los matas, obtienes 1 estrella.
                    killOrHurt(id, npc, giveStar = true); changed = true; impactWorthy = true
                    continue
                }
                if (d > DODGE_TRIGGER_RADIUS) continue
                // ¿Está DELANTE del coche? (producto punto con el avance). Solo esos esquivan.
                val relLat = npc.location.latitude - playerLoc.latitude
                val relLon = npc.location.longitude - playerLoc.longitude
                if (relLat * hdLat + relLon * hdLon <= 0) continue   // detrás/al costado: ignóralo
                // MIDNIGHT CLUB: marca el ESQUIVE ANIMADO (el Host lo anima como sidestep suave, NO
                // teletransporte). Si ya está esquivando, no lo re-disparo (dirección estable).
                if (npc.dodgeUntil <= now) {
                    val rel = relLat * perpLat + relLon * perpLon
                    val s = if (rel >= 0) 1.0 else -1.0   // hacia el lado al que ya se inclina
                    remoteEntities[id] = npc.copy(
                        dodgeUntil = now + DODGE_MS,
                        dodgeDirLat = perpLat * s,
                        dodgeDirLon = perpLon * s
                    )
                    changed = true
                }
            }
            ovh.gabrielhuav.pow.domain.models.map.NpcType.ZOMBIE -> {
                if (spd < RUN_OVER_MIN_SPEED) continue
                if (d > RUN_OVER_RADIUS) continue
                // Los zombis NO esquivan (shamblean): atropellables a cualquier velocidad.
                killOrHurt(id, npc); changed = true; impactWorthy = true
            }
            // Coches y patrullas: se resuelven después, ordenados por id (ver más abajo).
            NpcType.CAR, ovh.gabrielhuav.pow.domain.models.map.NpcType.POLICE_CAR -> continue
            else -> continue
        }
    }
    // remoteEntities es un ConcurrentHashMap: ordenar por id hace reproducible el primer contacto.
    fun footprintOf(npc: Npc) = VehicleFootprint(
        center = npc.location,
        headingDegrees = (npc.rotationAngle + 90f) % 360f,
        dimensions = npc.carModel.collisionDimensions,
    )
    val vehicleIds = ArrayList<String>(4)
    for ((id, npc) in remoteEntities) {
        if ((npc.type == NpcType.CAR || npc.type == NpcType.POLICE_CAR) &&
            npc.displayName.isNullOrEmpty() && !npc.isDying &&
            distance(playerLoc, npc.location) <= VEHICLE_CONTACT_COARSE_RADIUS
        ) vehicleIds.add(id)
    }
    val hitId = firstContactingId(vehicleIds) { id ->
        val other = remoteEntities[id]
        other != null && resolveTrafficImpact(previousPlayerLoc, playerFootprint, footprintOf(other)) != null
    }
    val hitNpc = hitId?.let { remoteEntities[it] }
    if (hitId != null && hitNpc != null) {
        val trafficFootprint = footprintOf(hitNpc)
        val impact = resolveTrafficImpact(previousPlayerLoc, playerFootprint, trafficFootprint)
        if (impact != null) {
            // Un coche estacionado es sólido (no se mueve); el tráfico en marcha se separa por vector mínimo.
            if (isHost && shouldDisplaceTrafficAfterPlayerCollision(hitNpc.navState)) {
                remoteEntities[hitId] = hitNpc.copy(
                    location = hitNpc.location.offsetMeters(
                        impact.trafficOffset.northMeters,
                        impact.trafficOffset.eastMeters,
                    ),
                )
                vehicleDisplaced = true
            }
            // La física se aplica en cada tick de contacto; el EVENTO solo al entrar en contacto.
            trafficImpact = impact
            if (vehicleContactLatch.registerContact("player|$hitId", now)) {
                logVehicleCollision(
                    "traffic npc=$hitId player=$playerLoc h=${_uiState.value.vehicleRotation} " +
                        "${playerFootprint.dimensions} npc=${hitNpc.location} h=${trafficFootprint.headingDegrees} " +
                        "${trafficFootprint.dimensions} separate=${impact.trafficOffset}",
                )
                if (isHost) changed = true
                impactWorthy = true
            }
        }
    }
    if (trafficImpact == null) {
        policeManager.activeUnits()
            .asSequence()
            .filter { it.type == NpcType.POLICE_CAR }
            .sortedBy { it.id }
            .forEach { patrol ->
                if (trafficImpact != null) return@forEach
                val footprint = VehicleFootprint(
                    center = patrol.location,
                    headingDegrees = (patrol.rotationAngle + 90f) % 360f,
                    dimensions = patrol.carModel.collisionDimensions,
                )
                val impact = resolveTrafficImpact(previousPlayerLoc, playerFootprint, footprint) ?: return@forEach
                policeManager.displaceVehicle(
                    patrol.id,
                    impact.trafficOffset.northMeters,
                    impact.trafficOffset.eastMeters,
                )
                trafficImpact = impact
                if (vehicleContactLatch.registerContact("player|patrol:${patrol.id}", now)) {
                    impactWorthy = true
                    logVehicleCollision("traffic patrol=${patrol.id} player=$playerLoc")
                }
            }
    }
    if (changed) npcAiManager.triggerFear(playerLoc.latitude, playerLoc.longitude)
    if (changed || vehicleDisplaced) updateNpcsState()
    if (impactWorthy) viewModelScope.launch(Dispatchers.Main) { fireImpactEffect() } // 💥 (con throttle)
    return trafficImpact
}

// GOLPE DE NPC AGRESIVO: los NPCs en estado de embestida (aggro) que tocan al
// jugador le hacen daño, con un cooldown por NPC para no vaciar la vida de golpe.
// Provoca a la policía del apocalipsis que esté en tu FOG (en frente): pasa a perseguirte
// (aggroUntil). Se llama al golpear a un poli o al agredir a un civil con un poli cerca.
internal fun WorldMapViewModel.provokeApocalypsePolice(playerLoc: GeoPoint) {
    if (!_uiState.value.globalZombieMode) return
    val until = System.currentTimeMillis() + NpcAiManager.AGGRO_DURATION_MS
    val fogDeg = 0.0003 // ~33 m: el poli debe ver el crimen LITERALMENTE enfrente para reaccionar
    var changed = false
    remoteEntities.entries.toList().forEach { (id, n) ->
        if (n.type == ovh.gabrielhuav.pow.domain.models.map.NpcType.POLICE_COP &&
            distance(playerLoc, n.location) <= fogDeg) {
            remoteEntities[id] = n.copy(aggroUntil = until)
            changed = true
        }
    }
    if (changed) updateNpcsState()
}

internal fun WorldMapViewModel.applyNpcContactDamage(playerLoc: GeoPoint) {
    // Muerto / en WASTED: no recibas daño (evita el 💥 repetido sobre el cadáver).
    if (_uiState.value.showWastedScreen || playerHealth <= 0f) return
    // SIN gate de host: el daño se aplica a TU PROPIO jugador en TU cliente, seas o no
    // el host de zona (el host solo decide quién SIMULA la IA, no quién recibe daño).
    val now = System.currentTimeMillis()
    for ((id, npc) in remoteEntities) {
        val isAggroPerson = npc.type == NpcType.PERSON && npc.aggroUntil > now
        // Policía del apocalipsis PROVOCADA (la golpeaste o agrediste a un civil frente a ella).
        val isAggroCop = npc.type == ovh.gabrielhuav.pow.domain.models.map.NpcType.POLICE_COP && npc.aggroUntil > now
        // El SCOUT ("Explorador") NO ataca al jugador (solo grita y huye).
        val isZombie = npc.type == ovh.gabrielhuav.pow.domain.models.map.NpcType.ZOMBIE && npc.health > 0f &&
                npc.zombieRole != ovh.gabrielhuav.pow.domain.models.map.ZombieRole.SCOUT
        if (!isAggroPerson && !isAggroCop && !isZombie) continue
        if (distance(playerLoc, npc.location) > NPC_CONTACT_RADIUS) continue
        if (_uiState.value.isDriving) continue // en coche no te golpean (te bajan, no te pegan)
        // Mordida de zombi: cooldown GLOBAL (daño moderado aunque te rodeen muchos).
        if (isZombie) {
            if (now - lastZombieBiteMs < ZOMBIE_BITE_TO_PLAYER_MS) continue
            lastZombieBiteMs = now
            npcContactCooldowns[id] = now
            viewModelScope.launch(Dispatchers.Main) { takeDamage(ZOMBIE_BITE_TO_PLAYER_DMG) }
            continue
        }
        val last = npcContactCooldowns[id] ?: 0L
        if (now - last < NPC_CONTACT_COOLDOWN_MS) continue
        npcContactCooldowns[id] = now
        viewModelScope.launch(Dispatchers.Main) { takeDamage(NPC_CONTACT_DAMAGE) } // takeDamage ya dispara el 💥
    }
    // Policía de CAMPAÑA (no vive en remoteEntities): si te alcanza A PIE, te pega con cooldown
    // global. Antes no hacían daño al jugador (R8). Solo en escolta/persecución y fuera del coche.
    if (!_uiState.value.isDriving &&
        (isCampaignEscortActive() || isMission1ChaseActive()) &&
        campaignEscortPolice.copTouching(playerLoc.latitude, playerLoc.longitude, NPC_CONTACT_RADIUS)) {
        val lastCampaign = npcContactCooldowns["CAMPAIGN_COP"] ?: 0L
        if (now - lastCampaign >= NPC_CONTACT_COOLDOWN_MS) {
            npcContactCooldowns["CAMPAIGN_COP"] = now
            viewModelScope.launch(Dispatchers.Main) { takeDamage(NPC_CONTACT_DAMAGE) }
        }
    }
}

// IMPLACABLE: el NPC persigue y golpea al jugador sin descanso hasta matarlo (o hasta
// morir/desaparecer). Refresca su aggro para que nunca deje de perseguir y le pega
// cada NPC_CONTACT_COOLDOWN_MS si está a su alcance.
private fun WorldMapViewModel.startRelentlessAttacker(npcId: String) {
    viewModelScope.launch(Dispatchers.Main) {
        while (true) {
            delay(NPC_CONTACT_COOLDOWN_MS)
            val npc = remoteEntities[npcId] ?: break        // murió/despawneó
            if (npc.isDying) break
            if (playerHealth <= 0f) break                    // jugador muerto → la secuencia WASTED sigue
            // Mantener vivo el aggro para que NO deje de perseguir.
            remoteEntities[npcId] = npc.copy(aggroUntil = System.currentTimeMillis() + NpcAiManager.AGGRO_DURATION_MS)
            val pl = _uiState.value.currentLocation
            if (pl != null && !_uiState.value.isDriving && distance(pl, npc.location) <= ATTACK_RADIUS) {
                takeDamage(NPC_CONTACT_DAMAGE)
            }
        }
        relentlessNpcs.remove(npcId)
    }
}

// Radio grueso (~55 m, en grados) para pre-filtrar vehículos candidatos a contacto con el jugador.
private const val VEHICLE_CONTACT_COARSE_RADIUS = 0.0005
