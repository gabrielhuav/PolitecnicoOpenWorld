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

    suspend fun initializeDefaultCollectiblesIfNeeded() {
        val count = collectibleDao.getCollectiblesCount()
        if (count == 0) {
            val defaultList = listOf(
                CollectibleEntity(
                    id = "c_1",
                    name = "Apuntes de Leyenda",
                    description = "Aprobaste Cálculo Vectorial mágicamente.",
                    assetPath = "PRINCIPAL/lazaroIdle/lazaro_i_1.webp" // <-- Asset básico de prueba
                ),
                CollectibleEntity(
                    id = "c_2",
                    name = "Gordita de Mixiote",
                    description = "Te recupera toda la vida y te da un sueño inmediato.",
                    assetPath = "PRINCIPAL/lazaroIdle/lazaro_i_1.webp" // <-- Asset básico de prueba
                )
            )
            collectibleDao.insertInitialCollectibles(defaultList)
        }
    }
}