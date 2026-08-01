package ovh.gabrielhuav.pow.data.repository

import kotlinx.coroutines.flow.Flow
import ovh.gabrielhuav.pow.data.local.room.dao.CollectibleDao
import ovh.gabrielhuav.pow.data.local.room.entity.CollectibleEntity

class CollectibleRepository(
    private val collectibleDao: CollectibleDao
) {
    val allCollectiblesFlow: Flow<List<CollectibleEntity>> = collectibleDao.getAllCollectiblesFlow()

    suspend fun getUncollectedCollectibles(): List<CollectibleEntity> {
        return collectibleDao.getUncollectedCollectibles()
    }

    suspend fun claimCollectible(id: String) {
        collectibleDao.markAsCollected(id)
    }

    /**
     * Siembra los coleccionables base y **repara el arte de las partidas viejas**.
     *
     * ⚠️ El sembrado original solo corría con la tabla VACÍA, así que a quien ya tuviera partida
     * NUNCA se le actualizaba el `assetPath`. Cuando la carpeta de arte se renombró (`coleccionables/`
     * → `SPRITES/COLLECTIBLES/`, PR #126), esos jugadores se quedaron con rutas que ya no existen:
     * el coleccionable aparece **desbloqueado, con su nombre y su borde dorado, pero con el círculo
     * gris de "sin arte"**. No hay error ni log — solo se ve mal, y solo en instalaciones antiguas,
     * que es justo por lo que sobrevivió a varias versiones.
     *
     * Por eso [repararRutasDeArte] se ejecuta SIEMPRE, no solo en el primer arranque.
     */
    suspend fun initializeDefaultCollectiblesIfNeeded() {
        val count = collectibleDao.getCollectiblesCount()
        val defaultList = listOf(
            CollectibleEntity(
                id = "c_1",
                name = "IPN",
                description = "Dato curioso: El engrane, el edificio y el matraz del escudo representan las tres áreas del conocimiento del Instituto. Además, se dice que la mascota es un burro blanco porque uno deambulaba libremente por los terrenos de Zacatenco en los años 30.",
                assetPath = "SPRITES/COLLECTIBLES/colec_1.webp"
            ),
            CollectibleEntity(
                id = "c_2",
                name = "Casa abierta al tiempo",
                description = "Dato curioso: Es el significado de 'Incalli Ixcahuicopa', el lema en náhuatl de la UAM. Sus estudiantes, las panteras negras, son conocidos por sobrevivir a su implacable e intenso sistema de estudio por trimestres.",
                assetPath = "SPRITES/COLLECTIBLES/colec_2.webp"
            ),
            CollectibleEntity(
                id = "c_3",
                name = "Gloriosa ESIME",
                description = "Dato curioso: Es la escuela más antigua del IPN (fundada en 1916). De sus pasillos surgió el legendario grito de guerra '¡Huélum!', inventado por un estudiante en 1937 para animar los eventos deportivos.",
                assetPath = "SPRITES/COLLECTIBLES/colec_3.webp"
            ),
            CollectibleEntity(
                id = "c_4",
                name = "ETS",
                description = "Dato curioso: El temido Examen a Título de Suficiencia. La leyenda cuenta que durante la semana de ETS, las papelerías cercanas y las cafeterías triplican sus ventas gracias a las desveladas masivas.",
                assetPath = "SPRITES/COLLECTIBLES/colec_4.webp"
            ),
            CollectibleEntity(
                id = "c_5",
                name = "Laptop escomia",
                description = "Dato curioso: Auténtica herramienta de combate en ESCOM. Suele tener sistema dual con Linux, teclas borradas por programar de madrugada y sus ventiladores suenan como turbina de avión al compilar en C++.",
                assetPath = "SPRITES/COLLECTIBLES/colec_5.webp"
            ),
            CollectibleEntity(
                id = "c_6",
                name = "Apuntes de Leyenda",
                description = "Dato curioso: Conocidos como la 'herencia sagrada'. Son fotocopias de fotocopias del año 2008 que, inexplicablemente, siguen teniendo la solución exacta al problema más difícil que el profesor pondrá en el examen.",
                assetPath = "SPRITES/COLLECTIBLES/colec_6.webp"
            ),
            CollectibleEntity(
                id = "c_shine",
                name = "Refresco de la Casa",
                description = "Dato curioso: El refresco de Shine CTO tiene fama de ser adictivo. " +
                        "Se dice que quien llega a la décima copa descubre el sabor secreto... " +
                        "aunque el precio lo paga el estómago.",
                assetPath = "SPRITES/COLLECTIBLES/colec_shine.webp",
                isCollected = false
            )
        )
        if (count == 0) {
            collectibleDao.insertInitialCollectibles(defaultList)
        } else {
            repararRutasDeArte(defaultList)
        }
        ensureFighterCollectibles()
    }

    /**
     * 🆕 (2026-07-21) COLECCIONABLES DE PELEADOR (recompensa del arcade en DIFÍCIL).
     *
     * Se identifican por el PREFIJO del id (`fighter_<SfFighterId>`), no por una columna
     * nueva: así NO hace falta migrar la tabla de Room ni tocar las instalaciones ya
     * existentes. Es idempotente y se ejecuta también en partidas viejas (donde el conteo
     * ya no es 0), de modo que quien lleve tiempo jugando también los recibe.
     *
     * La HISTORIA de cada personaje es un placeholder: la pantalla muestra "Próximamente".
     */
    suspend fun ensureFighterCollectibles() {
        val existing = collectibleDao.getAllCollectibles().map { it.id }.toSet()
        val missing = FIGHTER_COLLECTIBLES.filter { it.id !in existing }
        if (missing.isNotEmpty()) collectibleDao.insertInitialCollectibles(missing)
    }

    /**
     * Pone al día el `assetPath` de las filas que ya existen, **conservando el progreso**.
     *
     * ⚠️ NO se usa `@Insert(REPLACE)` con la lista por defecto, que sería lo cómodo: eso
     * sobrescribiría `isCollected` y **le borraría al jugador los coleccionables que ya encontró**.
     * Se actualiza solo la columna del arte, y solo en las filas cuya ruta haya cambiado.
     */
    private suspend fun repararRutasDeArte(porDefecto: List<CollectibleEntity>) {
        val pendientes = rutasDesactualizadas(collectibleDao.getAllCollectibles(), porDefecto)
        for ((id, rutaBuena) in pendientes) collectibleDao.updateAssetPath(id, rutaBuena)
    }

    /** Marca como obtenido el coleccionable de un peleador (id del enum SfFighterId). */
    suspend fun unlockFighterCollectible(fighterName: String) {
        collectibleDao.markAsCollected(fighterCollectibleId(fighterName))
    }

    companion object {

        /**
         * Qué filas tienen el arte desactualizado. **Función PURA** — se separó de la escritura a
         * propósito, para poder fijarla con tests sin base de datos ni corrutinas.
         *
         * @return pares `(id, rutaCorrecta)` SOLO de las filas que existen y cuya ruta difiere.
         */
        fun rutasDesactualizadas(
            actuales: List<CollectibleEntity>,
            porDefecto: List<CollectibleEntity>,
        ): List<Pair<String, String>> {
            val porId = actuales.associateBy { it.id }
            return porDefecto.mapNotNull { esperado ->
                val actual = porId[esperado.id] ?: return@mapNotNull null
                if (actual.assetPath == esperado.assetPath) null
                else esperado.id to esperado.assetPath
            }
        }

        /** Prefijo que distingue a los coleccionables de PELEADOR del resto. */
        const val FIGHTER_PREFIX = "fighter_"

        fun fighterCollectibleId(fighterName: String): String =
            FIGHTER_PREFIX + fighterName.lowercase()

        /**
         * Un coleccionable por peleador con arte dedicado. `assetPath` apunta a su ATLAS:
         * la pantalla recorta su cuadro de idle, así que no hace falta arte extra.
         */
        private val FIGHTER_COLLECTIBLES: List<CollectibleEntity> = listOf(
            "PRANKEDY" to ("Prankedy" to "STREETFIGHTER/IMAGES/Prankedy.webp"),
            "SENOR_TIENDA" to ("El Señor de la Tienda" to "STREETFIGHTER/IMAGES/SenorTienda.webp"),
            "PAPARAZZI_1" to ("Paparazzi 1" to "STREETFIGHTER/IMAGES/Paparazzi1.webp"),
            "PAPARAZZI_5" to ("Paparazzi 5" to "STREETFIGHTER/IMAGES/Paparazzi5.webp"),
            "REY_GRUPERO" to ("Rey Grupero" to "STREETFIGHTER/IMAGES/ReyGrupero.webp"),
            "POLICIA_CDMX" to ("Policía CDMX" to "STREETFIGHTER/IMAGES/PoliciaCDMX.webp"),
            "POLICIA_CDMX_HOMBRE" to ("Policía CDMX (Hombre)" to "STREETFIGHTER/IMAGES/PoliciaCDMXHombre.webp"),
            "POLICIA_GRANADERO_HOMBRE" to ("Granadero" to "STREETFIGHTER/IMAGES/PoliciaGranaderoHombre.webp"),
            "POLICIA_GRANADERO_MUJER" to ("Granadera" to "STREETFIGHTER/IMAGES/PoliciaGranaderoMujer.webp"),
            "PARAMEDICO_CRUZ_ROJA" to ("Paramédico Cruz Roja" to "STREETFIGHTER/IMAGES/ParamedicoCruzRoja.webp"),
            "ESCOMBOY" to ("Escomboy" to "STREETFIGHTER/IMAGES/EscomBoy.webp"),
            "ESCOMGIRL" to ("Escomgirl" to "STREETFIGHTER/IMAGES/EscomGirl.webp"),
            "ROBOT" to ("Escomrobot" to "STREETFIGHTER/IMAGES/Robot.webp"),
            "CHARRO_NEGRO" to ("Charro Negro" to "STREETFIGHTER/IMAGES/CharroNegro.webp"),
            "LA_LLORONA" to ("La Llorona" to "STREETFIGHTER/IMAGES/LaLlorona.webp"),
            "LA_TZITZIMIME" to ("La Tzitzimime" to "STREETFIGHTER/IMAGES/LaTzitzimime.webp"),
            "YOALLI_EHECATL" to ("Yoalli Ehécatl" to "STREETFIGHTER/IMAGES/YoalliEhecatl.webp"),
            "LA_PRESIDENTA" to ("La Presidenta" to "STREETFIGHTER/IMAGES/LaPresidenta.webp"),
        ).map { (enumName, info) ->
            CollectibleEntity(
                id = fighterCollectibleId(enumName),
                name = info.first,
                description = "Vence a este peleador en ARCADE (Difícil) para conocer su historia.",
                assetPath = info.second,
                isCollected = false,
            )
        }
    }
}
