package ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel

// Constantes de gameplay del minijuego zombi (antes en companion object).
internal const val PLAYER_WALK_STEP = 7f
internal const val PLAYER_RUN_STEP = 13f
internal const val PLAYER_RADIUS = 28f

internal const val ZOMBIE_SPEED = 1.3f
internal const val ZOMBIE_FRAME_COUNT = 9
internal const val ZOMBIE_FRAME_INTERVAL_MS = 140L
internal const val ZOMBIE_RADIUS = 30f

internal const val STALKER_WALK_FRAME_COUNT = 4
internal const val STALKER_ATTACK_FRAME_COUNT = 4
internal const val STALKER_ATTACK_DIST = 85f

internal const val CONTACT_DIST = 44f
internal const val ZOMBIE_DAMAGE = 12f
internal const val ZOMBIE_DAMAGE_COOLDOWN_MS = 3000L

        // Regeneración de vida en el lobby: cuando el jugador no está al 100%,
        // se cura gradualmente por tick hasta tope de 100.
internal const val LOBBY_REGEN_PER_TICK = 0.35f

internal const val PLAYER_PUNCH_DAMAGE = 34f
internal const val PLAYER_ATTACK_RADIUS = 120f
internal const val PLAYER_ATTACK_COOLDOWN_MS = 600L

        // Knockback aplicado a los zombis al recibir golpes/proyectiles.
internal const val MELEE_KNOCKBACK = 46f
internal const val PROJECTILE_KNOCKBACK = 34f
        // Recoil del jugador al disparar (se empuja hacia atrás, con corrección de colisión).
internal const val PLAYER_RECOIL = 10f

internal const val PROJECTILE_SPEED = 22f
internal const val PROJECTILE_LIFETIME_MS = 1500L
internal const val PROJECTILE_DAMAGE = 50f
internal const val PROJECTILE_HIT_RADIUS = 36f
internal const val RANGED_COOLDOWN_MS = 350L
internal const val Y_HOLD_FOR_MENU_MS = 500L

internal const val SPAWN_RADIUS_MIN = 280f
internal const val SPAWN_RADIUS_MAX = 520f

internal const val TICK_MS = 33L
internal const val ITEM_PICKUP_DIST = 70f
internal const val RETURN_SPAWN_OFFSET = 40f

internal const val EXIT_GUIDE_DURATION_MS = 2000L

internal const val SKILL_DROP_CHANCE = 0.45f

internal const val SLOW_ZOMBIE_FACTOR = 0.45f
internal const val FAST_ZOMBIE_FACTOR = 1.9f
internal const val ZOMBIE_DMG_FURY_FACTOR = 2.0f
internal const val ZOMBIE_DMG_WEAK_FACTOR = 0.4f
internal const val PLAYER_DMG_BRUTE_FACTOR = 2.2f

internal const val NET_SEND_INTERVAL_MS = 100L
