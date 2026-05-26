# Walkthrough - Arma Divina & Auditorium Collisions

I have implemented the "Arma Divina" power-up and added physical collisions to the Auditorium room in the zombie minigame.

## Changes Made

### 1. Arma Divina Power-Up
- **New Effect**: Added `ARMA_DIVINA` to `SkillEffect`. It has a 12-second duration.
- **Logic**:
    - Picking up the item automatically switches the player to **Ranged Mode**.
    - Grants a **5.0x damage multiplier**, allowing one-hit kills on zombies.
- **Visuals**:
    - **Ground Item**: Represented by a golden circle with a white lightning bolt icon.
    - **Projectiles**: When the effect is active, projectiles become **larger** and change color to **Cyan/Aqua** with a blue border.

### 2. Auditorium Collisions
- Added physical collision boundaries to the `za_auditorio` room in `ZombieRoomCatalog.kt`.
- **Stage**: The raised area at the top of the room is now impassable.
- **Benches**: The three main blocks of seating are now solid obstacles that the player must walk around.
- **Perimeter**: Added invisible walls at the edges of the room to prevent the player from walking off-screen.

## Verification Summary

### Automated Checks
- Ran `analyze_file` on all modified files (`ZombieModels.kt`, `ZombieGameViewModel.kt`, `ZombieHud.kt`, `ZombieGameScreen.kt`, `ZombieRoomCatalog.kt`).
- No critical syntax errors or unresolved references were found after fixing initial import issues.

### Manual Verification (User)
- **Power-up**: Defeat zombies in any building until "Arma Divina" drops. Verify it switches you to "ARMA" mode and kills in one hit.
- **Collisions**: Enter the "Auditorio" and try to walk through the stage or the benches. You should be blocked by them.
