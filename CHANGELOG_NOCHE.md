# CHANGELOG DE NOCHE — Roadmap "producción senior" (autónomo)

> Sesión autónoma iniciada 2026-06-24 ~11:20 UTC. Objetivo: avanzar el roadmap de
> `README for IAS/ANALISIS_codigo.md §9` haciendo TODO lo SEGURO y dejando lo RIESGOSO en planes.
> NO se compiló (sin Android SDK en el entorno): cada cambio se verificó a mano (balance
> Kotlin-aware, EOL por archivo, referencias). Confirmar con Rebuild en Android Studio.
>
> Rutas: repo raíz = C:\...\PolitecnicoOpenWorld\ (tiene .git, .github, README.md).
> Proyecto Gradle = PolitecnicoOpenWorld\ (tiene gradlew, app/, README for IAS/).

## Estado por tanda
| Tanda | Qué | Estado |
|---|---|---|
| 1 | Normalizar EOL a CRLF + `.editorconfig` | HECHO |
| 2 | Tests JUnit de lógica pura | HECHO (3 archivos, sin correr) |
| 3 | detekt + gate de lint/tests en CI | HECHO (CLI; plugin staged) |
| 4 | KDoc + magic numbers -> const | HECHO |
| 5 | Planes (DI, descomponer VM, de-dup routing) | HECHO |

---

## TANDA 1 — Higiene EOL + .editorconfig (HECHO)

### 1.1 Normalización EOL a CRLF (6 archivos .kt que estaban en LF puro)
Convertidos LF->CRLF (solo cambian los fines de línea; contenido byte-idéntico verificado:
nb.replace(CRLF,'') == orig.replace(LF,''), y loneLF==0 tras la conversión):

| Archivo | Líneas |
|---|---|
| app/.../domain/models/ai/NpcAiManagerTraffic.kt | 565 |
| app/.../features/interiores/zombies/viewmodel/ZombieCombat.kt | 221 |
| app/.../features/map_exterior/ui/WorldMapScreenGoogle.kt | 522 |
| app/.../features/map_exterior/ui/components/PlayerCharacter.kt | 278 |
| app/.../features/map_exterior/ui/components/PlayerSkin.kt | 97 |
| app/.../features/map_exterior/viewmodel/WorldMapGameLoop.kt | 15 (tombstone) |

Verificación: re-escaneo de los 194 .kt del módulo -> 0 archivos con lone-LF
(antes: 6 LF, 0 mixtos, 188 CRLF -> ahora: 194 CRLF puros). No se tocó contenido.

### 1.2 .editorconfig (NUEVO, en la raíz del repo)
C:\...\PolitecnicoOpenWorld\.editorconfig con root=true, end_of_line=crlf (default para todo el repo),
charset=utf-8, e indentación por tipo (kt/kts=4, xml=4, toml=4, yml/json/md/js=2).
trim_trailing_whitespace=false + insert_final_newline=false para que AS NO reformatee al guardar
(evita diffs ruidosos). En la raíz del repo: EditorConfig se resuelve subiendo por el árbol de ficheros,
así cubre app/src/** (proyecto Gradle anidado) y Multiplayer*/. Archivo en CRLF.

### Observaciones (NO se tocaron — fuera de alcance / conservador)
- gradlew (script POSIX) está en CRLF en TODO el repo. Rompería ./gradlew en Linux/macOS
  (bad interpreter: /bin/sh^M), pero NO afecta hoy: el CI usa `gradle` del sistema
  (gradle/actions/setup-gradle) y Windows usa gradlew.bat. El .editorconfig lo deja en CRLF (estado
  actual) para no contradecir el archivo. Si quieres correr ./gradlew en Unix, conviértelo a LF.
- .gitattributes (raíz) tiene `* text=auto` -> git puede re-normalizar EOL al commitear. No se tocó
  (el prompt solo pidió .editorconfig). Para forzar CRLF a nivel git: añade `*.kt text eol=crlf`.

---

## TANDA 2 — Tests JUnit de lógica pura (HECHO; escritos, NO ejecutados)

NOTA: No se pudieron correr aquí: el entorno solo tiene JDK 11, sin Gradle, sin Android SDK y sin red.
El proyecto necesita JDK 21 + Gradle 9.4.1. Los tests se escribieron y se verificó el balance
Kotlin-aware + EOL; las aserciones numéricas se validaron con un modelo en Python (errores ~1e-12, muy
dentro de la tolerancia 1e-4). Confirmar con testDebugUnitTest en el Rebuild.

