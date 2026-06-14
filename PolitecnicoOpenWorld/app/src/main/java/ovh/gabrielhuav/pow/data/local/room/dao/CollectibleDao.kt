package ovh.gabrielhuav.pow.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ovh.gabrielhuav.pow.data.local.room.entity.CollectibleEntity

@Dao
interface CollectibleDao {
    // Para la pantalla del menú (observa los cambios en tiempo real)
    @Query("SELECT * FROM collectibles")
    fun getAllCollectiblesFlow(): Flow<List<CollectibleEntity>>

    // Para que el ViewModel del mapa sepa cuáles puede aparecer
    @Query("SELECT * FROM collectibles WHERE isCollected = 0")
    suspend fun getUncollectedCollectibles(): List<CollectibleEntity>

    // Marca un objeto como recogido
    @Query("UPDATE collectibles SET isCollected = 1 WHERE id = :collectibleId")
    suspend fun markAsCollected(collectibleId: String)

    // Inserta la lista inicial de collectibles
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInitialCollectibles(collectibles: List<CollectibleEntity>)

    // Para verificar si ya se pobló la base de datos
    @Query("SELECT COUNT(id) FROM collectibles")
    suspend fun getCollectiblesCount(): Int
}