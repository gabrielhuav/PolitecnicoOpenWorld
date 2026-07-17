package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.random.Random

/**
 * ESCALERA del MODO ARCADE (HUELUM VS. GOYA, versión POW). 11 peleas, DATA-DRIVEN: dado el
 * peleador que ELIGIÓ el jugador (uno de los 3 estudiantes de arranque), devuelve la
 * secuencia ORDENADA de rivales + el mapa ligado a cada escalón. Aislado y puro (sin
 * Android) para poder cambiarse sin tocar el VM. Ver README for IAS/DISENO_ARCADE_SF_POW.md.
 *
 * Estructura (el jugador elige 1 de {ESCOMBOY, ESCOMGIRL, ROBOT}):
 *   1-2  : los OTROS 2 estudiantes (orden ALEATORIO)
 *   3-5  : PARAMEDICO_CRUZ_ROJA, SENOR_TIENDA, PAPARAZZI_1 (orden ALEATORIO)
 *   6-9  : POLICIA_CDMX_HOMBRE, POLICIA_CDMX (mujer), POLICIA_GRANADERO_HOMBRE,
 *          POLICIA_GRANADERO_MUJER (orden FIJO). Ya con arte dedicado (2026-07-17).
 *   10   : REY_GRUPERO (SEMIFINAL)
 *   11   : PRANKEDY (FINAL)
 *
 * Mapas: "ligado al rival" pero solo hay 6 mapas para 11 peleas, y la asociación intermedia
 * la dará el dueño. Por ahora FIJOS: escalón 1 = Queso IPN (ya desbloqueado) y escalón 11
 * (final) = CU UNAM. Los intermedios llevan `mapFile = null` (TBD): el VM mantiene el mapa
 * anterior hasta que se defina cuál desbloquea cada rival.
 */
object SfArcadeLadder {

    const val MAP_FIRST = "fondo_IPN_QUESO_1.png"                 // Queso IPN (escalón 1)
    const val MAP_FINAL = "fondo_UNAM_bibliotecaCentral_1.png"    // "Ciudad Universitaria UNAM" (escalón 11)

    /** Los 3 estudiantes desbloqueados de arranque; el jugador elige uno. */
    val STARTERS = listOf(SfFighterId.ESCOMBOY, SfFighterId.ESCOMGIRL, SfFighterId.ROBOT)

    /**
     * TODOS los personajes que participan en el arcade (desbloqueables jugando), en orden de
     * aparición aproximado. Lo usa el selector para pintar los bloqueados con candado 🔒.
     */
    val ALL_PARTICIPANTS = listOf(
        SfFighterId.ESCOMBOY, SfFighterId.ESCOMGIRL, SfFighterId.ROBOT,
        SfFighterId.PARAMEDICO_CRUZ_ROJA, SfFighterId.SENOR_TIENDA, SfFighterId.PAPARAZZI_1,
        SfFighterId.POLICIA_CDMX_HOMBRE, SfFighterId.POLICIA_CDMX,
        SfFighterId.POLICIA_GRANADERO_HOMBRE, SfFighterId.POLICIA_GRANADERO_MUJER,
        SfFighterId.REY_GRUPERO, SfFighterId.PRANKEDY,
    )

    /** Un escalón de la escalera: rival, mapa (null = conservar el anterior) y si es jefe. */
    data class Step(
        val index: Int,               // 1..11 (para HUD "PELEA N / 11")
        val rival: SfFighterId,
        val mapFile: String?,         // null = TBD (el VM conserva el mapa vigente)
        val isBoss: Boolean = false,  // semifinal o final → dificultad tope + banner especial
    )

    /** Total de peleas de la escalera. */
    const val TOTAL_FIGHTS = 11

    /**
     * Arma la secuencia de 11 rivales para el `player` elegido. `rng` inyectable para que
     * online/tests sean deterministas; por defecto azar real. Si `player` no es uno de los
     * 3 estudiantes, se asume ESCOMBOY (defensivo).
     */
    fun build(player: SfFighterId, rng: Random = Random.Default): List<Step> {
        val steps = mutableListOf<SfFighterId>()

        // 1-2: los otros 2 estudiantes (aleatorio)
        val others = STARTERS.filter { it != player }.let {
            if (it.size == 2) it else listOf(SfFighterId.ESCOMGIRL, SfFighterId.ROBOT)
        }
        steps += others.shuffled(rng)

        // 3-5: trío intermedio (aleatorio)
        steps += listOf(
            SfFighterId.PARAMEDICO_CRUZ_ROJA,
            SfFighterId.SENOR_TIENDA,
            SfFighterId.PAPARAZZI_1,
        ).shuffled(rng)

        // 6-9: policías + granaderos (ORDEN FIJO). Ya con arte dedicado (2026-07-17).
        steps += listOf(
            SfFighterId.POLICIA_CDMX_HOMBRE,
            SfFighterId.POLICIA_CDMX,
            SfFighterId.POLICIA_GRANADERO_HOMBRE,
            SfFighterId.POLICIA_GRANADERO_MUJER,
        )

        // 10-11: jefes
        steps += SfFighterId.REY_GRUPERO   // semifinal
        steps += SfFighterId.PRANKEDY      // final

        return steps.mapIndexed { i, rival ->
            val n = i + 1
            Step(
                index = n,
                rival = rival,
                mapFile = when (n) {
                    1 -> MAP_FIRST
                    TOTAL_FIGHTS -> MAP_FINAL
                    else -> null            // TBD: asociación mapa↔rival intermedia (dueño)
                },
                isBoss = n >= TOTAL_FIGHTS - 1, // 10 (semifinal) y 11 (final)
            )
        }
    }
}