### Criterio de selección (conservador)
Solo lógica 100% pura (sin Context/framework en runtime). Se descartó testear CampaignEscortPolice (usa
GeoPoint/Npc -> instanciarlos en un unit test JVM es frágil) y repos/VMs (necesitan Context). Esa
cobertura se deja para Compose UI Test / instrumentados (ver plan).

### Archivos nuevos en app/src/test/java/ovh/gabrielhuav/pow/ (3, CRLF, balance OK)
1. domain/usecases/CalculateLocalCoordinatesUseCaseTest.kt (7 tests) — proyección GPS->coordenada local
   de un edificio. Invariantes: centro->(0.5,0.5); media-anchura al este->x=1.0; media-altura al
   norte->y=0.0 (eje Y invertido); rotación 90° intercambia ejes; scaleX ensancha; LocalCoordinate.isValid
   con margen por defecto y custom. La clase ya era pura (solo kotlin.math/Math).
2. domain/models/campaign/MissionCatalogTest.kt (8 tests) — API del catálogo (byId/first/orden de
   objectives) + blindaje de bugfixes: ESCOLTAR_PRANKEDY.arriveRadiusMeters==25.0 (R9), radios 0 de
   INGRESAR/BUSCAR, default 60 de IR_ENCB, constantes de la puerta ESCOM compartidas. R.string.* son ints
   generados -> sin runtime Android.
3. features/interiores/escom/viewmodel/TransitSystemsTest.kt (7 tests) — catálogo TransitSystems. El
   lambda loadStations:(Context)->... NO se invoca al construir -> leer campos es puro. Blindaje:
   spriteBaseDir=="SPRITES/PLAYER/" en METRO y METROBÚS (bug del jugador invisible del metrobús),
   map.png vs mapa.png, signos de turnstileBoardDeltaY, ejes/overlay por sistema, getters derivados.

