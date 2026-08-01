package ovh.gabrielhuav.pow.features.streetfighter.data

import ovh.gabrielhuav.pow.data.json.PowJson
import ovh.gabrielhuav.pow.data.json.jsonOf
import kotlin.test.Test
import kotlin.test.assertEquals

class SfPlayerStateJsonTest {

    @Test
    fun `PLAYER_STATE convierte fireballs serializables sin crashear`() {
        val fireball = SfNetFireball(
            x = 321.5f,
            y = 144.25f,
            dir = -1,
            strength = "HEAVY",
            state = "FLYING",
            frame = 3,
        )

        val raw = jsonOf(
            sfPlayerStatePayload(
                x = 100f,
                y = 200f,
                state = "SPECIAL",
                frame = 4,
                dir = 1,
                hp = 87,
                timer = 42,
                fireballs = listOf(fireball),
                meter = 60,
                audio = emptyList(),
            ),
        )
        val decoded = PowJson.decodeFromString<SfNetMsg>(raw)

        assertEquals("PLAYER_STATE", decoded.type)
        assertEquals(listOf(fireball), decoded.fireballs)
        assertEquals(60, decoded.meter)
        assertEquals(null, decoded.audio)
    }
}
