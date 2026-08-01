package ovh.gabrielhuav.pow.data.repository

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import ovh.gabrielhuav.pow.data.json.PowJson
import java.io.File

// ─── PARTIDA COMPLETA DEL MODO HISTORIA (JSON, CON SLOTS) ─────────────────────
// Cada partida se guarda como un archivo JSON por SLOT (pow_campaign_save_<n>.json,
// n = 1..SLOT_COUNT) en el almacenamiento interno. Guarda el ESTADO COMPLETO de la
// sesión: escuela, posición (coordenadas), vida, nivel de búsqueda, vehículo, skin,
// NPCs activos cercanos y el objetivo de la campaña. Permite tener varias partidas.

// ⚠️⚠️ TODOS los campos LLEVAN VALOR POR DEFECTO, y no es por estilo: es lo que mantiene vivas
// las partidas guardadas de los jugadores. Gson rellenaba solo los campos AUSENTES (null/0/false);
// kotlinx.serialization, en cambio, LANZA EXCEPCIÓN si falta un campo sin default → la partida
// NO CARGARÍA. Al migrar de Gson (Fase 3) se añadió default a los 11 que no lo tenían.
// **Si añades un campo nuevo, dale SIEMPRE un default** o romperás los guardados existentes.
@Serializable
data class GameSaveData(
    val schoolId: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val health: Float = 0f,
    val wantedLevel: Int = 0,
    val isDriving: Boolean = false,
    val isDrivingPoliceCar: Boolean = false,
    val vehicleModel: String? = null,   // CarModel.name o null si va a pie
    val vehicleColor: Int? = null,
    val skin: String = "",              // PlayerSkin.name
    val nearbyNpcs: List<SavedNpc> = emptyList(),
    val objectiveId: String? = null,   // id del objetivo activo (MissionCatalog)
    val objectiveDone: Boolean = false,
    // Sala de interiores donde se guardó la partida (id del ZombieRoomCatalog) o null si
    // se guardó en el MAPA GLOBAL. Al CARGAR, si no es null se reentra a ese interior.
    val interiorRoomId: String? = null,
    // INVENTARIO (assetPaths de llaves recogidas) y progreso del puzzle de ENCB_lab1. Default
    // vacío/false para compatibilidad con guardados antiguos (Gson los deja así).
    val inventoryKeys: List<String> = emptyList(),
    val lab1KeyFound: Boolean = false,
    // MISIÓN 2 · "El rumor": fase de la máquina de estados (Mission2.PHASE_*). 0 = no iniciada.
    // Int primitivo → Gson deja 0 en guardados antiguos (compatibilidad automática).
    val mission2Phase: Int = 0,
    // MISIÓN 3 · "Regreso a la ENCB": fase (Mission3.PHASE_*). 0 = no iniciada.
    val mission3Phase: Int = 0,
    // Recompensa de la Misión 3: primera arma de fuego (desbloquea RANGED en campaña).
    // Boolean primitivo → false en guardados antiguos.
    val hasFirearm: Boolean = false,
    // ECONOMÍA: dinero del jugador (coleccionables + recompensas de misión).
    // Int primitivo → 0 en guardados antiguos (compatibilidad automática).
    val playerMoney: Int = 0,
    // REGISTRO DE MISIONES: ids completadas (MissionCatalog.MISSION_*_ID). ⚠️ Gson deja NULL
    // las listas ausentes en guardados antiguos → coalesce al leer (restoreSaveData ya recibe
    // el default emptyList() solo en escrituras nuevas; ver gotcha de listas de Gson en 09).
    val completedMissions: List<String> = emptyList(),
    // Tipo de guardado: "MANUAL" (el jugador eligió slot) o "AUTO" (al salir/cerrar la app).
    // Nullable por compatibilidad con guardados antiguos (Gson los deja en null).
    val saveType: String? = null,
    val savedAt: Long = 0L,
)

