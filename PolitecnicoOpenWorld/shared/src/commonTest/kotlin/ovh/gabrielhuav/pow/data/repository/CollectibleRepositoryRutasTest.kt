package ovh.gabrielhuav.pow.data.repository

import ovh.gabrielhuav.pow.data.local.room.entity.CollectibleEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 🗃️ REPARACIÓN DEL ARTE DE LOS COLECCIONABLES EN PARTIDAS VIEJAS.
 *
 * ## Qué protege esto
 *
 * El sembrado de coleccionables solo corría con la tabla VACÍA, así que a quien ya tuviera partida
 * nunca se le actualizaba el `assetPath`. Cuando la carpeta de arte se renombró (`coleccionables/`
 * → `SPRITES/COLLECTIBLES/`), esos jugadores se quedaron con rutas muertas: el coleccionable salía
 * **desbloqueado, con su nombre y su borde dorado, pero con el círculo gris de "sin arte"**. Sin
 * error, sin log. Reproducido en el emulador con una BD real de una versión anterior.
 *
 * ⚠️ La tentación al arreglarlo es hacer `insertInitialCollectibles(porDefecto)` con REPLACE, que
 * es una línea. **Eso le borra al jugador todo lo que había encontrado.** Estos tests existen para
 * que esa "simplificación" se ponga roja.
 */
class CollectibleRepositoryRutasTest {

    private fun fila(id: String, ruta: String, obtenido: Boolean = false) = CollectibleEntity(
        id = id,
        name = id.uppercase(),
        description = "",
        assetPath = ruta,
        isCollected = obtenido,
    )

    private val porDefecto = listOf(
        fila("c_1", "SPRITES/COLLECTIBLES/colec_1.webp"),
        fila("c_2", "SPRITES/COLLECTIBLES/colec_2.webp"),
    )

    @Test
    fun `una ruta vieja se detecta como desactualizada`() {
        val actuales = listOf(
            fila("c_1", "coleccionables/colec_1.webp", obtenido = true),
            fila("c_2", "SPRITES/COLLECTIBLES/colec_2.webp", obtenido = true),
        )
        assertEquals(
            listOf("c_1" to "SPRITES/COLLECTIBLES/colec_1.webp"),
            CollectibleRepository.rutasDesactualizadas(actuales, porDefecto),
        )
    }

    @Test
    fun `si todo esta al dia no se toca NADA`() {
        // Importa: en cada arranque se llama a esto. Si devolviera trabajo sin necesidad, serían
        // escrituras a la BD en el arranque de todas las partidas, para nada.
        val actuales = porDefecto.map { it.copy(isCollected = true) }
        assertTrue(CollectibleRepository.rutasDesactualizadas(actuales, porDefecto).isEmpty())
    }

    @Test
    fun `un coleccionable que el jugador no tiene NO se inventa`() {
        // Solo se reparan filas EXISTENTES. Dar de alta lo que falta es otro camino
        // (`ensureFighterCollectibles`), y mezclarlos aquí insertaría filas por sorpresa.
        val actuales = listOf(fila("c_1", "coleccionables/colec_1.webp"))
        val pendientes = CollectibleRepository.rutasDesactualizadas(actuales, porDefecto)
        assertEquals(1, pendientes.size)
        assertEquals("c_1", pendientes.single().first)
    }

    @Test
    fun `las filas que el jugador tiene de mas se respetan`() {
        // Coleccionables de una versión futura, o de peleador: no están en la lista por defecto y
        // no deben tocarse ni borrarse.
        val actuales = listOf(
            fila("c_1", "SPRITES/COLLECTIBLES/colec_1.webp"),
            fila("c_2", "SPRITES/COLLECTIBLES/colec_2.webp"),
            fila("fighter_prankedy", "STREETFIGHTER/IMAGES/Prankedy.webp", obtenido = true),
        )
        assertTrue(CollectibleRepository.rutasDesactualizadas(actuales, porDefecto).isEmpty())
    }

    @Test
    fun `la reparacion NO decide nada sobre isCollected`() {
        // El contrato es "solo la ruta". Se comprueba que el progreso no entra en la decisión:
        // la misma fila desactualizada da el mismo resultado esté obtenida o no.
        val sinObtener = listOf(fila("c_1", "vieja.webp", obtenido = false))
        val obtenida = listOf(fila("c_1", "vieja.webp", obtenido = true))
        assertEquals(
            CollectibleRepository.rutasDesactualizadas(sinObtener, porDefecto),
            CollectibleRepository.rutasDesactualizadas(obtenida, porDefecto),
        )
    }
}
