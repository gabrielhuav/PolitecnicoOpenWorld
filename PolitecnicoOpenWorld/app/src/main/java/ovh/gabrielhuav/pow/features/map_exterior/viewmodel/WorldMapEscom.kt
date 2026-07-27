package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import java.util.UUID
import kotlin.math.abs

internal fun WorldMapViewModel.spawnEscomDoors() {
        // Las coordenadas exactas se ajustan con el Modo Diseñador.
        // Estos valores son placeholders; reemplázalos con los guardados en Room DB
        // una vez hayas colocado las puertas con el Diseñador.
        val doors = listOf(
            ActiveCollectible(
                id          = "escom_door_norte",
                name        = "Puerta Norte ESCOM",
                description = "door",
                assetPath   = ESCOM_DOOR_ASSET,
                latitude    = 19.50490,
                longitude   = -99.14674
            ),
            ActiveCollectible(
                id          = "escom_door_sur",
                name        = "Puerta Sur ESCOM",
                description = "door",
                assetPath   = ESCOM_DOOR_ASSET,
                latitude    = 19.50420,
                longitude   = -99.14674
            )
        )
        _escomItems.value = doors
        _uiState.update { it.copy(isZombieHandSpawned = true) }
    }


internal fun WorldMapViewModel.isInsideEscom(lat: Double, lon: Double): Boolean {
        return abs(lat - ESCOM_BASE_LAT) < ESCOM_OFFSET &&
                abs(lon - ESCOM_BASE_LON) < ESCOM_OFFSET
    }


internal fun WorldMapViewModel.spawnOustedDriver(carLocation: GeoPoint) {
        // DE-DUP (2026-06-21): sincronizado al MIEMBRO canónico de WorldMapViewModel.kt antes de
        // eliminarlo. El miembro divergía: el conductor aparece más cerca (+0.00002 vs +0.00005) y
        // REACCIONA según personalidad (trait/fear/aggro/llamar a la policía); la extensión vieja no.
        // El conductor desalojado aparece JUNTO al coche (~2 m), como si se bajara por la puerta.
        val offsetLoc = GeoPoint(carLocation.latitude + 0.00002, carLocation.longitude + 0.00002)
        val randomHairId = (1..5).random()
        val randomHairColor = listOf(
            androidx.compose.ui.graphics.Color.Black,
            androidx.compose.ui.graphics.Color.DarkGray,
            androidx.compose.ui.graphics.Color(0xFF8B4513),
            androidx.compose.ui.graphics.Color(0xFFDAA520)
        ).random()
        val randomShirtColor = listOf(
            androidx.compose.ui.graphics.Color.White,
            androidx.compose.ui.graphics.Color.Red,
            androidx.compose.ui.graphics.Color.Blue,
            androidx.compose.ui.graphics.Color.Green
        ).random()
        val visualConfig = ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig(
            bodyFolder = "npc_walk_1",
            bodyPrefix = "npc_walk_1_",
            hairId = randomHairId,
            hairColor = randomHairColor,
            shirtColor = randomShirtColor,
            pantsColor = androidx.compose.ui.graphics.Color.DarkGray
        )
        // REACCIÓN AL ROBO según personalidad: el cobarde huye (estado de miedo), el
        // agresivo te embiste (estado aggro) y el pasivo simplemente se aleja andando.
        val trait = NpcAiManager.rollTrait()
        val now = System.currentTimeMillis()
        val driver = Npc(
            id = UUID.randomUUID().toString(),
            type = NpcType.PERSON,
            location = offsetLoc,
            speed = NpcAiManager.PERSON_SPEED,
            isMoving = true,
            visualConfig = visualConfig,
            trait = trait,
            fearUntil = if (trait == ovh.gabrielhuav.pow.domain.models.map.NpcTrait.COWARD) now + NpcAiManager.FEAR_DURATION_MS else 0L,
            fearFromLat = carLocation.latitude,
            fearFromLon = carLocation.longitude,
            aggroUntil = if (trait == ovh.gabrielhuav.pow.domain.models.map.NpcTrait.AGGRESSIVE) now + NpcAiManager.AGGRO_DURATION_MS else 0L,
            // Llama a la policía unos segundos (muestra 📞 sobre su cabeza).
            callingUntil = now + 4000L
        )
        remoteEntities[driver.id] = driver
    }

