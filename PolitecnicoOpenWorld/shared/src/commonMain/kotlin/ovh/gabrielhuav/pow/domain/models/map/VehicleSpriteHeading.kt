package ovh.gabrielhuav.pow.domain.models.map

/**
 * Rumbo del frame de sprite de un coche a partir del rumbo de conducción (`vehicleRotation`, 0 = norte,
 * horario). Con la cámara norte-arriba el coche del jugador, los NPC y el coche abandonado deben
 * compartir esta misma fórmula para verse idénticos.
 */
fun vehicleSpriteHeading(vehicleRotation: Float): Float {
    val heading = (vehicleRotation + 270f) % 360f
    return if (heading < 0f) heading + 360f else heading
}
