package ovh.gabrielhuav.pow.features.streetfighter.records

data class FighterRecord(val wins: Int = 0, val losses: Int = 0) {
    val total: Int get() = wins + losses
    val winRatePercent: Int get() = if (total == 0) 0 else wins * 100 / total
}

sealed interface RecordsLoadResult {
    data object Empty : RecordsLoadResult
    data class Loaded(val records: Map<String, FighterRecord>) : RecordsLoadResult
    data object Corrupt : RecordsLoadResult
}

/** Formato guardado: "fighterId:wins:losses;fighterId:wins:losses" */
object FighterRecordsCodec {

    fun encode(records: Map<String, FighterRecord>): String =
        records.entries.joinToString(";") { (id, r) -> "$id:${r.wins}:${r.losses}" }

    fun decode(raw: String?): RecordsLoadResult {
        if (raw.isNullOrBlank()) return RecordsLoadResult.Empty
        val result = mutableMapOf<String, FighterRecord>()
        for (entry in raw.split(";")) {
            val parts = entry.split(":")
            if (parts.size != 3) return RecordsLoadResult.Corrupt
            val wins = parts[1].toIntOrNull()
            val losses = parts[2].toIntOrNull()
            if (parts[0].isBlank() || wins == null || losses == null || wins < 0 || losses < 0) {
                return RecordsLoadResult.Corrupt
            }
            result[parts[0]] = FighterRecord(wins, losses)
        }
        return RecordsLoadResult.Loaded(result)
    }

    fun registerResult(
        records: Map<String, FighterRecord>,
        fighterId: String,
        playerWon: Boolean,
    ): Map<String, FighterRecord> {
        val current = records[fighterId] ?: FighterRecord()
        val updated = if (playerWon) current.copy(wins = current.wins + 1)
        else current.copy(losses = current.losses + 1)
        return records + (fighterId to updated)
    }
}