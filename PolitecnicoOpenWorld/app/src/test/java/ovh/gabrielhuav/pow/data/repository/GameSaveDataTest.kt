package ovh.gabrielhuav.pow.data.repository

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de CARACTERIZACIÓN del guardado (GameSaveData ↔ Gson) — lógica pura, sin Android.
 *
 * Fijan dos comportamientos de los que depende la compatibilidad con guardados VIEJOS
 * (documentados en 09 §12 y en el propio modelo):
 * 1. Campos PRIMITIVOS ausentes en el JSON → 0/false (compat automática de misiones nuevas).
 * 2. ⚠️ Gotcha Gson: una LISTA ausente queda **NULL en runtime** aunque el tipo Kotlin sea
 *    no-nulo con default (`completedMissions: List<String> = emptyList()`). Gson NO aplica
 *    los defaults de Kotlin. Por eso `restoreSaveData` hace coalesce defensivo. Este test
 *    PRUEBA que el coalesce sigue siendo necesario: si algún día se migra a kotlinx.serialization
 *    o a un TypeAdapter con defaults, este test se pone rojo y el coalesce puede retirarse.
 */
class GameSaveDataTest {

    private val gson = Gson()

    /** JSON mínimo estilo "guardado antiguo": solo los campos que existían al inicio. */
    private val oldSaveJson = """
        {
          "schoolId": "escom",
          "lat": 19.5046,
          "lon": -99.1459,
          "health": 87.5,
          "wantedLevel": 2,
          "isDriving": false,
          "isDrivingPoliceCar": false,
          "skin": "LAZARO",
          "savedAt": 1750000000000
        }
    """.trimIndent()

    @Test
    fun old_save_missing_primitives_default_to_zero_false() {
        val data = gson.fromJson(oldSaveJson, GameSaveData::class.java)
        // Misiones nuevas sobre guardados viejos: fase 0 = no iniciada, sin arma.
        assertEquals(0, data.mission2Phase)
        assertEquals(0, data.mission3Phase)
        assertFalse(data.hasFirearm)
        assertFalse(data.lab1KeyFound)
        assertFalse(data.objectiveDone)
        assertNull(data.objectiveId)
        assertNull(data.interiorRoomId)
        assertNull(data.saveType)
    }

    @Test
    fun gson_gotcha_missing_list_is_NULL_at_runtime_despite_kotlin_default() {
        val data = gson.fromJson(oldSaveJson, GameSaveData::class.java)
        // ⚠️ ESTE es el gotcha: el tipo dice List<String> no-nulo con default, pero Gson
        // (reflexión, sin kotlin-adapter) deja el campo NULL. El coalesce de restoreSaveData
        // (`data.completedMissions ?: emptyList()`) es OBLIGATORIO mientras esto sea rojo…
        // es decir, mientras esta aserción PASE. Si migras el parser y esto falla, retira
        // el coalesce y actualiza 09 §12.
        @Suppress("SENSELESS_COMPARISON")
        assertTrue(data.completedMissions == null)
        @Suppress("SENSELESS_COMPARISON")
        assertTrue(data.nearbyNpcs == null)
        @Suppress("SENSELESS_COMPARISON")
        assertTrue(data.inventoryKeys == null)
    }

    @Test
    fun round_trip_preserves_mission_progress() {
        val original = GameSaveData(
            schoolId = "escom",
            lat = 19.5,
            lon = -99.1,
            health = 100f,
            wantedLevel = 0,
            isDriving = false,
            isDrivingPoliceCar = false,
            vehicleModel = null,
            vehicleColor = null,
            skin = "LAZARO",
            objectiveId = null,
            objectiveDone = false,
            mission2Phase = 6,   // PHASE_DONE
            mission3Phase = 4,   // PHASE_DONE
            hasFirearm = true,
            completedMissions = listOf("mission1", "mission2", "mission3"),
            saveType = "MANUAL",
            savedAt = 123L
        )
        val restored = gson.fromJson(gson.toJson(original), GameSaveData::class.java)
        assertEquals(original, restored)
    }
}
