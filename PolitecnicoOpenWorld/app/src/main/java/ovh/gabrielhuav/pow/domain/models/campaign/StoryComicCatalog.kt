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

    // ─── MISIÓN 1 · CHASE: llegada a la ESCOM (Modo Historia) ────────────────
    // Se reproduce al cumplir la escolta de la Misión 1 (llegar a la ESCOM con Prankedy). Son 4
    // paneles HORIZONTALES en assets/STORY/INTRO/IntroPOW12..15.webp (IntroPOW15 cambia según la
    // skin). EDITA el `text`. Tras esta secuencia se retoma la jugabilidad con la persecución
    // final (6 policías) y el objetivo "Ingresa a la ESCOM". (Antes: MISSION2_INTRO_ID.)
    const val MISSION1_CHASE_INTRO_ID = "mission1_chase_intro"

    private val mission1ChaseIntro = listOf(
        ComicPanel("STORY/INTRO/IntroPOW12.webp", ""),
        ComicPanel("STORY/INTRO/IntroPOW13.webp", ""),
        ComicPanel("STORY/INTRO/IntroPOW14.webp", ""),
        ComicPanel("STORY/INTRO/IntroPOW15.webp", "")   // 4º panel (cambia según skin)
    )

    // ─── MISIÓN 2 · "La mochila" (al terminar de hablar con Prankedy → fase 5) ──────
    // Se reproduce cuando la Misión 2 pasa a la fase MOCHILA: explica que hay que ir al salón y
    // usar la LATA APESTOSA. 🆕 Paneles PLACEHOLDER en assets/STORY/INTRO/IntroPOW16..18.webp
    // (imágenes horizontales con recuadro blanco para el texto — SUSTITÚYELAS por el arte real).
    const val MISSION2_BACKPACK_INTRO_ID = "mission2_backpack_intro"

    private val mission2BackpackIntro = listOf(
        ComicPanel("STORY/INTRO/IntroPOW16.webp",
            "Toma, es una lata de surströmming.\nApesta a muerto, wey.",
            boxTopFrac = 0.79f, boxHeightFrac = 0.18f, fontSp = 15f, boxWidthFrac = 0.90f),
        ComicPanel("STORY/INTRO/IntroPOW17.webp",
            "El salón está a reventar de alumnos.\nCon esto sale hasta el maestro.",
            boxTopFrac = 0.79f, boxHeightFrac = 0.18f, fontSp = 15f, boxWidthFrac = 0.90f),
        ComicPanel("STORY/INTRO/IntroPOW18.webp",
            "A la cuenta de tres la avientas y agarras la mochila. ¡Órale!",
            boxTopFrac = 0.79f, boxHeightFrac = 0.18f, fontSp = 15f, boxWidthFrac = 0.90f)
    )

    // ─── MISIÓN 3 · "Regreso a la ENCB" (al completar la Misión 2) ─────────────────
    // Se reproduce al recuperar la mochila (M2 completada): la broma escaló a un brote en la ENCB,
    // ahora acordonada; hay que colarse. 🆕 Paneles PLACEHOLDER IntroPOW19..22.webp — SUSTITÚYELOS.
    const val MISSION3_INTRO_ID = "mission3_intro"

    private val mission3Intro = listOf(
        ComicPanel("STORY/INTRO/IntroPOW19.webp",
            "La broma se salió de control…\ny todo apunta a la ENCB.",
            boxTopFrac = 0.79f, boxHeightFrac = 0.18f, fontSp = 15f, boxWidthFrac = 0.90f),
        ComicPanel("STORY/INTRO/IntroPOW20.webp",
            "La tienen acordonada con granaderos.\nHay que colarse sin que nos vean.",
            boxTopFrac = 0.79f, boxHeightFrac = 0.18f, fontSp = 15f, boxWidthFrac = 0.90f),
        ComicPanel("STORY/INTRO/IntroPOW21.webp",
            "Yo te acompaño… pero esos infectados me dan mala espina.",
            boxTopFrac = 0.79f, boxHeightFrac = 0.18f, fontSp = 15f, boxWidthFrac = 0.90f),
        ComicPanel("STORY/INTRO/IntroPOW22.webp",
            "Si nos alcanza uno… ni modo, corremos. Vamos por esa evidencia.",
            boxTopFrac = 0.79f, boxHeightFrac = 0.18f, fontSp = 15f, boxWidthFrac = 0.90f)
    )

    // ─── MISIÓN 3 · CIERRE (al recuperar la evidencia y salir de la ENCB) ──────────
    // La prueba está en la mano… pero ¿a quién se la llevas? Gancho directo a la M4.
    // 🆕 Paneles PLACEHOLDER IntroPOW23..24.webp — SUSTITÚYELOS por el arte real.
    const val MISSION3_OUTRO_ID = "mission3_outro"

    private val mission3Outro = listOf(
        ComicPanel("STORY/INTRO/IntroPOW23.webp",
            "Esto lo prueba todo.",
            boxTopFrac = 0.79f, boxHeightFrac = 0.18f, fontSp = 15f, boxWidthFrac = 0.90f),
        ComicPanel("STORY/INTRO/IntroPOW24.webp",
            "¿Y ahora a QUIÉN se lo llevamos?",
            boxTopFrac = 0.79f, boxHeightFrac = 0.18f, fontSp = 15f, boxWidthFrac = 0.90f)
    )

    // ─── MISIÓN 4 · "Código Rojo en Zacatenco" (PLANEADA — aún sin misión jugable) ──
    // Secuencias LISTAS en el catálogo con paneles PLACEHOLDER (IntroPOW25..30.webp) para que el
    // arte y el cableo no bloqueen el diseño de la M4. CABLEAR al crear la misión (mismo patrón
    // bandera→LaunchedEffect→ruta que MISSION3_INTRO). Guion: nadie les cree la evidencia → la
    // radio médica confirma mordidas → primera HORDA en Av. IPN → la paramédica jefa recluta al
    // jugador (evacuación). El outro siembra la M5: un evacuado viene mordido.
    const val MISSION4_INTRO_ID = "mission4_intro"
    const val MISSION4_OUTRO_ID = "mission4_outro"

    private val mission4Intro = listOf(
        ComicPanel("STORY/INTRO/IntroPOW25.webp",
            "Trai un frasco con moco verde y dice que hay zombis…\nAjá. Siguiente.",
            boxTopFrac = 0.79f, boxHeightFrac = 0.18f, fontSp = 15f, boxWidthFrac = 0.90f),
        ComicPanel("STORY/INTRO/IntroPOW26.webp",
            "…múltiples heridos por MORDEDURA en Av. IPN.\nSolicito apoyo, ¡el que sea!",
            boxTopFrac = 0.79f, boxHeightFrac = 0.18f, fontSp = 15f, boxWidthFrac = 0.90f),
        ComicPanel("STORY/INTRO/IntroPOW27.webp",
            "¡¡CORRAN!!",
            boxTopFrac = 0.79f, boxHeightFrac = 0.18f, fontSp = 15f, boxWidthFrac = 0.90f),
        ComicPanel("STORY/INTRO/IntroPOW28.webp",
            "¿Traes pruebas y un fierro? Perfecto.\nAyúdame a evacuar y te consigo a alguien que SÍ te escuche.",
            boxTopFrac = 0.79f, boxHeightFrac = 0.18f, fontSp = 15f, boxWidthFrac = 0.90f)
    )

    private val mission4Outro = listOf(
        ComicPanel("STORY/INTRO/IntroPOW29.webp",
            "Salimos todos. Buen trabajo, wey.",
            boxTopFrac = 0.79f, boxHeightFrac = 0.18f, fontSp = 15f, boxWidthFrac = 0.90f),
        ComicPanel("STORY/INTRO/IntroPOW30.webp",
            "Doctora… este viene MORDIDO.",
            boxTopFrac = 0.79f, boxHeightFrac = 0.18f, fontSp = 15f, boxWidthFrac = 0.90f)
    )

    // Devuelve una secuencia narrativa por id (para StoryIntroScreen). ENCB_OUTRO_ID =
    // segunda parte de la intro; MISSION1_CHASE_INTRO_ID = llegada a la ESCOM; MISSION2_BACKPACK /
    // MISSION3_INTRO = puentes narrativos M2→M3; MISSION3_OUTRO = cierre de la M3; MISSION4_* =
    // reservadas para la M4 (aún sin cablear); cualquier otro id cae al prologo de ESCOM.
    fun sequence(sequenceId: String): List<ComicPanel> = when (sequenceId) {
        ENCB_OUTRO_ID -> encbOutro
        MISSION1_CHASE_INTRO_ID -> mission1ChaseIntro
        MISSION2_BACKPACK_INTRO_ID -> mission2BackpackIntro
        MISSION3_INTRO_ID -> mission3Intro
        MISSION3_OUTRO_ID -> mission3Outro
        MISSION4_INTRO_ID -> mission4Intro
        MISSION4_OUTRO_ID -> mission4Outro
        else -> escom
    }
}