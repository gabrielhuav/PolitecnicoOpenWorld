package ovh.gabrielhuav.pow.domain.models

data class Race(
    val id: String,
    val name: String,
    val description: String,
    val timeLimitSec: Int,
    val finishLat: Double,
    val finishLon: Double,
    val finishRadiusDeg: Double
)

object RaceCatalog {

    // El Entrenador (salida) está en lat=19.50378, lon≈-99.14688 (borde sur de ESCOM).
    // Las metas están FUERA del bbox de ESCOM (lat [19.50356..19.50556])
    // en las calles del campus Zacatenco, sin colliders de edificios.
    // El jugador sale de ESCOM por la entrada y corre por las avenidas.
    // Condición: dist(salida, meta) > radio en todos los casos.

    val races: List<Race> = listOf(

        Race(
            id              = "sprint",
            name            = "Sprint del Zacatenco",
            description     = "Sale de ESCOM y llega a la avenida norte del campus.\n" +
                              "30 segundos. ¡Velocidad pura!",
            timeLimitSec    = 30,
            // Norte de ESCOM, sobre la calle que bordea el campus (~250 m del inicio)
            finishLat       = 19.50620,
            finishLon       = -99.14680,
            finishRadiusDeg = 0.00200   // ~222 m; dist inicio ~270 m ✓
        ),

        Race(
            id              = "campus",
            name            = "Carrera del Campus ESCOM",
            description     = "La clásica. Sale de ESCOM y corre hasta la avenida principal\n" +
                              "del Politécnico. 60 segundos.",
            timeLimitSec    = 60,
            // Más al norte/noreste, sobre una avenida principal del Politécnico (~420 m)
            finishLat       = 19.50760,
            finishLon       = -99.14580,
            finishRadiusDeg = 0.00260   // ~289 m; dist inicio ~420 m ✓
        ),

        Race(
            id              = "ipn",
            name            = "Gran Carrera del IPN ⭐",
            description     = "Inspirada en la Carrera Atlética del IPN que se celebra\n" +
                              "cada año en las calles del Politécnico. 90 segundos.",
            timeLimitSec    = 90,
            // Más lejos aún, rumbo a otras escuelas del Politécnico (~600 m)
            finishLat       = 19.50900,
            finishLon       = -99.14420,
            finishRadiusDeg = 0.00320   // ~356 m; dist inicio ~590 m ✓
        )
    )

    val default: Race get() = races[1]   // Campus como opción predeterminada
}
