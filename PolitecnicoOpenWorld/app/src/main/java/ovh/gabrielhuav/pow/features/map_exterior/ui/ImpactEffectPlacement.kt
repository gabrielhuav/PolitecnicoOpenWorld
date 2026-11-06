package ovh.gabrielhuav.pow.features.map_exterior.ui

/** El impacto compuesto en el centro solo representa al jugador mientras la cámara lo sigue. */
internal fun shouldRenderPlayerAnchoredImpact(isUserPanningMap: Boolean): Boolean =
    !isUserPanningMap
