package ovh.gabrielhuav.pow.data.repository

import kotlinx.coroutines.flow.Flow
import ovh.gabrielhuav.pow.data.local.room.dao.CollectibleDao
import ovh.gabrielhuav.pow.data.local.room.entity.CollectibleEntity
import ovh.gabrielhuav.pow.domain.models.EntrenadorLocation

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

    suspend fun initializeDefaultCollectiblesIfNeeded() {
        val count = collectibleDao.getCollectiblesCount()
        if (count == 0) {
            val defaultList = listOf(
                CollectibleEntity(
                    id = "c_1",
                    name = "IPN",
                    description = "Dato curioso: El engrane, el edificio y el matraz del escudo representan las tres áreas del conocimiento del Instituto. Además, se dice que la mascota es un burro blanco porque uno deambulaba libremente por los terrenos de Zacatenco en los años 30.",
                    assetPath = "collectibles/colec_1.webp"
                ),
                CollectibleEntity(
                    id = "c_2",
                    name = "Casa abierta al tiempo",
                    description = "Dato curioso: Es el significado de 'Incalli Ixcahuicopa', el lema en náhuatl de la UAM. Sus estudiantes, las panteras negras, son conocidos por sobrevivir a su implacable e intenso sistema de estudio por trimestres.",
                    assetPath = "collectibles/colec_2.webp"
                ),
                CollectibleEntity(
                    id = "c_3",
                    name = "Gloriosa ESIME",
                    description = "Dato curioso: Es la escuela más antigua del IPN (fundada en 1916). De sus pasillos surgió el legendario grito de guerra '¡Huélum!', inventado por un estudiante en 1937 para animar los eventos deportivos.",
                    assetPath = "collectibles/colec_3.webp"
                ),
                CollectibleEntity(
                    id = "c_4",
                    name = "ETS",
                    description = "Dato curioso: El temido Examen a Título de Suficiencia. La leyenda cuenta que durante la semana de ETS, las papelerías cercanas y las cafeterías triplican sus ventas gracias a las desveladas masivas.",
                    assetPath = "collectibles/colec_4.webp"
                ),
                CollectibleEntity(
                    id = "c_5",
                    name = "Laptop escomia",
                    description = "Dato curioso: Auténtica herramienta de combate en ESCOM. Suele tener sistema dual con Linux, teclas borradas por programar de madrugada y sus ventiladores suenan como turbina de avión al compilar en C++.",
                    assetPath = "collectibles/colec_5.webp"
                ),
                CollectibleEntity(
                    id = "c_6",
                    name = "Apuntes de Leyenda",
                    description = "Dato curioso: Conocidos como la 'herencia sagrada'. Son fotocopias de fotocopias del año 2008 que, inexplicablemente, siguen teniendo la solución exacta al problema más difícil que el profesor pondrá en el examen.",
                    assetPath = "collectibles/colec_6.webp"
                ),
                CollectibleEntity(
                    id = "c_shine",
                    name = "Refresco de la Casa",
                    description = "Dato curioso: El refresco de Shine CTO tiene fama de ser adictivo. " +
                            "Se dice que quien llega a la décima copa descubre el sabor secreto... " +
                            "aunque el precio lo paga el estómago.",
                    assetPath = "collectibles/colec_shine.webp",
                    isCollected = false
                )
            )
            collectibleDao.insertInitialCollectibles(defaultList)
        }
    }

    /**
     * Asegura que el coleccionable trofeo de la Carrera del Politécnico esté en la BD.
     * Usa OnConflictStrategy.IGNORE para ser idempotente (no falla si ya existe).
     */
    suspend fun ensureRaceCollectible() {
        collectibleDao.insertCollectible(
            CollectibleEntity(
                id          = EntrenadorLocation.REWARD_COLLECTIBLE_ID,
                name        = "Trofeo de la Carrera del Politécnico",
                description = "Premio al corredor más veloz del IPN. " +
                    "Completaste la Carrera del Politécnico cruzando el campus de ESCOM " +
                    "de extremo a extremo en menos de 60 segundos. " +
                    "¡Ni el metro llega tan rápido!",
                assetPath   = "collectibles/colec_race.webp",
                isCollected = false
            )
        )
    }
}