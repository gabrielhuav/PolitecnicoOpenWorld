package ovh.gabrielhuav.pow.domain.models

// Objetivo de la campaña (Modo Historia). El widget de Objetivos muestra `title` y la
// distancia al destino; al entrar en `arriveRadiusMeters` del destino, el objetivo se
// marca como cumplido (ver WorldMapViewModel.checkObjectiveProgress).
data class CampaignObjective(
    val id: String,
    val title: String,
    val description: String,
    val targetLat: Double,
    val targetLon: Double,
    val arriveRadiusMeters: Double = 60.0
)

// Catálogo de misiones/objetivos de la campaña. Por ahora solo la Misión 1.
object MissionCatalog {
    // Misión 1: ir de ESCOM a la ENCB.
    // ⚠️ Coordenadas APROXIMADAS de la ENCB — ajústalas al punto exacto que quieras.
    val IR_ENCB = CampaignObjective(
        id = "ir_encb",
        title = "Ve a la ENCB",
        description = "Dirígete a la ENCB para investigar el origen del brote.",
        targetLat = 19.498600,
        targetLon = -99.148900
    )

    // PUERTA de la ESCOM (puerta norte, de WorldMapEscom.spawnEscomDoors). Es el destino de la
    // Misión 1 y de donde sale la multitud al llegar. Compartida por los objetivos y la línea GPS.
    const val ESCOM_DOOR_LAT = 19.50490
    const val ESCOM_DOOR_LON = -99.14674

    // Punto donde el jugador ENTRA al mapa global tras el outro de la intro (al terminar la 2ª
    // secuencia de cómics). Es el CHECKPOINT de la Misión 1 (escolta): si fallas la misión,
    // reapareces AQUÍ (no en ESCOM ni en la posición guardada). X = lon, Y = lat.
    const val MISSION1_SPAWN_LAT = 19.50102
    const val MISSION1_SPAWN_LON = -99.14421

    // A <= ESCOM_FORCEWALK_RADIUS_M de este punto (durante la escolta/ingreso) el coche SOLO da
    // reversa: obliga al jugador a BAJARSE y entrar a pie por la puerta de la ESCOM. X=lon, Y=lat.
    const val ESCOM_FORCEWALK_LAT = 19.50500
    const val ESCOM_FORCEWALK_LON = -99.14596
    const val ESCOM_FORCEWALK_RADIUS_M = 50.0

    // Escolta: Prankedy te acompaña (fase HIRED) y debes llevarlo a la PUERTA de la ESCOM.
    // Solo se activa en campaña y en el vecindario de la ENCB (ver WorldMapPrankedy.maybeSpawnPrankedyCompanion).
    // Al entrar en arriveRadiusMeters de la puerta se marca cumplido → dispara el cómic + la persecución.
    // El destino REAL se ajusta en runtime a la PUERTA de la ESCOM más cercana A LAS COORDS
    // CANÓNICAS de la ESCOM (no a la de menor latitud, que podía ser la de FES Aragón) (landmark
    // DOORS/ESCOM_DOOR.webp) vía WorldMapCampaignPolice.syncObjectiveToEscomDoor. El radio es
    // pequeño: basta con llegar y quedar AL LADO de la puerta (no hay que interactuar).
    val ESCOLTAR_PRANKEDY = CampaignObjective(
        id = "escoltar_prankedy",
        title = "Lleva a Prankedy a la puerta de la ESCOM",
        description = "Protege a Prankedy y escóltalo hasta la PUERTA de la ESCOM.",
        targetLat = ESCOM_DOOR_LAT,
        targetLon = ESCOM_DOOR_LON,
        arriveRadiusMeters = 12.0
    )

    // Misión 2: tras llegar a la ESCOM, te persiguen y debes INGRESAR a la ESCOM (entrar al
    // edificio). El destino es el centro de la ESCOM con un radio pequeño (al acercarte a la
    // entrada se cumple). Lo activa MainActivity tras el cómic IntroPOW12..14.
    // arriveRadiusMeters = 0 → NO se cumple por cercanía (si fuera por cercanía se cumpliría sola,
    // porque ya estás en la puerta tras la Misión 1). Se cumple al INTERACTUAR/ENTRAR por la puerta
    // de la ESCOM (lo marca WorldMapViewModel al disparar el fade de la puerta). Ver checkObjectiveProgress.
    val INGRESAR_ESCOM = CampaignObjective(
        id = "ingresar_escom",
        title = "Ingresa a la ESCOM",
        description = "¡Te persiguen! Entra por la puerta de la ESCOM para ponerte a salvo.",
        targetLat = ESCOM_DOOR_LAT,
        targetLon = ESCOM_DOOR_LON,
        arriveRadiusMeters = 0.0
    )

    // Objetivo de INTERIORES tras la Misión 1: una vez DENTRO de la ESCOM. NO se muestra en
    // el mapa exterior (ahí queda "Ingresa a la ESCOM, Cumplido"); solo en el interior de la ESCOM.
    // Sin destino real (radio 0): es un objetivo de exploración, no de llegada.
    val BUSCAR_PISTAS_ESCOM = CampaignObjective(
        id = "buscar_pistas_escom",
        title = "Busca pistas en la ESCOM",
        description = "Explora la ESCOM en busca de pistas.",
        targetLat = ESCOM_DOOR_LAT,
        targetLon = ESCOM_DOOR_LON,
        arriveRadiusMeters = 0.0
    )

    private val all = listOf(IR_ENCB, ESCOLTAR_PRANKEDY, INGRESAR_ESCOM, BUSCAR_PISTAS_ESCOM)

    // Objetivo con el que arranca una campaña nueva.
    val first: CampaignObjective = IR_ENCB

    fun byId(id: String?): CampaignObjective? = all.firstOrNull { it.id == id }
}