### Verificación hecha
- Balance Kotlin-aware (ignora //, /* */ anidados, "...", triple-quote, '.'): los 3 -> ()={}=[]=0.
- EOL: los 3 en CRLF puro (loneLF=0).
- Aserciones de la math: replicadas en Python contra la fórmula real del use case -> coinciden.
- Refs/imports verificados contra el código. assertSame válido porque
  MissionCatalog.IR_ENCB === Mission1.IR_ENCB === byId("ir_encb").

### Pendiente / riesgo Rebuild
- Confirmar que R resuelve en el set de unit tests (estándar AGP; lo hace).
- Cobertura que QUEDA fuera (roadmap): routing/snap-to-road (enredado con el VM), save/load
  (SharedPreferences->Context), combate (GeoPoint/Npc). Ver PLAN_dedup_routing.md.

---

## TANDA 3 — detekt + gate de CI (HECHO; aditivo, CERO cambios al build de la app)

### Decisión de diseño (conservadora): por qué CLI y no plugin Gradle de noche
La tabla de compatibilidad de detekt indica que SOLO la línea 2.0.0-alpha (id dev.detekt) soporta
Gradle 9.x + Kotlin 2.2+; la estable 1.23.x tiene soporte LIMITADO de Gradle 9. El proyecto usa
Gradle 9.4.1 + Kotlin 2.2.10. Añadir un plugin alpha a app/build.gradle.kts SIN poder hacer un
gradle sync arriesga romper la configuración -> rompería también assembleDebug/bundleRelease (workflow de
release). Por eso, y porque el plan enmarca TANDA 3 como "config aditiva, no toca código de app", el gate
corre detekt por CLI (no por plugin): CERO cambios a archivos de build -> imposible romper el build
existente. La integración como plugin queda lista para pegar (abajo) para cuando se confirme con un sync.

### Archivos nuevos
1. PolitecnicoOpenWorld/config/detekt/detekt.yml — config base CONSERVADORA: prioriza CORRECCIÓN
   (potential-bugs, coroutines, empty-blocks, UnusedPrivateMember/Property -> caza gemelos muertos) y
   relaja estilo/nombres/MagicNumber/LongMethod (serían ruido en código grande ya en producción).
   config.validation=false (tolerante a versión: una clave de regla movida NO tumba el análisis).
   YAML validado con PyYAML. CRLF.
2. .github/workflows/pr-quality-gate.yml (NUEVO) — gate en PR ABIERTO
   (types: [opened, synchronize, reopened], paths: PolitecnicoOpenWorld/**):
   - job unit-tests: JDK 21 (temurin) + gradle/actions/setup-gradle@v4 (gradle 9.4.1) + crea
     secrets.properties (igual que release) + gradle :app:testDebugUnitTest. NO escribe
     google-services.json -> el plugin google-services no se aplica y compila/testea sin Firebase. Sube el
     reporte de tests como artifact.
   - job detekt: descarga la CLI de detekt (v1.23.8 desde GitHub Releases) y corre
     --config config/detekt/detekt.yml --build-upon-default-config --input app/src/main/java (+baseline si
     existe). Sube SARIF.
   - NO toca android-release.yml (ese corre en types: [closed]/merge; sin solape). Validado con PyYAML.

### Verificación hecha
- pr-quality-gate.yml y detekt.yml -> PyYAML safe_load OK; android-release.yml re-validado intacto.
- Estructura del workflow: trigger pull_request correcto, jobs unit-tests + detekt. EOL CRLF.
- Reutiliza el patrón EXACTO del workflow de release (JDK/gradle/secrets) para mínima sorpresa.

### Pendiente / riesgos para el primer PR
- No se pudo ejecutar el workflow (sin CI/red aquí). Confirmar en el primer PR.
- detekt CLI 1.23.8 y Kotlin 2.2: la CLI parsea con un frontend Kotlin más viejo; si algún archivo 2.2 no
  parsea, sube la CLI a una 2.0.0-alpha (artefacto dev.detekt). No bloquea el build (solo el job).
- Primer run de detekt puede salir ROJO por issues preexistentes -> genera el baseline UNA vez (comando
  exacto comentado en el workflow) y commitéalo; luego solo fallan issues NUEVOS.
- testDebugUnitTest en CI asume el Android SDK del runner ubuntu-latest (igual que el release actual).

### (Opcional) Wiring del PLUGIN Gradle de detekt — pegar cuando puedas hacer gradle sync
- gradle/libs.versions.toml -> [plugins]: detekt = { id = "dev.detekt", version = "2.0.0-alpha.5" }
- app/build.gradle.kts -> plugins { ... }: alias(libs.plugins.detekt)
- app/build.gradle.kts -> al final: bloque detekt { buildUponDefaultConfig=true;
  config.setFrom(files("$rootDir/config/detekt/detekt.yml")) }
Luego el job detekt del workflow puede cambiar a `gradle :app:detekt`. Aplicar SOLO tras confirmar el
sync (es alpha): si rompe, borra las piezas y vuelve a la CLI.

---

## TANDA 4 — KDoc + magic numbers -> const (HECHO; conservador y verificado con Read)

Contexto: el código YA extrae casi todas las constantes a `const val` nombradas y documenta clases con
comentarios `//`. Por eso TANDA 4 fue deliberadamente PEQUEÑA y 100% verificada con la herramienta Read
(ver GOTCHA del sandbox abajo); el resto queda como checklist para hacer con compilador.

Cambios (2 archivos; solo aditivos; balance correcto por construcción):
1. domain/models/ai/NpcAiManager.kt — añadido KDoc de clase (antes NO tenía; PoliceManager ya traía un
   bloque `//`). Describe la responsabilidad, la API pública (setLandmarks/setExteriorCollisions/
   updateRoadNetwork/setServerNpcs/addServerNpcs/triggerFear/updateNpcs/npcs StateFlow) y el escalado
   `popFactor` (gama×urbano×slider). Solo comentario -> 0 riesgo semántico. Verificado vía Read: KDoc en
   líneas 21-37, `class NpcAiManager {` en 38, cierre de clase intacto en 903.
2. domain/usecases/CalculateLocalCoordinatesUseCase.kt — magic number 0.15f (margen de tolerancia, default
   de LocalCoordinate.isValid) -> `companion object { const val DEFAULT_VALID_MARGIN = 0.15f }`; el default
   pasa a `margin: Float = DEFAULT_VALID_MARGIN`. Valor IDÉNTICO -> los tests de TANDA 2 siguen verdes.
   Verificado vía Read: companion en líneas 24-27, cierre del archivo en 81.

GOTCHA NUEVO del entorno (clave para el Rebuild y para la próxima IA) — candidato a 09 §12:
- El MOUNT de bash sirve copias DESINCRONIZADAS/TRUNCADAS de archivos EXISTENTES que se editaron con las
  herramientas (Edit/Write): los recorta a ~su tamaño PREVIO. La herramienta READ ve el archivo REAL.
  REGLAS: (a) verifica los edits con Read, NUNCA con bash; (b) NUNCA hagas bash-read + bash-write de un
  archivo recién editado por herramienta (persistirías la copia truncada). Esto fue lo que truncó este
  changelog a mitad; se recuperó reescribiéndolo ENTERO por `cat <<'EOF'` (bash sirve completos los
  archivos que el propio bash creó). La normalización EOL de TANDA 1 fue segura porque tocó archivos que
  bash leyó/escribió sin intervención de las herramientas.

Checklist de magic numbers / KDoc PENDIENTES (hacer en Android Studio con compilador):
- Render (NativeOsmMap/WorldMapScreen*): radios de culling/tamaños dp/alfas sueltos -> muchos ya son const;
  los restantes viven en hot-paths >1000 líneas -> editar en AS (gotcha de truncación del sandbox).
- WorldMapViewModel (~125KB): NO editar a ciegas. KDoc de su API pública conviene hacerlo en AS con el
  outline a la vista (y respetando el gotcha gemelo miembro-vs-extensión).

---

## TANDA 5 — Planes del trabajo RIESGOSO (HECHO; solo documentacion, NO ejecutado)

3 planes nuevos en `README for IAS/` (escritos por bash heredoc, CRLF, completos):
- **PLAN_dedup_routing.md** — de-dup de la cadena de routing CON tests PRIMERO: extraer un `RoadRouter`
  puro + tests golden-master (redes sinteticas), luego de-dup hoja->raiz, 1 funcion por compilacion. Cita
  el NO-TOCAR de 09 §12 y los call-sites miembro (VM ~1218-1301) vs extension muerta (WorldMapRouting.kt).
- **PLAN_descomponer_WorldMapViewModel.md** — god-object -> managers/use-cases con sub-estado; tecnica de
  `WorldMapState` como FACHADA combinada (`combine()`) para migrar SIN tocar Views; orden incremental
  (DesignerManager/CollectiblesManager primero, RoutingUseCase al final). Mapa de los ~20 grupos de estado
  (111 campos) -> managers.
- **PLAN_DI_hilt.md** — Hilt 2.56+ (KSP) sobre los 8 `Factory` manuales; modulos (DB/caches/repos),
  `@HiltViewModel`, `hilt-navigation-compose`; `WorldMapViewModel` (AndroidViewModel, Activity-scoped) al
  final. Versiones a confirmar en el primer sync.
- **Item A** (colapsar pantallas/overlays de transporte) NO se replanea: ya esta en ANALISIS §2.2/§8 como
  future work; solo se cita.
- **Extraccion del god-object NO ejecutada** (ante la duda, en plan): toda extraccion real mueve campos +
  `combine()` = requiere compilador. La unica extraccion aislada hecha es el const de TANDA 4.

## Docs actualizados (protocolo 09 §13)
- **ANALISIS_codigo.md §9**: bloque "AVANCE NOCHE 2026-06-24" (que del roadmap quedo HECHO vs en PLAN).
- **09_CONVENTIONS_GOTCHAS.md §12**: gotcha NUEVO (bash sirve copias truncadas de archivos editados por
  herramienta; verificar con Read). INCIDENTE real: el bash round-trip truncó 09 §13 ~24 lineas; se
  RESTAURO con la herramienta Edit desde el contenido original. 09 quedo en 967 lineas, §13 integro
  (verificado con Read).
- **README.md publico**: NO tocado (nada de esta noche es user-facing: tests/CI/EOL/docs internos).

## RESUMEN FINAL — para el Rebuild de la manana
**Archivos NUEVOS:** 3 tests (`app/src/test/.../{CalculateLocalCoordinatesUseCase,..campaign/MissionCatalog,
..escom/viewmodel/TransitSystems}Test.kt`), `PolitecnicoOpenWorld/config/detekt/detekt.yml`,
`.github/workflows/pr-quality-gate.yml`, `.editorconfig` (raiz del repo), 3 `PLAN_*.md`, este `CHANGELOG_NOCHE.md`.
**Archivos MODIFICADOS:** 6 `.kt` (LF->CRLF, contenido byte-identico), `NpcAiManager.kt` (+KDoc de clase),
`CalculateLocalCoordinatesUseCase.kt` (+`const DEFAULT_VALID_MARGIN`), `ANALISIS_codigo.md` (+§9 avance),
`09_CONVENTIONS_GOTCHAS.md` (+§12 gotcha; §13 restaurado).

**Riesgos / donde mirar si algo falla en el Rebuild:**
1. `testDebugUnitTest`: si NO compilan los tests, revisar imports/firmas en los 3 `*Test.kt` (verificados a
   mano + math validada en Python, NO compilados). Si fallara por `R`, basta con que el modulo compile.
2. `pr-quality-gate.yml` (primer PR): detekt CLI puede salir ROJO por issues preexistentes -> generar el
   baseline (comando comentado en el workflow). `testDebugUnitTest` usa el Android SDK del runner (igual que
   el release). detekt 1.23.8 vs Kotlin 2.2: si no parsea, subir a CLI 2.0.0-alpha.
3. `NpcAiManager.kt` / `CalculateLocalCoordinatesUseCase.kt`: cambios SOLO aditivos (KDoc + companion const);
   verificados con Read (cierres de clase intactos: NpcAiManager L903, UseCase L81). Balance por construccion.
4. EOL: los 6 `.kt` y los archivos nuevos en CRLF (verificado). En `09`/`ANALISIS`, lo escrito por bash es
   CRLF; la restauracion de `09 §13` (por Edit) -> verificar EOL en AS por si quedo LF (cosmetico, no rompe
   build; `git add --renormalize` si hace falta).
5. **NADA toca `android-release.yml` ni el build Gradle de la app** (sin cambios en `*.gradle.kts`/catalogo)
   -> el deploy a Play (al mergear) sigue EXACTAMENTE igual.

Lo SEGURO esta agotado; lo RIESGOSO (DI, god-object, de-dup routing) queda en los 3 `PLAN_*.md` listo para
hacerse en Android Studio con compilador, paso a paso.

---

## INCIDENTE 2026-06-24 (post-sesion) — 2 archivos corrompidos en TANDA 1; RECUPERAR de git

Al correr los tests, el modulo no compilo: "Unresolved reference 'idlePath'" (y walk/run/specialPath).
CAUSA: la normalizacion EOL de TANDA 1 (bash read+write) toco 2 archivos cuyo contenido el MOUNT de bash
sirvio TRUNCADO; al re-escribirlos se persistio la version dañada (mismo gotcha 09 §12, ahora confirmado
que tambien corrompe, no solo "muestra mal"):
- `app/src/main/java/ovh/gabrielhuav/pow/features/map_exterior/ui/components/PlayerCharacter.kt`
  -> cola perdida desde el byte 14176 + relleno de 2826 bytes NUL (\x00). Falta la cola con las
     extensiones `PlayerSkin.idlePath/walkPath/runPath/specialPath` -> por eso "Unresolved reference".
- `app/src/main/java/ovh/gabrielhuav/pow/features/map_exterior/ui/components/PlayerSkin.kt`
  -> truncado a mitad de la linea 98 (skin `robot` incompleto; falta cierre del enum + PLAYER_BODY_STANDARD_DP).
Por que TANDA 1 no lo detecto: el chequeo miro loneLF/CRLF (que seguian "ok") pero NO buscó bytes NUL ni
verifico la COLA con la herramienta Read. LECCION: tras convertir EOL por bash, verificar NUL + cola por Read.

NO corrompidos (verificados por Read, cola intacta): NpcAiManagerTraffic.kt, ZombieCombat.kt,
WorldMapScreenGoogle.kt, WorldMapGameLoop.kt. El resto del trabajo de la noche (tests, CI, planes, KDoc en
NpcAiManager/UseCase) esta intacto.

RECUPERACION (los 2 archivos estan en git = el commit de respaldo de la rama de noche):
- Android Studio: ventana Git -> en "Changes" selecciona PlayerCharacter.kt y PlayerSkin.kt -> click derecho
  -> Rollback (Ctrl+Alt+Z). Vuelven al original (EOL LF, compila igual).
- O por terminal desde la raiz del repo:
  git checkout HEAD -- "PolitecnicoOpenWorld/app/src/main/java/ovh/gabrielhuav/pow/features/map_exterior/ui/components/PlayerCharacter.kt" "PolitecnicoOpenWorld/app/src/main/java/ovh/gabrielhuav/pow/features/map_exterior/ui/components/PlayerSkin.kt"
Tras restaurar -> Rebuild -> los tests corren. (.editorconfig tambien se habia corrompido con NUL; ya se
reescribio limpio en esta sesion.)

### RESOLUCION (2026-06-24, mismo dia) — RECUPERADO directamente de git
Los 2 archivos se restauraron desde `git show HEAD:<ruta>` (el commit tiene la version limpia) y se
re-escribieron por bash (sin pasar por la copia desincronizada del mount):
- PlayerSkin.kt -> 139 lineas, define `idlePath/walkPath/runPath/specialPath` (lineas 117-120),
  `bodyFraction()` y `companion { PLAYER_BODY_STANDARD_DP = 23.5f }`. Verificado por Read. (NUL=0)
- PlayerCharacter.kt -> 278 lineas, completo, termina en el mapa de frames. Verificado. (NUL=0)
Quedan en EOL **LF** (su estado original en git; compila igual). NO re-normalizar por bash. Si se quiere
CRLF, hacerlo en Android Studio. .editorconfig ya quedo limpio. -> Rebuild y los tests deben correr.
