package ovh.gabrielhuav.pow.domain.models.streetfighter

/**
 * Catálogo de escenarios POW (fondos) + vínculo peleadór → mapa “de día” propio.
 *
 * Convención de archivos (assets `STREETFIGHTER/IMAGES/`):
 * - Día:            `fondo_<slug>_anim.png`
 * - Noche:          `fondo_<slug>_noche_1_anim.png`
 * - Noche apocalíptica: `fondo_<slug>_noche_2_anim.png`
 *
 * Arcade: Fácil → día, Medio → noche_1, Difícil → noche_2 (mismo “hogar” del rival).
 * Ver README for IAS / DISENO_ARCADE + SF_SPECIAL_VOICES (mapas).
 */
object SfStageCatalog {

    /** Variante de iluminación del escenario. */
    enum class Lighting {
        /** Día (Fácil / práctica libre). */
        DAY,
        /** Noche (Medio). */
        NIGHT,
        /** Noche 2 / apocalíptica (Difícil). */
        APOCALYPSE,
    }

    /**
     * Escenarios con trio día/noche/apocalipsis implementados (atlas `_anim`).
     * Clave = slug interno; [dayFile] es el archivo de día canónico.
     */
    data class Stage(
        val slug: String,
        val displayName: String,
        val dayFile: String,
    ) {
        fun file(lighting: Lighting): String = when (lighting) {
            Lighting.DAY -> dayFile
            Lighting.NIGHT -> dayFile.replace("_anim.png", "_noche_1_anim.png")
            Lighting.APOCALYPSE -> dayFile.replace("_anim.png", "_noche_2_anim.png")
        }
    }

    // ---- Catálogo de mapas implementados (16 bases × 3 iluminaciones = 48) ----
    val ESCOM = Stage("escom", "ESCOM", "fondo_escom_anim.png")
    val QUESO_IPN = Stage("queso_ipn", "Queso IPN", "fondo_queso_ipn_anim.png")
    val ESIME_AZC = Stage("esime_azc", "ESIME Azcapotzalco", "fondo_esime_azc_anim.png")
    val CECYT_9 = Stage("cecyt_9", "CECyT 9", "fondo_cecyt_9_anim.png")
    val CECYT_2 = Stage("cecyt_2", "CECyT 2", "fondo_cecyt_2_anim.png")
    val UNAM_CU = Stage("unam_biblioteca_cu", "Ciudad Universitaria UNAM", "fondo_unam_biblioteca_cu_anim.png")
    val FES_ACATLAN = Stage("fes_acatlan", "FES Acatlán", "fondo_fes_acatlan_anim.png")
    val UAM_AZCAPO = Stage("uam_azcapo", "UAM Azcapotzalco", "fondo_uam_azcapo_anim.png")
    val ISLA_MUNECAS = Stage("islamunecas", "Isla de las Muñecas", "fondo_islamunecas_anim.png")
    val MICTLÁN = Stage("mictlan", "Mictlán", "fondo_mictlan_anim.png")
    val AGAVE = Stage("campos_agave_jalisco", "Campos de Agave Jalisco", "fondo_campos_agave_jalisco_anim.png")
    val FAC_MED = Stage("facultad_medicina", "Facultad de Medicina", "fondo_facultad_medicina_anim.png")
    val FES_ARAGON = Stage("fes_aragon", "FES Aragón", "fondo_fes_aragon_anim.png")
    val PIRAMIDE = Stage("piramidesol", "Pirámide del Sol", "fondo_piramidesol_anim.png")
    val UAM_CUAJI = Stage("uam_cuajimalpa", "UAM Cuajimalpa", "fondo_uam_cuajimalpa_anim.png")
    val ZOCALO = Stage("zocalo", "Zócalo", "fondo_zocalo_anim.png")

    val ALL_STAGES: List<Stage> = listOf(
        ESCOM, QUESO_IPN, ESIME_AZC, CECYT_9, CECYT_2,
        UNAM_CU, FES_ACATLAN, UAM_AZCAPO, ISLA_MUNECAS, MICTLÁN,
        AGAVE, FAC_MED, FES_ARAGON, PIRAMIDE, UAM_CUAJI, ZOCALO,
    )

