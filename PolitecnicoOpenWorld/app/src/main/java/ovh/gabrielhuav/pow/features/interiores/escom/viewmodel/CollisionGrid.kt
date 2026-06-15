package ovh.gabrielhuav.pow.features.interiores.escom.viewmodel

/**
 * Matriz de colisión 20 columnas × 30 filas mapeada sobre la imagen de fondo
 * de un interior.
 *
 *  - 0 = no caminable (pared, mueble, vacío)
 *  - 1 = caminable
 *  - 2+ = reservados para futuro (spawn, trigger, salida, etc.)
 *
 * Convención de orientación:
 *  - grid[fila][columna]
 *  - fila 0 = arriba de la imagen
 *  - columna 0 = izquierda de la imagen
 */
class CollisionGrid(val grid: Array<IntArray>) {

    val rows: Int = grid.size
    val cols: Int = if (grid.isNotEmpty()) grid[0].size else 0

    /**
     * Dada una posición normalizada [0, 1] del jugador, retorna true si la
     * celda correspondiente es caminable (valor != 0).
     */
    fun isWalkable(normalizedX: Float, normalizedY: Float): Boolean {
        if (rows == 0 || cols == 0) return true
        val col = (normalizedX * cols).toInt().coerceIn(0, cols - 1)
        val row = (normalizedY * rows).toInt().coerceIn(0, rows - 1)
        return grid[row][col] != 0
    }

    /**
     * Útil para mecánicas zombie futuras: leer el valor crudo de una celda
     * (por ejemplo, detectar si el jugador entró en una celda tipo "trigger").
     */
    fun cellValueAt(normalizedX: Float, normalizedY: Float): Int {
        if (rows == 0 || cols == 0) return 1
        val col = (normalizedX * cols).toInt().coerceIn(0, cols - 1)
        val row = (normalizedY * rows).toInt().coerceIn(0, rows - 1)
        return grid[row][col]
    }

    companion object {
        const val ROWS = 30
        const val COLS = 20

        /**
         * Matriz vacía con borde de paredes. Útil como punto de partida para
         * un edificio nuevo: el jugador solo puede caminar en el interior del
         * rectángulo, todo el contorno es pared.
         */
        fun emptyWithBorder(): CollisionGrid {
            val g = Array(ROWS) { row ->
                IntArray(COLS) { col ->
                    if (row == 0 || row == ROWS - 1 || col == 0 || col == COLS - 1) 0 else 1
                }
            }
            return CollisionGrid(g)
        }
    }
}