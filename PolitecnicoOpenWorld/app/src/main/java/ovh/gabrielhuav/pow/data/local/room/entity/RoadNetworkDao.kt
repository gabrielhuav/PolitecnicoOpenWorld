package ovh.gabrielhuav.pow.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ovh.gabrielhuav.pow.data.local.room.entity.RoadNodeEntity
import ovh.gabrielhuav.pow.data.local.room.entity.RoadWayEntity
import ovh.gabrielhuav.pow.data.local.room.entity.RoadZoneEntity

@Dao
interface RoadNetworkDao {

    // ─── LECTURA ─────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM road_zones WHERE cellKey = :cellKey")
    suspend fun getZone(cellKey: String): RoadZoneEntity?

    @Query("SELECT * FROM road_ways WHERE cellKey = :cellKey")
    suspend fun getWaysForZone(cellKey: String): List<RoadWayEntity>

    @Query("""
        SELECT rn.* FROM road_nodes rn
        INNER JOIN road_ways rw ON rn.wayId = rw.wayId
        WHERE rw.cellKey = :cellKey
        ORDER BY rn.wayId, rn.position
    """)
    suspend fun getNodesForZone(cellKey: String): List<RoadNodeEntity>

    // ─── ESCRITURA EN TRANSACCIÓN ─────────────────────────────────────────────────
    // @Transaction garantiza que las 3 inserciones son atómicas:
    // si falla cualquiera, no se guarda nada (no quedan zonas a medias).

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZone(zone: RoadZoneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWays(ways: List<RoadWayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<RoadNodeEntity>)

    /**
     * Inserta zona + ways + nodos en una sola transacción atómica.
     * Sin @Transaction, Room abre y cierra una transacción por cada @Insert,
     * lo que con miles de nodos puede tardar segundos y dejar la BD incompleta
     * si el proceso muere entre medias.
     */
    @Transaction
    suspend fun insertZoneWithData(
        zone: RoadZoneEntity,
        ways: List<RoadWayEntity>,
        nodes: List<RoadNodeEntity>
    ) {
        insertZone(zone)
        insertWays(ways)
        insertNodes(nodes)
    }

    // ─── LIMPIEZA ─────────────────────────────────────────────────────────────────

    @Query("DELETE FROM road_zones WHERE downloadedAtMs < :beforeMs")
    suspend fun deleteExpiredZones(beforeMs: Long)

    @Query("""
        DELETE FROM road_zones WHERE cellKey = (
            SELECT cellKey FROM road_zones ORDER BY downloadedAtMs ASC LIMIT 1
        )
    """)
    suspend fun deleteOldestZone()

    @Query("SELECT COUNT(*) FROM road_zones")
    suspend fun getZoneCount(): Int

    @Query("SELECT SUM(wayCount) FROM road_zones")
    suspend fun getTotalWayCount(): Int?
}