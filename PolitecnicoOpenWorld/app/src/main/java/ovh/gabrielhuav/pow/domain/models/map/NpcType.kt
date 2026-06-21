package ovh.gabrielhuav.pow.domain.models.map

enum class NpcType(val drawableName: String) {
    PERSON("ic_npc_person"),
    CAR("ic_npc_car"),

    // ─── POLICIA (sistema de nivel de busqueda estilo GTA) ───────────────────
    POLICE_CAR("ic_npc_car"),
    POLICE_COP("ic_npc_person"),
    
    // ─── APOCALIPSIS ZOMBI GLOBAL ───
    ZOMBIE("ic_npc_person")
}