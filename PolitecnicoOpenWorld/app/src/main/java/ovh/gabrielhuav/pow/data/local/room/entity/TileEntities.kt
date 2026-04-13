package ovh.gabrielhuav.pow.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "map_tiles",
    primaryKeys = ["provider", "urlKey"]
)
data class MapTileEntity(
    val provider: String,
    val urlKey: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val data: ByteArray,
    val createdAtMs: Long
)