// NPC "congelado" en el guardado: solo lo imprescindible para re-inyectarlo al cargar
// (la IA lo adopta y vuelve a simularlo). Los campos de IA (trait, miedo…) no se guardan.
@Serializable
data class SavedNpc(
    val id: String = "",
    val type: String = "",   // NpcType.name
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val health: Float = 0f,
    val rotation: Float = 0f,
)

// Resumen de un slot para pintar el menú de slots (sin exponer todo el JSON).
data class SaveSlotSummary(
    val slot: Int,
    val exists: Boolean,
    val schoolId: String?,
    val savedAt: Long,
    val interiorRoomId: String? = null,   // null = mapa global; si no, sala de interiores
    val saveType: String? = null          // "MANUAL" / "AUTO"
)

// Lee/escribe las partidas por slot. Tolerante a fallos: si el disco falla o el JSON está
// corrupto, devuelve null/empty (la app sigue, mostrando el slot como vacío).
class SaveGameRepository(context: Context) {

    private val appContext = context.applicationContext

    private fun file(slot: Int): File = File(appContext.filesDir, "pow_campaign_save_$slot.json")

    /** Guarda (o sobrescribe) la partida del slot indicado. */
    fun save(slot: Int, data: GameSaveData) {
        if (slot !in 1..SLOT_COUNT) return
        try { file(slot).writeText(PowJson.encodeToString(data)) } catch (_: Exception) {}
    }

    /** Lee la partida del slot, o null si no existe / está corrupta. */
    fun load(slot: Int): GameSaveData? = try {
        val f = file(slot)
        if (f.exists()) PowJson.decodeFromString<GameSaveData>(f.readText()) else null
    } catch (_: Exception) { null }

    /** ¿El slot tiene una partida? */
    fun hasSave(slot: Int): Boolean = file(slot).exists()

    /** ¿Hay alguna partida en cualquier slot? (habilita "CARGAR PARTIDA" en el menú). */
    fun anySave(): Boolean = (1..SLOT_COUNT).any { hasSave(it) }

    /** Primer slot MANUAL vacío (para no sobrescribir partidas al COMENZAR una nueva). */
    fun firstEmptyManualSlot(): Int = MANUAL_SLOTS.firstOrNull { !hasSave(it) } ?: MANUAL_SLOTS.first()

    /** ¿El slot es de AUTO-GUARDADO (reservado, no se puede guardar manualmente en él)? */
    fun isAutoSlot(slot: Int): Boolean = slot in AUTO_SLOTS

    /** Slot de AUTO-GUARDADO donde escribir ahora: el vacío primero; si los dos están llenos,
     *  el más ANTIGUO (rota entre los 2 auto-slots → siempre se conservan los 2 más recientes). */
    fun nextAutoSlot(): Int {
        AUTO_SLOTS.firstOrNull { !hasSave(it) }?.let { return it }
        return AUTO_SLOTS.minByOrNull { load(it)?.savedAt ?: 0L } ?: AUTO_SLOTS.first()
    }

    /** Borra ambos slots de AUTO-GUARDADO (al COMENZAR una partida nueva, empiezan limpios). */
    fun clearAutoSlots() { AUTO_SLOTS.forEach { clear(it) } }

    /** Resumen de los SLOT_COUNT slots, para el menú de selección. */
    fun summaries(): List<SaveSlotSummary> = (1..SLOT_COUNT).map { s ->
        val d = load(s)
        SaveSlotSummary(
            slot = s,
            exists = d != null,
            schoolId = d?.schoolId,
            savedAt = d?.savedAt ?: 0L,
            interiorRoomId = d?.interiorRoomId,
            saveType = d?.saveType
        )
    }

    /** Borra la partida del slot. */
    fun clear(slot: Int) { try { val f = file(slot); if (f.exists()) f.delete() } catch (_: Exception) {} }

    companion object {
        // 7 slots en total: 2 de AUTO-GUARDADO (1,2 — reservados, no editables a mano) +
        // 5 de guardado MANUAL (3..7).
        const val SLOT_COUNT = 7
        val AUTO_SLOTS = listOf(1, 2)
        val MANUAL_SLOTS = (3..7).toList()
    }
}
