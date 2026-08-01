package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

/** Resultado de una pelea de arcade (dirige el overlay de fin del modo arcade). */
enum class SfArcadeOutcome {
    NONE,       // no aplica (fuera del arcade o pelea en curso)
    WON,        // ganaste el escalón → CONTINUAR al siguiente rival
    LOST,       // perdiste → REINTENTAR (retrocede 1 pelea)
    COMPLETED,  // venciste al jefe final (Prankedy) → ¡campeón!
}
