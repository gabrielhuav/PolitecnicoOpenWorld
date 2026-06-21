package ovh.gabrielhuav.pow.domain.models.campaign

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
        ComicPanel(
            "STORY/INTRO/IntroPOW1.webp",
            "Este es un buen lugar.\nAgarremos a este wey para la broma",
            boxTopFrac = 0.772f, boxHeightFrac = 0.200f, fontSp = 15f, boxWidthFrac = 0.940f
        ),
        ComicPanel(
            "STORY/INTRO/IntroPOW2.webp",
            "¡Chin! Ya valió",
            boxTopFrac = 0.830f, boxHeightFrac = 0.180f, fontSp = 15f, boxWidthFrac = 0.720f),
        ComicPanel(
            "STORY/INTRO/IntroPOW3.webp",
            "A ver perro, contestame.\n¿Qué te pasa?",
            boxTopFrac = 0.782f, boxHeightFrac = 0.200f, fontSp = 15f, boxWidthFrac = 0.940f),
        ComicPanel(
            "STORY/INTRO/IntroPOW4.webp",
            "Córrele gordo.\nNo te irás a ninguna parte.",
            boxTopFrac = 0.835f, boxHeightFrac = 0.140f, fontSp = 15f, boxWidthFrac = 0.900f),
        ComicPanel(
            "STORY/INTRO/IntroPOW5.webp",
            "Inche viejo. Por aquí puedo perderlo",
            boxTopFrac = 0.840f, boxHeightFrac = 0.140f, fontSp = 15f, boxWidthFrac = 0.940f),
        ComicPanel(
            "STORY/INTRO/IntroPOW6.webp",
            "",
            boxTopFrac = 0.700f, boxHeightFrac = 0.240f, fontSp = 15f, boxWidthFrac = 0.900f),
        ComicPanel(
            "STORY/INTRO/IntroPOW7.webp",
            "¿No esta vacío? No importa, me tengo que esconder",
            boxTopFrac = 0.779f, boxHeightFrac = 0.190f, fontSp = 15f, boxWidthFrac = 0.920f),
        ComicPanel(
            "STORY/INTRO/IntroPOW8.webp",
            "¡Llévense al perro! A ver si muy salsa.",
            boxTopFrac = 0.786f, boxHeightFrac = 0.180f, fontSp = 15f, boxWidthFrac = 0.920f)
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
        ComicPanel("STORY/INTRO/IntroPOW9.webp", "¿Quién es?...",
            boxTopFrac = 0.860f, boxHeightFrac = 0.110f, fontSp = 15f, boxWidthFrac = 0.820f),
        ComicPanel("STORY/INTRO/IntroPOW10.webp", "Relax, relax.\n",
            boxTopFrac = 0.856f, boxHeightFrac = 0.110f, fontSp = 16f, boxWidthFrac = 0.720f),
        ComicPanel("STORY/INTRO/IntroPOW11.webp", "Me metí en un pedo y necesito tu ayuda\nNo sé salir de aquí, ayúdame.",
            boxTopFrac = 0.853f, boxHeightFrac = 0.120f, fontSp = 15f, boxWidthFrac = 0.720f)
    )

    // ─── MISIÓN 2: llegada a la ESCOM (Modo Historia) ────────────────────────
    // Se reproduce al cumplir la Misión 1 (llegar a la ESCOM con Prankedy). Son 4 paneles
    // HORIZONTALES en assets/STORY/INTRO/IntroPOW12..15.webp (IntroPOW15 cambia según la skin). EDITA el `text`.
    // Tras esta secuencia se retoma la jugabilidad con la persecución (6 policías) y el
    // objetivo "Ingresa a la ESCOM".
    const val MISSION2_INTRO_ID = "mission2_intro"

    private val mission2Intro = listOf(
        ComicPanel("STORY/INTRO/IntroPOW12.webp", ""),
        ComicPanel("STORY/INTRO/IntroPOW13.webp", ""),
        ComicPanel("STORY/INTRO/IntroPOW14.webp", ""),
        ComicPanel("STORY/INTRO/IntroPOW15.webp", "")   // 4º panel (cambia según skin)
    )

    // Devuelve una secuencia narrativa por id (para StoryIntroScreen). ENCB_OUTRO_ID =
    // segunda parte de la intro; MISSION2_INTRO_ID = llegada a la ESCOM; cualquier otro id
    // cae al prologo de ESCOM.
    fun sequence(sequenceId: String): List<ComicPanel> = when (sequenceId) {
        ENCB_OUTRO_ID -> encbOutro
        MISSION2_INTRO_ID -> mission2Intro
        else -> escom
    }
}