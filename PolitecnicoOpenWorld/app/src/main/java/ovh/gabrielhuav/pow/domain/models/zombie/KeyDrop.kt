package ovh.gabrielhuav.pow.domain.models.zombie

/**
 * Llave del puzzle del Modo Historia (Misión 1) en la sala ENCB_lab1. Se dispersan varias por
 * la escena; el jugador debe pasar sobre cada una, inspeccionarla y probarla hasta encontrar la
 * CORRECTA, que abre la puerta de avance a la siguiente sala.
 *
 * Assets en `assets/CAMPAIGN/KEYS/`. La llave correcta es `LLave4.png`.
 */
data class KeyDrop(
    val id: String,
    val assetPath: String,
    val x: Float,
    val y: Float,
    val isCorrect: Boolean
) {
    companion object {
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
    }
}
