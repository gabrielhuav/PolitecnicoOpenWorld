package ovh.gabrielhuav.pow.domain.models

// Panel del cómic de la intro (prólogo de la campaña). `assetPath` apunta a una imagen en
// la carpeta assets/ (recomendado .webp 1080×1920, con un recuadro BLANCO en la parte de
// abajo para el texto). `text` es el texto de la historia que el código dibuja sobre ese
// recuadro blanco (ver StoryIntroScreen).
data class ComicPanel(
    val assetPath: String,
    val text: String
)

// Catálogo del cómic por escuela. ⚠️ EDITA esto: pon tus imágenes en
// app/src/main/assets/story/ y cambia el texto. Puedes añadir o quitar paneles
// libremente; la pantalla de intro soporta cualquier número.
object StoryComicCatalog {

    private val escom = listOf(
        ComicPanel(
            "story/panel_01.webp",
            "En la ENCB del Politécnico, Prankedy mezcla por accidente una sustancia corrosiva…"
        ),
        ComicPanel(
            "story/panel_02.webp",
            "La sustancia se propaga y algo empieza a salir terriblemente mal."
        ),
        ComicPanel(
            "story/panel_03.webp",
            "Estás en la ESCOM. Es momento de averiguar qué fue lo que pasó."
        )
    )

    // Paneles para la escuela elegida (por ahora todas usan el prólogo de ESCOM).
    fun forSchool(schoolId: String): List<ComicPanel> = when (schoolId) {
        else -> escom
    }
}
