package ovh.gabrielhuav.pow.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "map_tiles",
    indices = [Index("urlKey"), Index("provider")]
)
data class MapTileEntity(
    @PrimaryKey val urlKey: String,
    val provider: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val data: ByteArray,
    val createdAtMs: Long
)