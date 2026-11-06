package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import ovh.gabrielhuav.pow.domain.models.map.NpcNavState

/** Un coche estacionado es un obstáculo fijo; el tráfico en marcha sí puede separarse. */
internal fun shouldDisplaceTrafficAfterPlayerCollision(navState: NpcNavState): Boolean =
    navState != NpcNavState.PARKED

/** Primer id (orden alfabético) que toca al jugador; el orden de un ConcurrentHashMap no es estable. */
internal fun firstContactingId(ids: Iterable<String>, touches: (String) -> Boolean): String? =
    ids.sorted().firstOrNull(touches)
