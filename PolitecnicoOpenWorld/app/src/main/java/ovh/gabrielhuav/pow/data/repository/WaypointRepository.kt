// data/repository/WaypointRepository.kt
package ovh.gabrielhuav.pow.data.repository

import android.content.Context
import com.google.gson.Gson
import ovh.gabrielhuav.pow.domain.models.zombie.DoorKind
import ovh.gabrielhuav.pow.domain.models.zombie.NormRect
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneDoor
import java.io.File

/**
 * Persistencia de los WAYPOINTS (puertas / transiciones) del minijuego de
 * zombis en un único JSON, con exactamente la misma idea que
 * [CollisionMatrixRepository]: se editan en el Modo Diseñador (arrastrando las
 * puertas sobre el fondo) y quedan guardados entre sesiones.
 *
 * Las coordenadas son FRACCIONARIAS [0,1] respecto al tamaño del fondo, igual
 * que las matrices de colisión, por lo que son independientes de la resolución
 * real con que cada dispositivo decodifica la imagen.
 *
 * Formato:
 * {
 *   "version": 1,
 *   "rooms": {
 *     "lobby_campus": [
 *       { "left":0.34, "top":0.13, "right":0.50, "bottom":0.23,
 *         "targetRoomId":"za_auditorio", "label":"Auditorio", "kind":"TO_BUILDING" },
 *       ...
 *     ]
 *   }
 * }
 *
 * Archivo de usuario: <filesDir>/waypoints.json
 * Archivo de fábrica: assets/waypoints.json
 */
object WaypointRepository {

    private const val FILE_NAME = "waypoints.json"
    private val gson = Gson()

    /** Una puerta serializable (coordenadas fraccionarias [0,1]). */
    data class DoorDef(
        val left: Float = 0f,
        val top: Float = 0f,
        val right: Float = 0f,
        val bottom: Float = 0f,
        val targetRoomId: String = "",
        val label: String = "",
        val kind: String = "GENERIC"
    ) {
        fun toZoneDoor(): ZoneDoor = ZoneDoor(
            hitboxFrac = NormRect(left, top, right, bottom),
            targetRoomId = targetRoomId,
            label = label,
            kind = runCatching { DoorKind.valueOf(kind) }.getOrDefault(DoorKind.GENERIC)
        )

        companion object {
            fun from(d: ZoneDoor): DoorDef = DoorDef(
                left = d.hitboxFrac.left,
                top = d.hitboxFrac.top,
                right = d.hitboxFrac.right,
                bottom = d.hitboxFrac.bottom,
                targetRoomId = d.targetRoomId,
                label = d.label,
                kind = d.kind.name
            )
        }
    }

    private data class Store(
        val version: Int = 1,
        val rooms: MutableMap<String, List<DoorDef>> = mutableMapOf()
    )

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** Waypoints de fábrica empaquetados en assets/waypoints.json (si existe). */
    private fun readAssetStore(context: Context): Store = try {
        context.assets.open(FILE_NAME).use { input ->
            gson.fromJson(input.reader().readText(), Store::class.java) ?: Store()
        }
    } catch (e: Exception) {
        Store()
    }

    private fun readStore(context: Context): Store = try {
        val f = file(context)
        if (!f.exists()) readAssetStore(context)
        else gson.fromJson(f.readText(), Store::class.java) ?: readAssetStore(context)
    } catch (e: Exception) {
        readAssetStore(context)
    }

    private fun writeStore(context: Context, store: Store) {
        runCatching { file(context).writeText(gson.toJson(store)) }
    }

    /** Todas las puertas guardadas (roomId -> lista de ZoneDoor). */
    fun loadAll(context: Context): Map<String, List<ZoneDoor>> =
        readStore(context).rooms.mapValues { (_, defs) -> defs.map { it.toZoneDoor() } }

    /** Puertas de una sala, o null si nunca se editaron. */
    fun load(context: Context, roomId: String): List<ZoneDoor>? =
        readStore(context).rooms[roomId]?.map { it.toZoneDoor() }

    /** Guarda/actualiza las puertas de una sala. */
    fun save(context: Context, roomId: String, doors: List<ZoneDoor>) {
        val s = readStore(context)
        s.rooms[roomId] = doors.map { DoorDef.from(it) }
        writeStore(context, s)
    }

    /** JSON completo (para copiarlo / compartirlo). */
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
