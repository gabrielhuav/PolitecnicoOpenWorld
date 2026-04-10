package ovh.gabrielhuav.pow.data.cache

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.File

/**
 * Caché de tiles PNG en SQLite (filesDir — nunca borrado por Android ni limpiadores).
 *
 * CAMBIO DE DISEÑO vs versión anterior:
 * Antes: usaba z/x/y como clave → requería parsear la URL de cada proveedor con regex,
 *   lo que fallaba con distintos formatos (ESRI invierte y/x, Google usa query params, etc.)
 *
 * Ahora: usa el hash de la URL completa como clave → funciona con CUALQUIER proveedor
 *   sin necesidad de regex. Más simple, más robusto, mismo resultado.
 *
 * TABLA:
 *   url_key  TEXT  — hash de la URL del tile (hashCode().toString())
 *   data     BLOB  — bytes PNG/JPG del tile
 *   created_at INT — timestamp para TTL
 */
class TileCache(context: Context) {

    private val TAG      = "TileCache"
    private val tilesDir = File(context.filesDir, "mbtiles").also { it.mkdirs() }

    private val helpers     = HashMap<String, TileDatabase>()
    private val helpersLock = Any()

    companion object {
        private const val TILE_TTL_MS            = 1000L * 60 * 60 * 24 * 30 // 30 días
        private const val MAX_TILES_PER_PROVIDER = 8_000
    }

    // ─── API PÚBLICA (síncrona — para hilo background del WebViewClient) ──────────

    /**
     * Busca un tile por URL. Retorna null si no existe o expiró.
     */
    fun getTileByUrl(provider: String, urlKey: String): ByteArray? {
        return try {
            getOrCreateHelper(provider).getTile(urlKey)
        } catch (e: Exception) {
            Log.e(TAG, "getTileByUrl error: ${e.message}")
            null
        }
    }

    /**
     * Guarda un tile con su URL como clave.
     */
    fun putTileByUrl(provider: String, urlKey: String, data: ByteArray) {
        try {
            getOrCreateHelper(provider).putTile(urlKey, data)
        } catch (e: Exception) {
            Log.e(TAG, "putTileByUrl error: ${e.message}")
        }
    }

    /**
     * Stats para diagnóstico.
     */
    fun getStats(provider: String): String {
        return try {
            val count = getOrCreateHelper(provider).getCount()
            "$provider: $count tiles en caché"
        } catch (e: Exception) { "error" }
    }

    fun closeAll() {
        synchronized(helpersLock) {
            helpers.values.forEach { it.close() }
            helpers.clear()
        }
    }

    private fun getOrCreateHelper(provider: String): TileDatabase {
        synchronized(helpersLock) {
            return helpers.getOrPut(provider) {
                TileDatabase(File(tilesDir, "$provider.mbtiles"))
            }
        }
    }

    // ─── SQLITEOPENHELPER ─────────────────────────────────────────────────────────

    private inner class TileDatabase(dbFile: File) :
        SQLiteOpenHelper(context.applicationContext, dbFile.absolutePath, null, 2) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS tiles (
                    url_key    TEXT    NOT NULL PRIMARY KEY,
                    data       BLOB    NOT NULL,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_url ON tiles (url_key)")
            db.rawQuery("PRAGMA synchronous=NORMAL", null).close()
        }

        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
            db.execSQL("DROP TABLE IF EXISTS tiles")
            onCreate(db)
        }

        @Synchronized
        fun getTile(urlKey: String): ByteArray? {
            readableDatabase.rawQuery(
                "SELECT data, created_at FROM tiles WHERE url_key=?",
                arrayOf(urlKey)
            ).use { c ->
                if (!c.moveToFirst()) return null
                val age = System.currentTimeMillis() - c.getLong(1)
                if (age > TILE_TTL_MS) {
                    // Expirado — borrar y re-descargar
                    writableDatabase.delete("tiles", "url_key=?", arrayOf(urlKey))
                    return null
                }
                return c.getBlob(0)
            }
        }

        @Synchronized
        fun putTile(urlKey: String, data: ByteArray) {
            val db = writableDatabase
            // LRU: evictar el 10% más antiguo si se supera el límite
            val count = db.rawQuery("SELECT COUNT(*) FROM tiles", null)
                .use { c -> c.moveToFirst(); c.getLong(0) }
            if (count >= MAX_TILES_PER_PROVIDER) {
                val evict = MAX_TILES_PER_PROVIDER / 10
                db.execSQL(
                    "DELETE FROM tiles WHERE rowid IN " +
                            "(SELECT rowid FROM tiles ORDER BY created_at ASC LIMIT $evict)"
                )
                Log.d(TAG, "LRU: $evict tiles eliminados")
            }
            db.insertWithOnConflict("tiles", null, ContentValues().apply {
                put("url_key",    urlKey)
                put("data",       data)
                put("created_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }

        fun getCount(): Long {
            return readableDatabase.rawQuery("SELECT COUNT(*) FROM tiles", null)
                .use { c -> c.moveToFirst(); c.getLong(0) }
        }
    }
}