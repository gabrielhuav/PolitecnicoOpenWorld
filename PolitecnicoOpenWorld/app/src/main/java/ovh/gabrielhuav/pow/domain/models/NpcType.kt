package ovh.gabrielhuav.pow.domain.models

enum class NpcType(val drawableName: String) {
    PERSON("ic_npc_person"),
    CAR("ic_npc_car"),

    // ─── POLICÍA (sistema de nivel de búsqueda estilo GTA) ───────────────────
    // POLICE_CAR usa el asset especial de patrulla (VEHICLES/POLICE_TOPDOWN, sin
    // repintar). POLICE_COP no tiene asset de persona: se dibuja con un EMOJI de
    // policía. Ambos los simula localmente el jugador con nivel de búsqueda y se
    // replican a los demás clientes para que también los vean (multijugador).
    POLICE_CAR("ic_npc_car"),
    POLICE_COP("ic_npc_person")
}
