package ovh.gabrielhuav.pow.platform.tiempo

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * `System.currentTimeMillis()` para las dos plataformas.
 *
 * ⚠️ **Es un reloj de ÉPOCA, no monótono, y eso es DELIBERADO.** La tentación es usar
 * `TimeSource.Monotonic` (que es lo que hace `PoliceManager` para generar ids), pero aquí no vale:
 * hay marcas de tiempo que **cruzan la frontera del módulo**. `NpcAiManager` escribe
 * `Npc.fearUntil` / `aggroUntil` y quien las compara es el `WorldMapViewModel`, que sigue en
 * `:app` leyendo `System.currentTimeMillis()`. Dos relojes con orígenes distintos ahí no dan un
 * error de compilación ni rompen un test: dan un `aggroUntil` de cinco mil contra un `now` de un
 * billón y medio, o sea **todos los NPCs pierden el miedo y la agresión al instante**. Se vería
 * jugando, y tarde.
 *
 * Mientras quede UNA sola línea comparando estas marcas con `System.currentTimeMillis()`, este
 * reloj tiene que seguir siendo de época. El día que no quede ninguna, se puede reconsiderar —
 * y ese día hay que cambiar los dos lados a la vez.
 *
 * `kotlin.time.Clock` es experimental en Kotlin 2.3, de ahí el `@OptIn`; el valor que devuelve es
 * exactamente el mismo que `System.currentTimeMillis()` en Android.
 */
@OptIn(ExperimentalTime::class)
fun ahoraMs(): Long = Clock.System.now().toEpochMilliseconds()
