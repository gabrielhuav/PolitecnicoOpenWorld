package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.random.Random

/**
 * ESCALERA del MODO ARCADE (HUELUM VS. GOYA, versión POW). DATA-DRIVEN: dado el peleador que
 * ELIGIÓ el jugador (uno de los 3 estudiantes ESCOM, que YA NO son enemigos), devuelve la
 * secuencia ORDENADA de rivales + el mapa por escalón. Aislado y puro (sin Android).
 * Ver README for IAS/DISENO_ARCADE_SF_POW.md.
 *
 * Estructura (2026-07-17, dada por el dueño; ≤16 peleas):
 *   1     : PARAMEDICO_CRUZ_ROJA (siempre)
 *   2-4   : PAPARAZZI_1, PAPARAZZI_5, SENOR_TIENDA (orden ALEATORIO)
 *   5-6   : REY_GRUPERO, PRANKEDY (orden ALEATORIO) — 2 peleas para quedar en <16
 *   7-10  : POLICIA_CDMX_HOMBRE, POLICIA_CDMX (mujer), POLICIA_GRANADERO_HOMBRE,
 *           POLICIA_GRANADERO_MUJER (orden FIJO)
 *   11-12 : CHARRO_NEGRO, LA_LLORONA (orden ALEATORIO)
 *   13    : LA_TZITZIMIME (jefe)
 *   14    : YOALLI_EHECATL (jefe)
 *   15    : LA_PRESIDENTA (FINAL; su metamorfosis a Yoalli es un power/anim, no una 2ª fase)
 *
 * Mapas: cada RIVAL tiene su escenario de día (`SfStageCatalog.homeStage`); la iluminación
 * (día / noche / apocalipsis) la elige la dificultad del arcade (Fácil/Medio/Difícil).
 */
object SfArcadeLadder {

    // Fallbacks legacy (sesión guardada vieja / tests)
    const val MAP_FIRST = "fondo_queso_ipn_anim.webp"
    const val MAP_FINAL = "fondo_zocalo_anim.webp"

    /** Los 3 estudiantes desbloqueados de arranque; el jugador elige uno (NO son enemigos). */
    val STARTERS = listOf(SfFighterId.ESCOMBOY, SfFighterId.ESCOMGIRL, SfFighterId.ROBOT)

    /**
     * TODOS los personajes del arcade (estudiantes + enemigos). Lo usa el selector para pintar
     * los bloqueados con candado 🔒. (La Llorona entra cuando tenga assets.)
     */
    val ALL_PARTICIPANTS = listOf(
        SfFighterId.ESCOMBOY, SfFighterId.ESCOMGIRL, SfFighterId.ROBOT,
        SfFighterId.PARAMEDICO_CRUZ_ROJA, SfFighterId.PAPARAZZI_1, SfFighterId.PAPARAZZI_5,
        SfFighterId.SENOR_TIENDA, SfFighterId.REY_GRUPERO, SfFighterId.PRANKEDY,
        SfFighterId.POLICIA_CDMX_HOMBRE, SfFighterId.POLICIA_CDMX,
        SfFighterId.POLICIA_GRANADERO_HOMBRE, SfFighterId.POLICIA_GRANADERO_MUJER,
        SfFighterId.CHARRO_NEGRO, SfFighterId.LA_LLORONA, SfFighterId.LA_TZITZIMIME,
        SfFighterId.YOALLI_EHECATL, SfFighterId.LA_PRESIDENTA,
    )

    /** Un escalón: rival, mapa (null = conservar el anterior), si es jefe y si es el FINAL. */
    data class Step(
        val index: Int,               // 1..TOTAL (para HUD "PELEA N / T")
        val rival: SfFighterId,
        val mapFile: String?,         // null = TBD (el VM conserva el mapa vigente)
        val isBoss: Boolean = false,  // jefes (Tzitzímime/Yoalli/Presidenta) → dificultad tope
        val isFinal: Boolean = false, // La Presidenta → PESADILLA + banner especial
    )

    /** Total de peleas (15). */
    const val TOTAL_FIGHTS = 15

    /** Dificultad real de una pelea: los últimos escalones elevan la IA hasta PESADILLA. */
    fun difficultyForStep(base: SfCpuDifficulty, step: Step): SfCpuDifficulty {
        val increments = when {
            step.isFinal -> 2
            step.isBoss || step.index >= 10 -> 1
            else -> 0
        }
        val difficulties = SfCpuDifficulty.entries
        return difficulties[(base.ordinal + increments).coerceAtMost(difficulties.lastIndex)]
    }

    /** Intensidad adicional de la IA, de 20 % en la primera pelea a 100 % en la final. */
    fun intensityForStep(index: Int, total: Int): Float =
        if (total <= 1) 1f else 0.20f + 0.80f * (index - 1).toFloat() / (total - 1)

    /**
     * Arma la secuencia de escalones para el `player` elegido.
     * @param difficulty Fácil/Medio/Difícil → ilumina el mapa hogar del rival (día/noche/apocalipsis).
     * @param rng inyectable para tests.
     */
    fun build(
        @Suppress("UNUSED_PARAMETER") player: SfFighterId,
        difficulty: SfCpuDifficulty = SfCpuDifficulty.NORMAL,
        rng: Random = Random.Default,
    ): List<Step> {
        val rivals = mutableListOf<SfFighterId>()

        rivals += SfFighterId.PARAMEDICO_CRUZ_ROJA                                    // 1
        rivals += listOf(                                                             // 2-4
            SfFighterId.PAPARAZZI_1, SfFighterId.PAPARAZZI_5, SfFighterId.SENOR_TIENDA,
        ).shuffled(rng)
        rivals += listOf(SfFighterId.REY_GRUPERO, SfFighterId.PRANKEDY).shuffled(rng) // 5-6
        rivals += listOf(                                                             // 7-10
            SfFighterId.POLICIA_CDMX_HOMBRE, SfFighterId.POLICIA_CDMX,
            SfFighterId.POLICIA_GRANADERO_HOMBRE, SfFighterId.POLICIA_GRANADERO_MUJER,
        )
        rivals += listOf(SfFighterId.CHARRO_NEGRO, SfFighterId.LA_LLORONA).shuffled(rng) // 11-12
        rivals += SfFighterId.LA_TZITZIMIME  // 13
        rivals += SfFighterId.YOALLI_EHECATL // 14
        rivals += SfFighterId.LA_PRESIDENTA  // 15 (FINAL)

        val total = rivals.size
        return rivals.mapIndexed { i, rival ->
            val n = i + 1
            Step(
                index = n,
                rival = rival,
                // Mapa = hogar del RIVAL + iluminación por dificultad (Fácil/Medio/Difícil)
                mapFile = SfStageCatalog.mapForRival(rival, difficulty),
                isBoss = n >= total - 2, // los 3 últimos: Tzitzímime, Yoalli, Presidenta
                isFinal = n == total,    // La Presidenta
            )
        }
    }
}
