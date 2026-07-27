package ovh.gabrielhuav.pow.data.repository

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import com.russhwolf.settings.Settings

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

    // 🍏 Fase 4: mismo fichero de prefs, API multiplataforma.
    private val prefs: Settings = SharedPreferencesSettings(
        context.applicationContext.getSharedPreferences("pow_story_layout", Context.MODE_PRIVATE),
    )

    /** Layout guardado para el panel `index`, o el `default` si no hay ajuste. */
    fun layoutFor(index: Int, default: StoryBoxLayout): StoryBoxLayout {
        if (!prefs.hasKey("top_$index")) return default
        return StoryBoxLayout(
            topFrac = prefs.getFloat("top_$index", default.topFrac),
            heightFrac = prefs.getFloat("h_$index", default.heightFrac),
            fontSp = prefs.getFloat("font_$index", default.fontSp),
            widthFrac = prefs.getFloat("w_$index", default.widthFrac)
        )
    }

    /** Guarda el ajuste del panel `index`. */
    fun save(index: Int, layout: StoryBoxLayout) {
        prefs.putFloat("top_$index", layout.topFrac)
        prefs.putFloat("h_$index", layout.heightFrac)
        prefs.putFloat("font_$index", layout.fontSp)
        prefs.putFloat("w_$index", layout.widthFrac)
    }

    /** Aplica el mismo ajuste a TODOS los paneles (0 hasta count-1). */
    fun saveAll(count: Int, layout: StoryBoxLayout) {
        for (i in 0 until count) {
            prefs.putFloat("top_$i", layout.topFrac)
            prefs.putFloat("h_$i", layout.heightFrac)
            prefs.putFloat("font_$i", layout.fontSp)
            prefs.putFloat("w_$i", layout.widthFrac)
        }
    }
}
