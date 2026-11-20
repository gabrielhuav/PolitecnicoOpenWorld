package ovh.gabrielhuav.pow.domain.models.ai

import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.distanceMetersTo

// Tope de los PEATONES DEL CAMPUS (`CAMPUS_PED_*`). Estaban excluidos de TODOS los cupos de NpcAiManager
// (activeCount, totalCount, nonParkedAlive, recorte de exceso), así que cada escaneo de spawn añadía 1-3 más
// y nunca se retiraban mientras el jugador siguiera en el campus: en el Motorola pasaron de ~60 a ~200 en
// unos minutos, con el WebView (un marcador DOM por NPC) cada vez más lento. Ver 04_MAP_EXTERIOR.md.

internal const val CAMPUS_PED_PREFIX = "CAMPUS_PED_"

private const val CAMPUS_PED_BASE_CAP = 36
private const val CAMPUS_PED_MIN_CAP = 8
private const val CAMPUS_PED_MAX_CAP = 100

/** Tope de peatones del campus según la densidad (gama del equipo × ciudad × ajuste del usuario). */
internal fun campusPedestrianCap(popFactor: Float): Int =
    (CAMPUS_PED_BASE_CAP * popFactor).toInt().coerceIn(CAMPUS_PED_MIN_CAP, CAMPUS_PED_MAX_CAP)

/** Cuántos peatones del campus se pueden añadir ahora sin pasar el tope. */
internal fun campusPedestriansToSpawn(alive: Int, cap: Int, requested: Int): Int =
    (cap - alive).coerceIn(0, requested.coerceAtLeast(0))

/** Los peatones del campus que sobran sobre [cap], empezando por los más lejanos al jugador. */
internal fun campusPedestriansToCull(npcs: List<Npc>, cap: Int, player: GeoPoint): List<Npc> {
    val campus = npcs.filter { it.id.startsWith(CAMPUS_PED_PREFIX) }
    val excess = campus.size - cap
    if (excess <= 0) return emptyList()
    return campus.sortedByDescending { it.location.distanceMetersTo(player) }.take(excess)
}
