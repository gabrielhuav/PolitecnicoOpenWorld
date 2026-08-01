package ovh.gabrielhuav.pow.platform.imagen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🖼️ Carga una imagen de `assets/` **sin bloquear el hilo de UI**, con caché.
 *
 * Sustituye al patrón `remember(ruta) { PowImagen.deAsset(ruta) }`, que decodifica **durante la
 * composición** — en una rejilla eso es decodificar N imágenes antes de poder pintar el primer
 * frame, y repetirlo cada vez que el item vuelve a entrar en pantalla.
 *
 * Devuelve `null` mientras carga (o si el asset no existe): el llamante pinta un hueco.
 *
 * ⚠️ **Si ya está en memoria, se devuelve en la MISMA composición**, sin pasar por `null`. Ese
 * detalle es el que evita el parpadeo al hacer scroll hacia atrás: sin él, cada item que reaparece
 * mostraría un frame de hueco gris aunque su imagen ya estuviera decodificada.
 *
 * @param reduccion factor de reducción al decodificar (1 = original, 2 = mitad de lado, ¼ de
 *   memoria). Elige el menor que siga dando más píxeles que el tamaño real en pantalla.
 */
@Composable
fun rememberImagenDeAsset(ruta: String, reduccion: Int = 1): ImageBitmap? {
    var imagen by remember(ruta, reduccion) {
        mutableStateOf(PowImagenCache.enMemoria(ruta, reduccion))
    }
    LaunchedEffect(ruta, reduccion) {
        if (imagen == null) {
            // `Dispatchers.Default` y no `IO`: `IO` no existe en Kotlin/Native. Es el mismo
            // dispatcher que ya usa `StreetFighterScreen` para cargar los atlas de pelea.
            imagen = withContext(Dispatchers.Default) { PowImagenCache.cargar(ruta, reduccion) }
        }
    }
    return imagen
}
