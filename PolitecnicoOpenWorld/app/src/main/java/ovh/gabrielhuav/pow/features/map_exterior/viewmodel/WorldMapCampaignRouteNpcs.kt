package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.compose.ui.graphics.Color
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig
import ovh.gabrielhuav.pow.domain.models.map.MapNode
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager

// ─────────────────────────────────────────────────────────────────────────────
// NPCs DE PRUEBA QUE CAMINAN POR LA RUTA ROJA DE CAMPAÑA
//
// Objetivo: sembrar 60 NPCs repartidos a lo largo de la línea roja de la misión
// (`campaignRouteWaypoints`) y que REALMENTE caminen por ella (no animación de
// caminado en el mismo sitio).
//
// Cómo: se construye una "calle/ruta virtual" para el motor de IA a partir de los
// waypoints (MapNode → MapWay con isForPeople=true), se instancian 60 Npc con esa
// `currentWay`, y se INYECTAN en el motor (NpcAiManager). El motor (moveNpc) los
// avanza nodo a nodo y, al llegar a un extremo de la ruta virtual (sin vías
// conectadas), invierte la dirección → caminan hacia adelante y hacia atrás.
//
// Persistencia: la fuente de verdad de los NPCs de IA es `remoteEntities` (el game
// loop re-alimenta serverNpcs desde ahí cada pocos ticks). Por eso los NPCs viven
// en remoteEntities con un id prefijado (NpcAiManager.ROUTE_NPC_PREFIX), que los
// deja EXENTOS del despawn por distancia y del cull por cupo (ver NpcAiManager).
// ─────────────────────────────────────────────────────────────────────────────

private const val ROUTE_NPC_COUNT = 60
private const val ROUTE_WAY_ID = -9001L          // id negativo: no colisiona con ways de OSM (positivos)
private const val ROUTE_NODE_ID_BASE = -100000L  // ids negativos: no colisionan con nodos de OSM

/** ¿Hay NPCs de ruta sembrados ahora mismo? */
internal fun WorldMapViewModel.hasCampaignRouteNpcs(): Boolean =
    remoteEntities.keys.any { it.startsWith(NpcAiManager.ROUTE_NPC_PREFIX) }

/**
 * Siembra [ROUTE_NPC_COUNT] NPCs repartidos por los nodos de [route] (la línea roja) y los
 * inyecta en el motor de IA para que CAMINEN por ella. Idempotente: limpia los previos primero.
 */
internal fun WorldMapViewModel.spawnCampaignRouteNpcs(route: List<GeoPoint>) {
    clearCampaignRouteNpcs()
    if (route.size < 2) return

    // 1) "Calle/ruta virtual" para el motor: waypoints → nodos → MapWay peatonal.
    val nodes = route.mapIndexed { i, p -> MapNode(id = ROUTE_NODE_ID_BASE - i, lat = p.latitude, lon = p.longitude) }
    val customWay = MapWay(id = ROUTE_WAY_ID, nodes = nodes, isForCars = false, isForPeople = true)
    val lastIdx = nodes.size - 1

    // 2) 60 NPCs repartidos por los nodos, con la ruta virtual asignada.
    val newNpcs = ArrayList<Npc>(ROUTE_NPC_COUNT)
    for (i in 0 until ROUTE_NPC_COUNT) {
        val nodeIdx = (0..lastIdx).random()
        // Dirección y nodo objetivo válidos dentro de los límites de la ruta.
        val dir = when (nodeIdx) {
            0 -> 1
            lastIdx -> -1
            else -> if (Math.random() < 0.5) 1 else -1
        }
        val target = (nodeIdx + dir).coerceIn(0, lastIdx)
        val node = nodes[nodeIdx]
        newNpcs.add(
            Npc(
                id = "${NpcAiManager.ROUTE_NPC_PREFIX}$i",
                type = NpcType.PERSON,
                location = GeoPoint(node.lat, node.lon),
                speed = NpcAiManager.PERSON_SPEED,
                currentWay = customWay,
                targetNodeIndex = target,
                moveDirection = dir,
                isRemote = false,
                isMoving = true,
                visualConfig = routeNpcVisual(i)
            )
        )
    }

    // 3) Inyección: persisten en remoteEntities (fuente de verdad que re-alimenta serverNpcs)
    //    y se siembran de inmediato en serverNpcs para que aparezcan sin esperar un re-sync.
    newNpcs.forEach { remoteEntities[it.id] = it }
    npcAiManager.addServerNpcs(newNpcs)
    updateNpcsState()
    android.util.Log.d("POW_DBG", "Ruta de campaña: sembrados ${newNpcs.size} NPCs sobre ${nodes.size} waypoints")
}

/** Quita todos los NPCs de ruta sembrados. Idempotente. */
internal fun WorldMapViewModel.clearCampaignRouteNpcs() {
    val ids = remoteEntities.keys.filter { it.startsWith(NpcAiManager.ROUTE_NPC_PREFIX) }
    if (ids.isEmpty()) return
    ids.forEach { remoteEntities.remove(it) }
    updateNpcsState()
}

/**
 * DEBUG (panel de Debug Interiores): alterna los NPCs de la ruta. Si ya hay, los quita; si no,
 * los siembra sobre la ruta roja ACTUAL (`campaignRouteWaypoints`). Sin ruta roja no hace nada.
 */
internal fun WorldMapViewModel.toggleCampaignRouteNpcsDebug() {
    if (hasCampaignRouteNpcs()) {
        clearCampaignRouteNpcs()
    } else {
        spawnCampaignRouteNpcs(_uiState.value.campaignRouteWaypoints)
    }
}

// Visual variado para los NPCs de ruta (mismo estilo que la multitud de la Misión 2).
private fun routeNpcVisual(seed: Int): CharacterVisualConfig {
    val hairColors = listOf(Color.Black, Color.DarkGray, Color(0xFF8B4513), Color(0xFFDAA520))
    val shirtColors = listOf(
        Color.White, Color.Red, Color.Blue, Color.Green,
        Color(0xFF9C27B0), Color(0xFFFF9800), Color(0xFF00BCD4)
    )
    return CharacterVisualConfig(
        bodyFolder = "npc_walk_1",
        bodyPrefix = "npc_walk_1_",
        hairId = (seed % 5) + 1,
        hairColor = hairColors[seed % hairColors.size],
        shirtColor = shirtColors[seed % shirtColors.size],
        pantsColor = Color(0xFF37474F)
    )
}
