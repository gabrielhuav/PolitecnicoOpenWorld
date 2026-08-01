// data/repository/CollisionMatrixRepository.kt
package ovh.gabrielhuav.pow.data.repository

import kotlinx.serialization.encodeToString
import ovh.gabrielhuav.pow.data.json.PowJson

import kotlinx.serialization.Serializable

import android.content.Context
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

    @Serializable
    private data class Store(
        val version: Int = 1,
        val rooms: MutableMap<String, List<String>> = mutableMapOf()
    )

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** Matrices de fábrica empaquetadas en assets/collision_matrices.json (si existe). */
    private fun readAssetStore(context: Context): Store = try {
        context.assets.open(FILE_NAME).use { input ->
            PowJson.decodeFromString<Store>(input.reader().readText())
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
            val local = PowJson.decodeFromString<Store>(f.readText())
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
        runCatching { file(context).writeText(PowJson.encodeToString(store)) }
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
    fun exportJson(context: Context): String = PowJson.encodeToString(readStore(context))

    /** Importa un JSON completo (sobrescribe el archivo local). */
    fun importJson(context: Context, json: String) {
        runCatching {
            val s = PowJson.decodeFromString<Store>(json)
            writeStore(context, s)
        }
    }

    fun clear(context: Context, roomId: String) {
        val s = readStore(context)
        s.rooms.remove(roomId)
        writeStore(context, s)
    }
}