// data/repository/CollisionMatrixRepository.kt
package ovh.gabrielhuav.pow.data.repository

import android.content.Context
import com.google.gson.Gson
import java.io.File

/**
 * Persistencia de las matrices de colisión del minijuego de zombis en un único
 * JSON (misma idea que los assets/landmarks del mapa principal: lo editas en el
 * Modo Diseñador y queda guardado entre sesiones).
 *
 * Formato (idéntico al que lee el servidor, server.js):
 * {
 *   "version": 1,
 *   "rooms": {
 *     "lobby_campus":  ["####################", "#..................#", ...],
 *     "za_auditorio":  ["####################", ...]
 *   }
 * }
 *
 * Archivo: <filesDir>/collision_matrices.json
 */
object CollisionMatrixRepository {

    private const val FILE_NAME = "collision_matrices.json"
    private val gson = Gson()

    private data class Store(
        val version: Int = 1,
        val rooms: MutableMap<String, List<String>> = mutableMapOf()
    )

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** Matrices de fábrica empaquetadas en assets/collision_matrices.json (si existe). */
    private fun readAssetStore(context: Context): Store = try {
        context.assets.open(FILE_NAME).use { input ->
            gson.fromJson(input.reader().readText(), Store::class.java) ?: Store()
        }
    } catch (e: Exception) {
        Store()
    }

    private fun readStore(context: Context): Store = try {
        val asset = readAssetStore(context)
        val f = file(context)
        if (!f.exists()) {
            asset
        } else {
            val local = gson.fromJson(f.readText(), Store::class.java) ?: Store()
            // MERGE: base = matrices de FÁBRICA (asset); el LOCAL (ediciones del Diseñador) SOBREESCRIBE
            // por sala. Así las salas NUEVAS del asset (p. ej. encb_lab1) SIEMPRE se cargan aunque exista
            // un collision_matrices.json local viejo que no las tenga. (Antes el local tapaba al asset.)
            val merged = HashMap<String, List<String>>(asset.rooms)
            merged.putAll(local.rooms)
            Store(version = local.version, rooms = merged)
        }
    } catch (e: Exception) {
        readAssetStore(context)
    }

    private fun writeStore(context: Context, store: Store) {
        runCatching { file(context).writeText(gson.toJson(store)) }
    }

    /** Todas las matrices guardadas (roomId -> filas). Vacío si no hay archivo. */
    fun loadAll(context: Context): Map<String, List<String>> = readStore(context).rooms

    /** Matriz guardada de una sala, o null si nunca se editó. */
    fun load(context: Context, roomId: String): List<String>? = readStore(context).rooms[roomId]

    /** Guarda/actualiza la matriz de una sala. */
    fun save(context: Context, roomId: String, rows: List<String>) {
        val s = readStore(context)
        s.rooms[roomId] = rows
        writeStore(context, s)
    }

    /** JSON completo (para copiarlo al servidor o compartirlo). */
    fun exportJson(context: Context): String = gson.toJson(readStore(context))

    /** Importa un JSON completo (sobrescribe el archivo local). */
    fun importJson(context: Context, json: String) {
        runCatching {
            val s = gson.fromJson(json, Store::class.java) ?: return
            writeStore(context, s)
        }
    }

    fun clear(context: Context, roomId: String) {
        val s = readStore(context)
        s.rooms.remove(roomId)
        writeStore(context, s)
    }
}