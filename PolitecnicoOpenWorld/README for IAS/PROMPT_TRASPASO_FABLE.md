# PROMPT · Traspaso a Fable 5 — auditar el motor y continuar

> **Empieza LEYENDO `README for IAS\_SESION_ACTUAL.md`** (estado vivo). Rutas RELATIVAS al repo
> (ver ahí §Rutas por PC; la raíz absoluta del escritorio difiere de la laptop). Todo lo de abajo
> ya está commiteado y **compila + testea** (`.\gradlew.bat compileDebugKotlin testDebugUnitTest`).

## Dónde quedó (2026-07-22, Opus 4.8)

**Los 6 modos funcionan y están limpios** (probado por el dueño), **incluida gama baja** (jugable;
no del todo optimizado —FPS sin medir— pero va bien → la Fase 4 NO es urgente).

**Motor SF · Fase 1 (red de seguridad) HECHA y superada.** Se extrajo la lógica PURA del
`StreetFighterViewModel` a `domain/models/streetfighter/`, con el VM delegando por alias
(comportamiento idéntico) y ~35 tests de caracterización verdes (antes: 0):
`SfStateMachine`, `SfDamage`, `SfPhysics`, `sfUsableBonusPowerCount`, `SfBox`. Commits `SF motor Fase 1a…1f`.

Además, esta sesión de Opus cerró muchos bugs de jugabilidad (subtítulos secuenciados, IA usa
fatality/súper, IA vs IA en el mapa correcto, arcade 3 derrotas, controles "Neón Arcade"
L1/L2/R1/R2 consistentes en tutorial + hoja de combos, pausa con logo, stun/mareo de Fable
integrado, La Llorona con patada larga/overhead importados —adiós atlas ALPHA/crash P0—,
overhead/patada larga jugables, agacharse ya no se pega —zona muerta del joystick—, bloqueo = 0
daño en normales, y se quitó el hint de controles viejo del fondo de la pelea).

## Tu cola de trabajo (por prioridad)

1. **AUDITAR la Fase 1 del motor** → `SF\PROMPT_FABLE_auditar_y_continuar_motor.md` (Parte A):
   confirma que la extracción es IDÉNTICA (`git show` de cada `Fase 1x`), tests verdes, y juega
   los 6 modos para asegurar que nada cambió.
2. **CONTINUAR el motor (Fases 2-5)** → mismo prompt (Parte B). Fase 2 (`SfEngine` puro,
   incremental), Fase 3 (**modos como estrategia** = tu meta: modo nuevo = clase nueva), Fase 5
   (detekt a 0, partir la Screen, KDoc). **Fase 4 (gama baja) baja de prioridad** —ya es jugable—
   pero cae la parte medible (liberar/reutilizar bitmaps) cuando tengas dispositivo. **Cada pieza
   que muevas ya tiene su test de la Fase 1 como red; valida los 6 modos + multiplayer en cada paso.**
3. **Pendientes NO-motor** (ver `_SESION_ACTUAL.md` §4): audio (29 clips largos + 5 LUFS → Gemini),
   arte diferido de La Presidenta (fatality V2 + metamorfosis nuevas, el dueño decide), animaciones
   congeladas (necesitan arte). Correcciones HUMANAS de audio/sprites (Paparazzi 5 / Señor tienda /
   Tzitzimime): las hace el dueño con Gemini en el escritorio →
   `PROMPT_GEMINI_correcciones_humanas_audio_sprites.md`.

## Reglas (de `09_CONVENTIONS_GOTCHAS.md` — LÉELO antes de tocar)
MVVM (estado inmutable, `_state.update{it.copy}`), comentarios/strings en español, **paridad ES+EN**,
`runStateHandler` `when` EXHAUSTIVO + `validFrom` para cada estado nuevo, **CRLF en .kt**, pasos
pequeños verificables (un commit por pieza), y **medir antes de afirmar** (aquí abundan los
"está al 100%" falsos). Tras cada fase: compilar + tests + **jugar los 6 modos**.
