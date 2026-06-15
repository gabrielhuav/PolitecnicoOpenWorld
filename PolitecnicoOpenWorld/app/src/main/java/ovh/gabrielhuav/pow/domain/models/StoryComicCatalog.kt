package ovh.gabrielhuav.pow.domain.models

// Panel del cómic de la intro (prólogo de la campaña). `assetPath` apunta a una imagen en
// la carpeta assets/. `text` es el texto que el código dibuja sobre el RECUADRO BLANCO de la
// imagen. Como el recuadro está a distinta altura en cada panel, la posición del cuadro de
// texto es ajustable: `boxTopFrac`/`boxHeightFrac` (fracción 0..1 de la pantalla) y `fontSp`
// son los valores POR DEFECTO; el editor in-game los sobrescribe y los persiste
// (StoryLayoutRepository). Ver StoryIntroScreen.
data class ComicPanel(
    val assetPath: String,
    val text: String,
    val boxTopFrac: Float = 0.70f,
    val boxHeightFrac: Float = 0.24f,
    val fontSp: Float = 15f,
    val boxWidthFrac: Float = 0.9f
)

// Catalogo del comic por escuela. Los 8 paneles del prologo estan en
// assets/STORY/INTRO/IntroPOW1.webp ... IntroPOW8.webp (imagenes HORIZONTALES: la intro
// fuerza orientacion landscape). EDITA el `text` de cada panel con
// el dialogo real de la historia. La posicion del cuadro de texto se ajusta in-game (editor).
object StoryComicCatalog {

    private val escom = listOf(
        ComicPanel("STORY/INTRO/IntroPOW1.webp", "Texto del panel 1..."),
        ComicPanel("STORY/INTRO/IntroPOW2.webp", "Texto del panel 2..."),
        ComicPanel("STORY/INTRO/IntroPOW3.webp", "Texto del panel 3..."),
        ComicPanel("STORY/INTRO/IntroPOW4.webp", "Texto del panel 4..."),
        ComicPanel("STORY/INTRO/IntroPOW5.webp", "Texto del panel 5..."),
        ComicPanel("STORY/INTRO/IntroPOW6.webp", "Texto del panel 6..."),
        ComicPanel("STORY/INTRO/IntroPOW7.webp", "Texto del panel 7..."),
        ComicPanel("STORY/INTRO/IntroPOW8.webp", "Texto del panel 8...")
    )

    // Paneles para la escuela elegida (por ahora todas usan el prologo de ESCOM).
    fun forSchool(schoolId: String): List<ComicPanel> = when (schoolId) {
        else -> escom
    }

    // ─── SEGUNDA PARTE DE LA INTRO / OUTRO de la ENCB (Modo Historia) ─────────
    // Se reproduce al activar el waypoint final de ENCB_LAB2 (ver ZombieRoomCatalog),
    // cerrando el ciclo de exploracion interna. Son 3 paneles HORIZONTALES nuevos en
    // assets/STORY/INTRO/IntroPOW9..11.webp. EDITA el `text` con el dialogo real.
    const val ENCB_OUTRO_ID = "encb_outro"

    private val encbOutro = listOf(
        ComicPanel("STORY/INTRO/IntroPOW9.webp", "Texto del panel 9..."),
        ComicPanel("STORY/INTRO/IntroPOW10.webp", "Texto del panel 10..."),
        ComicPanel("STORY/INTRO/IntroPOW11.webp", "Texto del panel 11...")
    )

    // Devuelve una secuencia narrativa por id (para StoryIntroScreen). ENCB_OUTRO_ID =
    // segunda parte de la intro; cualquier otro id cae al prologo de ESCOM.
    fun sequence(sequenceId: String): List<ComicPanel> = when (sequenceId) {
        ENCB_OUTRO_ID -> encbOutro
        else -> escom
    }
}
