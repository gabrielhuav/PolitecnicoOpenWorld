# PLAN — Inyección de dependencias con Hilt (reemplazar los Factory manuales)

> Estado: TRABAJO FUTURO (requiere compilador + codegen KSP). Sesión de noche 2026-06-24. ANALISIS §9.4.

## 0. Situación actual
- **8 ViewModels** con `class Factory(context|...) : ViewModelProvider.Factory` a mano:
  StoryModeViewModel, InteriorViewModel, TransitInteriorViewModel, ShineCTOViewModel,
  ZombieInteriorViewModel, CollectiblesViewModel, **WorldMapViewModel**, SettingsViewModel.
- Deps construidas ad-hoc: `PowDatabase` (Room, abstract), DAOs, `RoadNetworkCache(dao)`,
  `TileCache(dao)`, `SettingsRepository(context)`, `CollectibleRepository(...)`, `OverpassRepository()`,
  `WebSocketManager(url)`, `SoundManager` (singleton vía getInstance).
- `WorldMapViewModel(application, roadNetworkCache, tileCache, settingsRepository, collectibleRepository)`
  es **AndroidViewModel** y está **Activity-scoped** (compartido por el grafo de navegación).
- Ya hay `PowApplication` (registrada en el manifest como `.PowApplication`). KSP ya está en el build
  (Room) -> Hilt-KSP encaja sin añadir KAPT.

## 1. Elección: Hilt (no Koin)
El proyecto ya usa **KSP** (Room) y AGP/Gradle nuevos. Hilt 2.56+ soporta KSP + Kotlin 2.2 + Gradle 9/AGP 9
(ver nota de versiones). Hilt da scoping de ViewModel/Activity listo (`@HiltViewModel`, `hiltViewModel()`),
que es justo lo que el WorldMapViewModel Activity-scoped necesita. (Koin evitaría codegen, pero romper el
patrón ya-KSP no aporta.)

## 2. Versiones a fijar (CONFIRMAR en el primer sync; son datos que cambian)
- Hilt **2.56+** (`com.google.dagger:hilt-android` + `hilt-android-compiler` por KSP).
- Plugin Gradle `com.google.dagger.hilt.android` (AGP 9 requerido — ya se cumple).
- `androidx.hilt:hilt-navigation-compose` (para `hiltViewModel()` en Compose).
- KSP compatible (el repo trae KSP 2.3.2; alinéalo con la matriz Hilt/KSP del momento).
Añadir al catálogo `gradle/libs.versions.toml` ([versions]+[libraries]+[plugins]) y aplicar el plugin en
root (`apply false`) + `app/build.gradle.kts`.

## 3. Pasos (incremental; 1 VM por PR, el WorldMap al final)
1. **Infra Hilt (PR 1):** plugin + deps; `@HiltAndroidApp` en `PowApplication`; crear UN módulo
   `di/DatabaseModule.kt` (`@Module @InstallIn(SingletonComponent::class)`) que provea `PowDatabase`
   (Room.databaseBuilder con `MIGRATION_7_8` + fallback) y sus DAOs. Compilar (sin migrar VMs todavía).
2. **Módulos de datos (PR 2):** `@Provides`/`@Binds` para `RoadNetworkCache(dao)`, `TileCache(dao)`,
   `SettingsRepository(@ApplicationContext ctx)`, `CollectibleRepository(...)`, `OverpassRepository()`.
   `SoundManager` -> `@Provides` envolviendo el singleton actual.
3. **Migrar 1 VM SIMPLE (PR 3):** `SettingsViewModel` -> `@HiltViewModel` + `@Inject constructor(...)`;
   borrar su `Factory`; en la pantalla usar `hiltViewModel()`. Verificar de punta a punta.
4. **Resto de VMs de interiores/menú (PRs siguientes):** Interior/Transit/ShineCTO/Zombie/Story/
   Collectibles, uno por uno (mismo patrón). Para los que reciben args de navegación (p. ej.
   `TransitInteriorViewModel(config, station, spawn)`): usar **`@AssistedInject` + `@AssistedFactory`**
   o `SavedStateHandle` con los args de la ruta. Documentar cuál por VM.
5. **`WorldMapViewModel` AL FINAL (PR final):** es AndroidViewModel + Activity-scoped. Con Hilt:
   `@HiltViewModel` y obtenerlo Activity-scoped (`hiltViewModel(activity)` o un ViewModel scoped al
   nav-graph) para preservar que SOBREVIVE a la navegación (gate `isMapReady`/loop no se reinicia, ver
   09 §12). Inyectar los 5 deps actuales; usar `@ApplicationContext`/`Application` para el contexto.
   Borrar su `Factory(context)` y actualizar el call-site en `MainActivity`/`AppNavGraph`.

## 4. Encaje con la descomposición del VM
Los managers nuevos de `PLAN_descomponer_WorldMapViewModel.md` (CombatManager, CollectiblesManager, …)
entran como `@Inject` en el módulo correspondiente -> el VM los recibe inyectados en vez de instanciarlos
con `= XManager()`. Hacer DI **antes** o **junto** a la descomposición facilita ambas.

## 5. Verificación
- Sin compilador (noche): N/A real — Hilt depende 100% de codegen KSP; no se puede validar sin compilar.
  Solo se puede revisar sintaxis de los módulos/anotaciones a ojo.
- Con compilador (AS): Rebuild (KSP genera los componentes) + `testDebugUnitTest` + arranque de la app +
  prueba de cada pantalla migrada. Hilt falla en COMPILACIÓN si falta un binding -> errores claros.
- MVVM intacto: las Views siguen observando `uiState`; solo cambia CÓMO se obtiene el VM.

## 6. Riesgos
- **Compat de versiones** Hilt/KSP/Kotlin/AGP — confirmar en el primer sync (como detekt). Si Hilt-KSP da
  guerra con Kotlin 2.2, revisar la matriz Hilt del momento o congelar versiones.
- **Activity-scope del WorldMapViewModel**: si se inyecta con scope equivocado, se RE-CREA al navegar y
  reaparece la pantalla de carga (regresión conocida, 09 §12). Probar volver de Ajustes sin recarga.
- Coexistencia con el `google-services` condicional (build sin Firebase para PRs): Hilt no lo afecta.
- No mezclar KAPT: usar Hilt **por KSP** para no duplicar procesadores de anotaciones.
