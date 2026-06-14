package ovh.gabrielhuav.pow.data.repository

import android.content.Context

// Posición/medidas del CUADRO DE TEXTO de un panel del cómic, como FRACCIONES de la
// pantalla (0..1): dónde empieza (topFrac), qué alto ocupa (heightFrac) y el tamaño de
// letra (fontSp). Cada imagen tiene su recuadro blanco a distinta altura, por eso es
// ajustable por panel desde el editor in-game.
data class StoryBoxLayout(
    val topFrac: Float,
    val heightFrac: Float,
    val fontSp: Float,
    val widthFrac: Float = 0.9f
)

// Persiste los ajustes del cuadro de texto por panel (índice) en SharedPreferences, para
// que lo que ajustes en el editor in-game se conserve. Si un panel no tiene ajuste guardado,
// se usa el default del catálogo (ComicPanel).
class StoryLayoutRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("pow_story_layout", Context.MODE_PRIVATE)

    /** Layout guardado para el panel `index`, o el `default` si no hay ajuste. */
    fun layoutFor(index: Int, default: StoryBoxLayout): StoryBoxLayout {
        if (!prefs.contains("top_$index")) return default
        return StoryBoxLayout(
            topFrac = prefs.getFloat("top_$index", default.topFrac),
            heightFrac = prefs.getFloat("h_$index", default.heightFrac),
            fontSp = prefs.getFloat("font_$index", default.fontSp),
            widthFrac = prefs.getFloat("w_$index", default.widthFrac)
        )
    }

    /** Guarda el ajuste del panel `index`. */
    fun save(index: Int, layout: StoryBoxLayout) {
        prefs.edit()
            .putFloat("top_$index", layout.topFrac)
            .putFloat("h_$index", layout.heightFrac)
            .putFloat("font_$index", layout.fontSp)
            .putFloat("w_$index", layout.widthFrac)
            .apply()
    }

    /** Aplica el mismo ajuste a TODOS los paneles (0 hasta count-1). */
    fun saveAll(count: Int, layout: StoryBoxLayout) {
        val e = prefs.edit()
        for (i in 0 until count) {
            e.putFloat("top_$i", layout.topFrac)
                .putFloat("h_$i", layout.heightFrac)
                .putFloat("font_$i", layout.fontSp)
                .putFloat("w_$i", layout.widthFrac)
        }
        e.apply()
    }
}
