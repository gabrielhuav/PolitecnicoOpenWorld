package ovh.gabrielhuav.pow.data.repository

import android.content.Context
import com.google.gson.Gson
import java.io.File

// ─── PARTIDA COMPLETA DEL MODO HISTORIA (JSON, CON SLOTS) ─────────────────────
// Cada partida se guarda como un archivo JSON por SLOT (pow_campaign_save_<n>.json,
// n = 1..SLOT_COUNT) en el almacenamiento interno. Guarda el ESTADO COMPLETO de la
// sesión: escuela, posición (coordenadas), vida, nivel de búsqueda, vehículo, skin,
// NPCs activos cercanos y el objetivo de la campaña. Permite tener varias partidas.

data class GameSaveData(
    val schoolId: String,
    val lat: Double,
    val lon: Double,
    val health: Float,
    val wantedLevel: Int,
    val isDriving: Boolean,
    val isDrivingPoliceCar: Boolean,
    val vehicleModel: String?,   // CarModel.name o null si va a pie
    val vehicleColor: Int?,
    val skin: String,            // PlayerSkin.name
    val nearbyNpcs: List<SavedNpc> = emptyList(),
    val objectiveId: String? = null,   // id del objetivo activo (MissionCatalog)
    val objectiveDone: Boolean = false,
    val savedAt: Long
)

// NPC "congelado" en el guardado: solo lo imprescindible para re-inyectarlo al cargar
// (la IA lo adopta y vuelve a simularlo). Los campos de IA (trait, miedo…) no se guardan.
data class SavedNpc(
    val id: String,
    val type: String,   // NpcType.name
    val lat: Double,
    val lon: Double,
    val health: Float,
    val rotation: Float
)

// Resumen de un slot para pintar el menú de slots (sin exponer todo el JSON).
data class SaveSlotSummary(
    val slot: Int,
    val exists: Boolean,
    val schoolId: String?,
    val savedAt: Long
)

// Lee/escribe las partidas por slot. Tolerante a fallos: si el disco falla o el JSON está
// corrupto, devuelve null/empty (la app sigue, mostrando el slot como vacío).
class SaveGameRepository(context: Context) {

    private val appContext = context.applicationContext
    private val gson = Gson()

    private fun file(slot: Int): File = File(appContext.filesDir, "pow_campaign_save_$slot.json")

    /** Guarda (o sobrescribe) la partida del slot indicado. */
    fun save(slot: Int, data: GameSaveData) {
        if (slot !in 1..SLOT_COUNT) return
        try { file(slot).writeText(gson.toJson(data)) } catch (_: Exception) {}
    }

    /** Lee la partida del slot, o null si no existe / está corrupta. */
    fun load(slot: Int): GameSaveData? = try {
        val f = file(slot)
        if (f.exists()) gson.fromJson(f.readText(), GameSaveData::class.java) else null
    } catch (_: Exception) { null }

    /** ¿El slot tiene una partida? */
    fun hasSave(slot: Int): Boolean = file(slot).exists()

    /** ¿Hay alguna partida en cualquier slot? (habilita "CARGAR PARTIDA" en el menú). */
    fun anySave(): Boolean = (1..SLOT_COUNT).any { hasSave(it) }

    /** Primer slot vacío (para no sobrescribir partidas al COMENZAR una nueva), o 1 si todos llenos. */
    fun firstEmptySlot(): Int = (1..SLOT_COUNT).firstOrNull { !hasSave(it) } ?: 1

    /** Resumen de los SLOT_COUNT slots, para el menú de selección. */
    fun summaries(): List<SaveSlotSummary> = (1..SLOT_COUNT).map { s ->
        val d = load(s)
        SaveSlotSummary(slot = s, exists = d != null, schoolId = d?.schoolId, savedAt = d?.savedAt ?: 0L)
    }

    /** Borra la partida del slot. */
    fun clear(slot: Int) { try { val f = file(slot); if (f.exists()) f.delete() } catch (_: Exception) {} }

    companion object {
        const val SLOT_COUNT = 5
    }
}
