# Temporary Power-Up: Divine Weapon (Arma Divina)

Implement a new power-up that zombies can drop upon defeat. This power-up, "Arma Divina", will temporarily grant the player a powerful ranged weapon capable of one-hit kills.

## Multiplayer Note
The current zombie minigame uses a local-authoritative model for zombies and items. This means:
- **Zombies and Drops are Local**: Each player sees and interacts with their own set of zombies and item drops.
- **Player State is Shared**: Other players will see you moving, your health, and your current action (like firing).
- **Power-up Effect**: The "Arma Divina" effect is purely local to your client. When you pick it up, you get the damage boost and switch to ranged mode. Other players will see you firing projectiles more frequently or effectively, but they won't "lose" the item if you pick it up because items are not synced globally yet.

## Proposed Changes

### [Zombie Models]

#### [ZombieModels.kt](file:///C:/Users/Jorge Paniagua/Desktop/PolitecnicoOpenWorld/PolitecnicoOpenWorld/app/src/main/java/ovh/gabrielhuav/pow/domain/models/zombie/ZombieModels.kt)

- Add `ARMA_DIVINA` to the `SkillEffect` enum with a duration of 12 seconds.

```kotlin
enum class SkillEffect(...) {
    ...
    ARMA_DIVINA("Arma Divina", isTrap = false, durationMs = 12000L, assetKey = "arma_divina")
}
```

---

### [Zombie Game Logic]

#### [ZombieGameViewModel.kt](file:///C:/Users/Jorge Paniagua/Desktop/PolitecnicoOpenWorld/PolitecnicoOpenWorld/app/src/main/java/ovh/gabrielhuav/pow/features/zombie_minigame/viewmodel/ZombieGameViewModel.kt)

- Update `playerDamageFactor()` to return a high value (e.g., 5.0f) when `ARMA_DIVINA` is active to ensure one-hit kills.
- Update `applyEffect()` to automatically switch the player to `RANGED` combat mode when `ARMA_DIVINA` is picked up.
- (Optional) Implement a faster firing rate for `ARMA_DIVINA`.

```kotlin
// In playerDamageFactor()
if (hasEffect(SkillEffect.ARMA_DIVINA)) return 5.0f

// In applyEffect()
SkillEffect.ARMA_DIVINA -> {
    val now = System.currentTimeMillis()
    _state.update { cur ->
        val withoutSame = cur.activeEffects.filter { it.effect != effect }
        cur.copy(
            activeEffects = withoutSame + ActiveEffect(effect, now + effect.durationMs),
            combatMode = CombatMode.RANGED
        )
    }
}
```

---

### [Zombie UI & HUD]

#### [ZombieHud.kt](file:///C:/Users/Jorge Paniagua/Desktop/PolitecnicoOpenWorld/PolitecnicoOpenWorld/app/src/main/java/ovh/gabrielhuav/pow/features/zombie_minigame/ui/ZombieHud.kt)

- Add a custom visual representation for the `ARMA_DIVINA` item on the ground in `SkillGroundItem`. It will be a golden circle with a specific icon (e.g., a lightning bolt or a sword).

#### [ZombieGameScreen.kt](file:///C:/Users/Jorge Paniagua/Desktop/PolitecnicoOpenWorld/PolitecnicoOpenWorld/app/src/main/java/ovh/gabrielhuav/pow/features/zombie_minigame/ui/ZombieGameScreen.kt)

- Update projectile rendering to change appearance (larger size and different color, like Cyan or Gold) when the `ARMA_DIVINA` effect is active.

---

### [Auditorium Collisions]

#### [ZombieRoomCatalog.kt](file:///C:/Users/Jorge Paniagua/Desktop/PolitecnicoOpenWorld/PolitecnicoOpenWorld/app/src/main/java/ovh/gabrielhuav/pow/domain/models/zombie/ZombieRoomCatalog.kt)

- Add specific collisions for the "za_auditorio" room, including the stage and the three blocks of benches.

```kotlin
// Approximate coordinates for za_auditorio collisions
val auditorioCollisions = listOf(
    // Escenario (Stage)
    NormRect(0.18f, 0.08f, 0.82f, 0.35f),
    // Bancas Izquierda
    NormRect(0.08f, 0.45f, 0.33f, 0.88f),
    // Bancas Centro
    NormRect(0.38f, 0.45f, 0.62f, 0.88f),
    // Bancas Derecha
    NormRect(0.67f, 0.45f, 0.92f, 0.88f),
    // Paredes perimetrales (opcional si no están ya en la lógica de límites)
    NormRect(0f, 0f, 1f, 0.05f), // Arriba
    NormRect(0f, 0.95f, 1f, 1f), // Abajo
    NormRect(0f, 0f, 0.05f, 1f), // Izquierda
    NormRect(0.95f, 0f, 1f, 1f)  // Derecha
)
```

## Verification Plan

### Manual Verification
- **Drop Verification**: Kill zombies until an "Arma Divina" item drops. (The drop is random, so it might take a few tries).
- **Pickup & Switch**: Verify that picking up "Arma Divina" displays a toast message and automatically switches the combat mode to "ARMA" (Ranged).
- **One-Hit Kill**: Verify that projectiles fired while "Arma Divina" is active kill zombies in a single hit.
- **Visuals**: Verify that the projectiles look different (larger/different color) while the power-up is active.
- **Duration**: Verify that the power-up expires after 12 seconds and damage returns to normal.
