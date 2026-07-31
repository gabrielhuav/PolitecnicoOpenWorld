package ovh.gabrielhuav.pow.domain.models.ai

/** Estado de animación visible de Prankedy en el mapa. */
enum class PrankedyAnimState {
    IDLE,          // parado (p_idle_#.webp)
    WALK,          // caminando tranquilo (p_walk_#.webp)
    RUN,           // corriendo para seguir al jugador (p_run_#.webp)
    RUN_TANQUE,    // sprint de ataque hacia enemigo (p_run_tanque_#.webp)
    ATTACK,        // animación de golpe/lanzamiento (p_attack_#.webp)
}

/** Fase del ciclo de vida del compañero. */
enum class PrankedyPhase {
    NOT_HIRED,   // vagabundo: existe en el mapa, aún no contratado
    HIRED,       // contratado: sigue y defiende al jugador
    DEAD,        // muerto: en cooldown de respawn
}
