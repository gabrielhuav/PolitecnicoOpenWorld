package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.serialization.encodeToString
import ovh.gabrielhuav.pow.data.json.PowJson

import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint


import android.content.Context
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.platform.assets.PowAssets
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.toast_car_injected
import ovh.gabrielhuav.pow.shared.recursos.toast_error_escom_missing
import ovh.gabrielhuav.pow.shared.recursos.toast_error_escom_navgraph

// ─────────────────────────────────────────────────────────────────────────────
// Items de ESCOM + inyección de coche dinámico (Modo Diseñador) extraídos del VM.
// spawnDynamicCarInEscom usa normalizeNavGraph (miembro internal del VM) y updateNpcsState.
// ─────────────────────────────────────────────────────────────────────────────

    /**
     * Sincroniza los items de ESCOM. La "Mano del Apocalipsis" se ELIMINÓ: ya no se
     * spawnea ninguna mano (el apocalipsis se activa desde el menú de Opciones).
     */
@Suppress("UnusedParameter")
internal fun WorldMapViewModel.spawnEscomItems(roadNetwork: List<MapWay>, cantidad: Int = 1) {
        // La "Mano del Apocalipsis" se ELIMINÓ de ESCOM (a petición). El modo zombi global
        // se activa/desactiva desde Opciones → "Activar/Desactivar Apocalipsis" (o el botón
        // flotante de salida). Aquí ya no se spawnea ninguna mano: dejamos vacíos los items
        // de ESCOM y marcamos el flag "sincronizado" para que el game loop no re-llame.
        if (_escomItems.value.any { it.id == "global_zombie_hand" }) {
            _escomItems.value = _escomItems.value.filter { it.id != "global_zombie_hand" }
        }
        _uiState.update { it.copy(isZombieHandSpawned = true) }
    }

internal fun WorldMapViewModel.collectEscomItem() {
        val loc = _uiState.value.currentLocation ?: return
        val interactionRadius = 0.00015
        val itemToCollect = _escomItems.value.find {
            distance(loc, GeoPoint(it.latitude, it.longitude)) <= interactionRadius
        }

        if (itemToCollect != null) {
            _escomItems.update { currentList -> currentList.filter { it.id != itemToCollect.id } }
        }
    }

internal fun WorldMapViewModel.spawnDynamicCarInEscom() {
        // 1. Cargar el JSON del navgraph de ESCOM si no está en memoria
        if (escomNavGraph == null) {
            try {
                val texto = PowAssets.texto("CONFIG/navgraphs/escom_navgraph.json")
                escomNavGraph = normalizeNavGraph(
                    PowJson.decodeFromString<ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph>(texto),
                )
            } catch (e: Exception) {
                avisarDesdeRecurso(Res.string.toast_error_escom_navgraph)
                return
            }
        }

        val navGraph = escomNavGraph ?: return

        // 2. Buscar el edificio ESCOM en el mapa
        val escomLandmarkBase = _uiState.value.landmarks.find { it.assetPath.contains("building_escom", ignoreCase = true) }
        if (escomLandmarkBase == null) {
            avisarDesdeRecurso(Res.string.toast_error_escom_missing)
            return
        }

        // CRUCIAL: Inyectarle el navGraph al Landmark para que la IA (NpcAiManager) pueda leer las "entryWays"
        val escomLandmark = escomLandmarkBase.copy(navGraph = navGraph)

        // 3. Obtener el carril de entrada (el objeto real LocalWay)
        val entryWayId = navGraph.entryWays.firstOrNull() ?: return
        val entryWay = navGraph.ways.find { it.id == entryWayId } ?: return
        val entryNode = entryWay.nodes.firstOrNull() ?: return

        // 4. Calcular posición global real en base al nodo local 0,0
        val spawnGeoPoint = escomLandmark.toGlobalGeoPoint(entryNode.localX, entryNode.localY)

        // 5. Crear el NPC con los estados exactos que exige el motor de IA
        val newCarId = "DYN_CAR_${System.currentTimeMillis()}"
        val newCar = ovh.gabrielhuav.pow.domain.models.map.Npc(
            id = newCarId,
            type = ovh.gabrielhuav.pow.domain.models.map.NpcType.CAR,
            location = spawnGeoPoint,
            carColor = android.graphics.Color.WHITE,
            carModel = ovh.gabrielhuav.pow.domain.models.map.CarModel.SPORT,
            rotationAngle = 0f,
            speed = ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager.CAR_SPEED,

            // 👇 PROPIEDADES QUE EVITAN QUE LA IA LO ELIMINE
            navState = ovh.gabrielhuav.pow.domain.models.map.NpcNavState.MICRO_LANDMARK,
            currentLandmark = escomLandmark, // Pasamos el objeto con el navGraph
            currentLocalWay = entryWay,      // Pasamos el objeto de la calle
            targetNodeIndex = 1,             // Le decimos que avance al nodo 1
            moveDirection = 1                // Dirección hacia adelante
        )

        // 6. Inyectarlo a la FUENTE DE LA VERDAD (remoteEntities)
        remoteEntities[newCarId] = newCar

        // 7. Refrescar la pantalla
        updateNpcsState()

        avisarDesdeRecurso(Res.string.toast_car_injected)
    }

