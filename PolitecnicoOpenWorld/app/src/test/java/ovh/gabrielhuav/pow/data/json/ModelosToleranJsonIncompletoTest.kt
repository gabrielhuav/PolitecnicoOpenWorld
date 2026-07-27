package ovh.gabrielhuav.pow.data.json

import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotNull
import org.junit.Test
import ovh.gabrielhuav.pow.data.repository.GameSaveData
import ovh.gabrielhuav.pow.data.repository.SavedNpc
import ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph
import ovh.gabrielhuav.pow.domain.models.ai.LocalNode
import ovh.gabrielhuav.pow.domain.models.ai.LocalWay
import ovh.gabrielhuav.pow.domain.models.map.LandmarkAssetTemplate
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.RemoteZombiePlayer
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MultiplayerNpc
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MultiplayerPlayer
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetFireball
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetMsg
import ovh.gabrielhuav.pow.features.streetfighter.data.SfRoomSummary

/**
 * 🍏 REGRESIÓN DE LA FASE 3 — "todo modelo debe sobrevivir a un JSON incompleto".
 *
 * ESTE TEST NACE DE UN BUG REAL que se coló al migrar de Gson y que cazó la auditoría:
 * **kotlinx.serialization LANZA EXCEPCIÓN si el JSON no trae un campo que no tiene valor por
 * defecto.** Gson, en cambio, dejaba el campo en `null`/`0` y seguía. Al anotar los modelos con
 * `@Serializable` sin revisar los defaults, quedaron 8 clases con campos obligatorios — entre
 * ellas `MultiplayerNpc`, `MultiplayerPlayer` y `RemoteZombiePlayer`, **que llegan de la red**.
 *
 * Consecuencia si no se arregla: un cliente viejo (o el servidor Node) que mande un mensaje sin
 * un campo **crashea la app del rival** en mitad de la partida, donde antes solo se degradaba.
 * Ningún compilador avisa de esto y no aparece hasta que hay dos versiones distintas jugando.
 *
 * ⚠️ Si añades un modelo que viaje por RED o venga de un ASSET, **añádelo aquí**. La regla es
 * simple: tiene que poder decodificarse desde `{}`.
 */
class ModelosToleranJsonIncompletoTest {

    /** Estricto a propósito: sin `coerceInputValues`, para probar los defaults DE VERDAD. */
    private val estricto = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> soportaVacio() {
        assertNotNull(
            "${T::class.simpleName} no puede decodificarse desde '{}': algún campo no tiene " +
                "valor por defecto. Con Gson eso quedaba en null/0; con kotlinx CRASHEA.",
            estricto.decodeFromString<T>("{}"),
        )
    }

    @Test
    fun `modelos de RED de Huelum vs Goya`() {
        soportaVacio<SfNetMsg>()
        soportaVacio<SfNetFireball>()
        soportaVacio<SfRoomSummary>()
    }

    @Test
    fun `modelos de RED del mundo abierto y de los zombis`() {
        soportaVacio<MultiplayerPlayer>()
        soportaVacio<MultiplayerNpc>()
        soportaVacio<RemoteZombiePlayer>()
    }

    @Test
    fun `modelos que vienen de ASSETS en disco`() {
        soportaVacio<LocalNode>()
        soportaVacio<LocalWay>()
        soportaVacio<LandmarkNavGraph>()
        soportaVacio<LandmarkAssetTemplate>()
    }

    @Test
    fun `partida guardada (lo que mas duele perder)`() {
        soportaVacio<GameSaveData>()
        soportaVacio<SavedNpc>()
    }
}
