package ovh.gabrielhuav.pow.domain.models.zombie

/**
 * Llave del puzzle del Modo Historia (Misión 1). Se dispersan varias en la sala ENCB_lab1; el
 * jugador recoge UNA, la lleva a ENCB_lab2 y la PRUEBA en su inventario hasta dar con la CORRECTA,
 * que desbloquea la puerta del fondo (2ª secuencia de cómic). Si se equivoca, la desecha y vuelve.
 *
 * Assets en `assets/CAMPAIGN/KEYS/`. La llave correcta es `LLave4.png`.
 */
data class KeyDrop(
    val id: String,
    val assetPath: String,
    val x: Float,
    val y: Float,
    val isCorrect: Boolean,
    // Identificador de MISIÓN al que pertenece la llave. Permite distinguir y REUTILIZAR los assets
    // de estas llaves en misiones futuras (filtrar por misión, reasignar la correcta, etc.).
    // Por defecto, la Misión 1 (cadena ENCB: buscar en lab1 → probar en lab2).
    val missionId: String = MISSION_1
) {
    companion object {
        // Misión 1 (cadena ENCB). Úsalo para etiquetar/filtrar las llaves de esta misión.
        const val MISSION_1 = "mission1"
        // Las 5 llaves del puzzle (assets reales en CAMPAIGN/KEYS, ojo con el case: "LLave2/4").
        val LAB1_KEY_ASSETS = listOf(
            "CAMPAIGN/KEYS/Llave.png",
            "CAMPAIGN/KEYS/LLave2.png",
            "CAMPAIGN/KEYS/Llave3.png",
            "CAMPAIGN/KEYS/LLave4.png",
            "CAMPAIGN/KEYS/Llave5.png"
        )
        // La correcta: abre la puerta de avance de ENCB_lab1.
        const val LAB1_CORRECT_KEY = "CAMPAIGN/KEYS/LLave4.png"

        // ── INVENTARIO: identificar la llave por MISIÓN ───────────────────────────────────────
        // Cada entrada de `inventoryKeys` (List<String>, se guarda tal cual en las partidas) se
        // codifica como "missionId|assetPath" para que la llave sea identificable por su MISIÓN
        // (no solo por su asset) y poder REUTILIZAR los assets en misiones futuras. Mantener el
        // tipo String evita romper el formato de guardado. Compatibilidad: una entrada SIN "|" se
        // interpreta como assetPath suelto de MISSION_1 (saves antiguos).
        fun inventoryEntry(missionId: String, assetPath: String): String = "$missionId|$assetPath"
        fun entryAsset(entry: String): String = if ('|' in entry) entry.substringAfter('|') else entry
        fun entryMission(entry: String): String = if ('|' in entry) entry.substringBefore('|') else MISSION_1
    }
}
