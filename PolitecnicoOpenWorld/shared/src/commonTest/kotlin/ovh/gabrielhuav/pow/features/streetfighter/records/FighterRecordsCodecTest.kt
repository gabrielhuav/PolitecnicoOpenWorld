package ovh.gabrielhuav.pow.features.streetfighter.records

import kotlin.test.Test
import kotlin.test.assertEquals

class FighterRecordsCodecTest {
    @Test fun emptyInputReturnsEmpty() =
        assertEquals(RecordsLoadResult.Empty, FighterRecordsCodec.decode(""))

    @Test fun roundTripKeepsData() {
        val data = mapOf("escomboy" to FighterRecord(3, 1))
        val decoded = FighterRecordsCodec.decode(FighterRecordsCodec.encode(data))
        assertEquals(RecordsLoadResult.Loaded(data), decoded)
    }

    @Test fun garbageReturnsCorrupt() =
        assertEquals(RecordsLoadResult.Corrupt, FighterRecordsCodec.decode("escomboy:abc:-1"))

    @Test fun winRateRoundsDown() =
        assertEquals(66, FighterRecord(wins = 2, losses = 1).winRatePercent)
}