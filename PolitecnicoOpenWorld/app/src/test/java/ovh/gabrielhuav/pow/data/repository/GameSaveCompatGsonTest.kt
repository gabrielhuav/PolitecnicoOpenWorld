package ovh.gabrielhuav.pow.data.repository

import com.google.gson.Gson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import ovh.gabrielhuav.pow.data.json.PowJson

/**
 * 🍏 COMPATIBILIDAD DE PARTIDAS GUARDADAS — red de seguridad de la Fase 3
 * (`README for IAS/PLAN_MIGRACION_KMP.md`).
 *
 * POR QUÉ: al cambiar Gson por kotlinx.serialization, lo que está en juego son **las partidas de
 * los jugadores que ya tienen la 1.0.0.14 instalada**. Un save es un archivo JSON en el
 * almacenamiento interno; si el parser nuevo no lo lee EXACTAMENTE igual, el jugador pierde la
 * partida. Y eso no lo detecta ningún compilador.
 *
 * Este test vive en `:app` porque es el único sitio donde conviven Gson (el de antes) y
 * kotlinx (el de ahora), así que puede comparar uno contra otro de verdad.
 *
 * ⚠️ NO borres este test hasta que Gson desaparezca del proyecto Y haya pasado un release en
 * producción sin quejas de guardados perdidos.
 */
class GameSaveCompatGsonTest {

    private val gson = Gson()

    /** Un save "completo", como el que escribe la versión actual. */
    private fun saveCompleto() = GameSaveData(
        schoolId = "ESCOM",
        lat = 19.504603, lon = -99.145985,
        health = 87.5f, wantedLevel = 3,
        isDriving = true, isDrivingPoliceCar = false,
        vehicleModel = "VOCHO", vehicleColor = -16711936,
        skin = "LAZARO",
        nearbyNpcs = listOf(SavedNpc("npc-1", "STUDENT", 19.5, -99.1, 100f, 45f)),
        objectiveId = "M1_OBJ_3", objectiveDone = true,
        interiorRoomId = null,
        inventoryKeys = listOf("KEY_LAB1"), lab1KeyFound = true,
        mission2Phase = 2, mission3Phase = 0, hasFirearm = true,
        playerMoney = 1250,
        completedMissions = listOf("MISSION_1", "MISSION_2"),
        saveType = "MANUAL", savedAt = 1_753_600_000_000L,
    )

    @Test
    fun `kotlinx LEE un save escrito por Gson (el caso del jugador que actualiza)`() {
        val jsonDeGson = gson.toJson(saveCompleto())
        val leido = PowJson.decodeFromString<GameSaveData>(jsonDeGson)
        assertEquals(saveCompleto(), leido)
    }

    @Test
    fun `Gson LEE un save escrito por kotlinx (por si hay que revertir la version)`() {
        val jsonDeKotlinx = PowJson.encodeToString(saveCompleto())
        val leido = gson.fromJson(jsonDeKotlinx, GameSaveData::class.java)
        assertEquals(saveCompleto(), leido)
    }

    @Test
    fun `el JSON que produce kotlinx es EL MISMO que producia Gson`() {
        // Si esto falla, el formato en disco cambió: revisa las opciones de PowJson
        // (encodeDefaults / explicitNulls son las que mandan aquí).
        val deGson = gson.toJson(saveCompleto())
        val deKotlinx = PowJson.encodeToString(saveCompleto())
        assertEquals(normalizar(deGson), normalizar(deKotlinx))
    }

    @Test
    fun `un save VIEJO al que le faltan campos NUEVOS sigue cargando`() {
        // Este es EL caso peligroso: kotlinx lanza excepción ante un campo ausente que no tenga
        // default. Es exactamente lo que le pasaría a quien tenga una partida de hace versiones.
        val saveAntiguo = """
            {"schoolId":"ESCOM","lat":19.5,"lon":-99.1,"health":100.0,"wantedLevel":0,
             "isDriving":false,"isDrivingPoliceCar":false,"skin":"LAZARO","savedAt":1700000000000}
        """.trimIndent()
        val leido = PowJson.decodeFromString<GameSaveData>(saveAntiguo)
        assertEquals("ESCOM", leido.schoolId)
        assertEquals(0, leido.playerMoney)             // campo nuevo -> default
        assertEquals(emptyList<String>(), leido.completedMissions)
        assertEquals(null, leido.saveType)
        assertEquals(false, leido.hasFirearm)
    }

    @Test
    fun `un save con campos DESCONOCIDOS (de una version mas nueva) no revienta`() {
        // Al revés: alguien vuelve a una versión anterior. Gson ignoraba lo que no conocía;
        // kotlinx solo lo hace con ignoreUnknownKeys = true (ver PowJson).
        val saveDelFuturo = """
            {"schoolId":"ESCOM","lat":19.5,"lon":-99.1,"health":100.0,"wantedLevel":0,
             "isDriving":false,"isDrivingPoliceCar":false,"skin":"LAZARO","savedAt":1700000000000,
             "campoQueAunNoExiste":"algo","otroMas":{"anidado":true}}
        """.trimIndent()
        assertNotNull(PowJson.decodeFromString<GameSaveData>(saveDelFuturo))
    }

    @Test
    fun `ida y vuelta con kotlinx conserva TODO`() {
        val original = saveCompleto()
        assertEquals(original, PowJson.decodeFromString<GameSaveData>(PowJson.encodeToString(original)))
    }

    /** Compara ignorando el ORDEN de las claves: lo que importa es el contenido, no el orden. */
    private fun normalizar(json: String): String =
        json.trim().removePrefix("{").removeSuffix("}")
            .split(Regex(",(?=\")")).map { it.trim() }.sorted().joinToString(",")
}
