package ovh.gabrielhuav.pow.data.repository

import android.content.Context
import com.google.gson.Gson
import java.io.File

// ─── PARTIDA COMPLETA DEL MODO HISTORIA (JSON) ────────────────────────────────
// A diferencia de CampaignRepository (que solo guarda la ESCUELA + fecha en
// SharedPreferences para habilitar "CARGAR PARTIDA" en el menú), aquí guardamos el
// ESTADO COMPLETO de la sesión en un archivo JSON dentro del almacenamiento interno
// de la app: posición (coordenadas), vida, nivel de búsqueda, vehículo, skin y los
// NPCs activos cercanos. Es la fuente de verdad de "CARGAR PARTIDA".

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

// Lee/escribe la partida completa como un único archivo JSON. Tolerante a fallos:
// si el disco falla o el JSON está corrupto, devuelve null (la partida ligera de
// CampaignRepository sigue habilitando el menú con un spawn por defecto).
class SaveGameRepository(context: Context) {

    private val appContext = context.applicationContext
    private val gson = Gson()
    private val file: File get() = File(appContext.filesDir, SAVE_FILE)

    /** Guarda (o sobrescribe) la partida completa en JSON. */
    fun save(data: GameSaveData) {
        try {
            file.writeText(gson.toJson(data))
        } catch (_: Exception) { /* sin disco: la partida ligera sigue funcionando */ }
    }

    /** Lee la partida completa, o null si no existe / está corrupta. */
    fun load(): GameSaveData? = try {
        if (file.exists()) gson.fromJson(file.readText(), GameSaveData::class.java) else null
    } catch (_: Exception) { null }

    /** ¿Hay una partida completa guardada en JSON? */
    fun hasSave(): Boolean = file.exists()

    /** Borra la partida completa. */
    fun clear() {
        try { if (file.exists()) file.delete() } catch (_: Exception) {}
    }

    companion object {
        private const val SAVE_FILE = "pow_campaign_save.json"
    }
}
