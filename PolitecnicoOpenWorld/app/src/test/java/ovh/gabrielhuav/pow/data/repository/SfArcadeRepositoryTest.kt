package ovh.gabrielhuav.pow.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class SfArcadeRepositoryTest {

    @Test
    fun `un snapshot V1 escrito por la version vieja se lee sin perder datos`() {
        val jsonViejo = """
            {
              "v": 1,
              "playerId": "ESCOMBOY",
              "step": 2,
              "total": 8,
              "mapFile": "null",
              "playerRoundWins": 1,
              "cpuRoundWins": 0,
              "difficulty": "HARD",
              "paused": true,
              "ladderRivals": ["ROBOT", "GOYA"]
            }
        """.trimIndent()

        val decoded = SfArcadeRepository.decodeSession(jsonViejo)
        assertNotNull(decoded)
        val session = requireNotNull(decoded)

        assertEquals("ESCOMBOY", session.playerId)
        assertEquals(2, session.step)
        assertEquals(8, session.total)
        assertEquals(listOf("ROBOT", "GOYA"), session.ladderRivals)
        assertEquals(null, session.mapFile)
        assertEquals(1, session.playerRoundWins)
        assertEquals(0, session.cpuRoundWins)
        assertEquals("HARD", session.difficulty)
        assertEquals("", session.baseDifficulty)
        assertEquals(true, session.paused)
    }

    @Test
    fun `guardar mapFile null omite la clave como hacia JSONObject put`() {
        val session = SfArcadeRepository.ArcadeSession(
            playerId = "ESCOMGIRL",
            step = 1,
            total = 4,
            ladderRivals = listOf("ROBOT"),
            mapFile = null,
            playerRoundWins = 0,
            cpuRoundWins = 1,
            difficulty = "NORMAL",
        )

        val json = SfArcadeRepository.encodeSession(session)

        assertFalse("\"mapFile\"" in json)
        assertEquals(session, SfArcadeRepository.decodeSession(json))
    }
}