    /**
     * Mapa “hogar” de día por peleadór (arcade + desbloqueo → 3 luces del escenario).
     *
     * Asignación del dueño (2026-07-18). Solo peleadors del arcade
     * (`SfArcadeLadder.ALL_PARTICIPANTS`) son formales.
     *
     * Compartidos:
     * - CU UNAM: PAPARAZZI_1 + POLICIA_GRANADERO_MUJER
     * - Zócalo: POLICIA_GRANADERO_HOMBRE + LA_PRESIDENTA
     *
     * Los 16 mapas base tienen al menos un peleadór hogar.
     * Alpha/shared (LÁZARO, etc.): fallback solo para Modo Dev.
     */
    fun homeStage(id: SfFighterId): Stage = when (id) {
        // ── Starters IPN ──
        SfFighterId.ESCOMGIRL -> ESCOM
        SfFighterId.ESCOMBOY -> CECYT_9
        SfFighterId.ROBOT -> CECYT_2
        // ── Universo Prankedy / calle ──
        SfFighterId.PRANKEDY -> ESIME_AZC
        SfFighterId.REY_GRUPERO -> FES_ARAGON
        SfFighterId.SENOR_TIENDA -> QUESO_IPN
        SfFighterId.PAPARAZZI_1 -> UNAM_CU
        SfFighterId.PAPARAZZI_5 -> UAM_AZCAPO
        // ── Salud ──
        SfFighterId.PARAMEDICO_CRUZ_ROJA -> FAC_MED
        // ── Seguridad CDMX (POLICIA_CDMX = mujer en el enum) ──
        SfFighterId.POLICIA_CDMX_HOMBRE -> FES_ACATLAN
        SfFighterId.POLICIA_CDMX -> UAM_CUAJI
        SfFighterId.POLICIA_GRANADERO_HOMBRE -> ZOCALO
        SfFighterId.POLICIA_GRANADERO_MUJER -> UNAM_CU // comparte con Paparazzi 1
        // ── Leyenda / jefes ──
        SfFighterId.CHARRO_NEGRO -> AGAVE
        SfFighterId.LA_LLORONA -> ISLA_MUNECAS
        SfFighterId.LA_TZITZIMIME -> PIRAMIDE
        SfFighterId.YOALLI_EHECATL -> MICTLÁN
        SfFighterId.LA_PRESIDENTA -> ZOCALO // comparte con Granadero H
        // ── Alpha / no arcade (solo Dev) ──
        SfFighterId.LAZARO -> ESCOM
        SfFighterId.GRANADERO -> ZOCALO
        SfFighterId.PARAMEDICO -> FAC_MED
    }

    /** Iluminación del arcade según dificultad elegida (Fácil/Medio/Difícil). */
    fun lightingForArcadeDifficulty(diff: SfCpuDifficulty): Lighting = when (diff) {
        SfCpuDifficulty.BASICA -> Lighting.DAY
        SfCpuDifficulty.NORMAL -> Lighting.NIGHT
        SfCpuDifficulty.AVANZADA, SfCpuDifficulty.PESADILLA -> Lighting.APOCALYPSE
    }

    /** Archivo de mapa para pelear contra [rival] en la dificultad de arcade [diff]. */
    fun mapForRival(rival: SfFighterId, diff: SfCpuDifficulty): String =
        homeStage(rival).file(lightingForArcadeDifficulty(diff))

    /** Día + noche + apocalipsis de un escenario (para desbloquear el “paquete” del peleadór). */
    fun filesForStage(stage: Stage): Set<String> = setOf(
        stage.file(Lighting.DAY),
        stage.file(Lighting.NIGHT),
        stage.file(Lighting.APOCALYPSE),
    )

    /** Todas las variantes de mapa ligadas a un peleadór (su hogar × 3 luces). */
    fun unlockableMapsForFighter(id: SfFighterId): Set<String> =
        filesForStage(homeStage(id))

    /**
     * Resuelve a qué [Stage] pertenece un archivo (`…_anim.png` / `…_noche_1_anim.png` / …).
     * Útil al desbloquear un archivo suelto y abrir toda la familia.
     */
    fun stageForFile(file: String): Stage? {
        val base = file
            .removeSuffix(".png")
            .replace("_noche_2_anim", "_anim")
            .replace("_noche_1_anim", "_anim")
            .let { if (it.endsWith("_anim")) "$it.png" else file }
        return ALL_STAGES.find { it.dayFile == base || it.dayFile == file }
    }

    /** Lista plana de los 48 fondos (día+noche+apocalipsis) para el selector de práctica. */
    fun allBackgroundFiles(): List<String> = ALL_STAGES.flatMap { s ->
        listOf(s.file(Lighting.DAY), s.file(Lighting.NIGHT), s.file(Lighting.APOCALYPSE))
    }
}
