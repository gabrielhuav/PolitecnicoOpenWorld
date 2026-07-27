package ovh.gabrielhuav.pow.data.repository

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import ovh.gabrielhuav.pow.data.json.PowJson

/**
 * Tests de CARACTERIZACIÓN del guardado (GameSaveData ↔ JSON) — lógica pura, sin Android.
 *
 * 🍏 ACTUALIZADO EN LA FASE 3 (2026-07-27): el parser ya NO es Gson, es `PowJson`
 * (kotlinx.serialization). Ver `PLAN_MIGRACION_KMP.md`.
 *
 * ⚠️ **EL GOTCHA DE LAS LISTAS NULL YA NO EXISTE.** Este test afirmaba lo contrario y era CORRECTO
 * en su momento: Gson, al construir por reflexión (sin `kotlin-adapter`), se saltaba el constructor
 * y dejaba las listas en **null** aunque el tipo Kotlin fuese no-nulo con default — por eso
 * `restoreSaveData` hacía un coalesce defensivo. Al migrar cambiaron DOS cosas y ambas lo arreglan:
 *  1. `PowJson` sí aplica los valores por defecto declarados.
 *  2. Al dar default a TODOS los campos de `GameSaveData`, Kotlin genera un constructor sin
 *     argumentos, así que hasta Gson dejaría de usar `Unsafe` y aplicaría los defaults.
 * El coalesce de `restoreSaveData` queda como cinturón redundante: **inofensivo, no lo quites sin
 * mirar**, pero ya no es lo único que evita el crash.
 */
class GameSaveDataTest {

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
        val data = PowJson.decodeFromString<GameSaveData>(oldSaveJson)
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
    fun listas_ausentes_ahora_llegan_VACIAS_y_no_null() {
        // Lo contrario de lo que afirmaba este test con Gson. Si esto se pone rojo, alguien
        // rompió los defaults de GameSaveData o cambió las opciones de PowJson.
        val data = PowJson.decodeFromString<GameSaveData>(oldSaveJson)
        assertEquals(emptyList<String>(), data.completedMissions)
        assertEquals(emptyList<SavedNpc>(), data.nearbyNpcs)
        assertEquals(emptyList<String>(), data.inventoryKeys)
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
        val restored = PowJson.decodeFromString<GameSaveData>(PowJson.encodeToString(original))
        assertEquals(original, restored)
    }
}
