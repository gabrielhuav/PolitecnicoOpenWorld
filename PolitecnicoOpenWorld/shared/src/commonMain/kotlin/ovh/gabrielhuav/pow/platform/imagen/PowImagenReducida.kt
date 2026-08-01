package ovh.gabrielhuav.pow.platform.imagen

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 🍏 DECODIFICAR UNA IMAGEN **A RESOLUCIÓN REDUCIDA**.
 *
 * POR QUÉ ESTO SÍ NECESITA `expect/actual` (y el resto de [PowImagen] no): el decodificador común
 * de Compose no deja pedir una resolución menor, y aquí eso NO es un lujo. Los atlas croma de
 * "Huelum vs. Goya" llegan a **2560×7168** — unos **73 MB en ARGB_8888 por peleador**, ×2 en
 * pantalla. En Android de gama baja eso era OOM directo (y muchas GPU viejas ni aceptan texturas
 * de ese tamaño). Por eso el juego ya decodificaba con `inSampleSize`, y perder eso al portar
 * sería una REGRESIÓN de memoria en la app que hoy está en producción.
 *
 * @param reduccion factor de reducción. 1 = tamaño original, 2 = mitad de ancho y alto (¼ de
 *   memoria), 4 = un cuarto… Valores < 1 se tratan como 1.
 *
 * ⚠️ Quien llame tiene que dividir por el MISMO factor las coordenadas que saque del JSON del
 * atlas (`src`, `origin`, cajas). En Android eso ya lo hacía `sheetSampleScale`.
 *
 * ⚠️ NO se puede usar un formato sin alfa (RGB_565 y similares): los sprites lo necesitan.
 */
expect fun decodificarReducido(bytes: ByteArray, reduccion: Int): ImageBitmap
