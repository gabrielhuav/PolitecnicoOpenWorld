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
 *   14    : LA_PRESIDENTA (jefe)
 *   15    : YOALLI_EHECATL (FINAL; a ≤1/4 de vida se metamorfosea en La Presidenta — 2ª vida)
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

    /**
     * Dificultad real de la IA de una pelea del arcade.
     *
     * 🆕 (2026-07-25, rebalance pedido por el dueño) La IA juega **un escalón POR ENCIMA de la
     * etiqueta elegida** (así "Fácil" ya no es trivial — usaba BASICA, que reacciona en ~1 s) y
     * **sube con el avance** (antes casi no cambiaba: solo los jefes subían). Curva:
     *  - peleas 1-4  : +1  (Fácil→NORMAL, Medio→AVANZADA, Difícil→PESADILLA)
     *  - peleas 5-9  : +1
     *  - peleas 10-12: +2
     *  - jefes 13-14 : +2
     *  - FINAL 15    : +3 (tope PESADILLA)
     * La ILUMINACIÓN del mapa sigue usando la dificultad ELEGIDA (SfStageCatalog.
     * lightingForArcadeDifficulty), así que este bump NO cambia día/noche/apocalipsis.
     */
    fun difficultyForStep(base: SfCpuDifficulty, step: Step): SfCpuDifficulty {
        val increments = when {
            step.isFinal -> 3
            step.isBoss -> 2
            step.index >= 10 -> 2
            step.index >= 5 -> 1
            else -> 1
        }
        val difficulties = SfCpuDifficulty.entries
        return difficulties[(base.ordinal + increments).coerceAtMost(difficulties.lastIndex)]
    }

    /**
     * Intensidad adicional de la IA (acelera reacción, presión y combos), de la primera pelea a
     * la final. 🆕 (2026-07-25) Arranca en 35 % (antes 20 %) para que las primeras peleas ya no
     * se sientan lentas; llega a 100 % en la final.
     */
    fun intensityForStep(index: Int, total: Int): Float =
        if (total <= 1) 1f else 0.35f + 0.65f * (index - 1).toFloat() / (total - 1)

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
        // 🆕 (2026-07-25, decisión del dueño) Orden invertido de los 2 jefes finales: primero
        // LA PRESIDENTA (14) y el jefe FINAL es YOALLI EHÉCATL (15), que a ≤1/4 de vida se
        // METAMORFOSEA en La Presidenta (segunda vida) — la dirección inversa a la de antes.
        rivals += SfFighterId.LA_PRESIDENTA  // 14
        rivals += SfFighterId.YOALLI_EHECATL // 15 (FINAL; se transforma en La Presidenta)

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
