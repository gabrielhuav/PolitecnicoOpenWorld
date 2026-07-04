# GUÍA DE MANTENIMIENTO — para devs/IAs NO-senior

> **Para quién:** cualquier persona o IA que vaya a tocar este código sin conocerlo y sin ser
> experta. Si sigues esta guía al pie de la letra, es difícil romper el juego. Complementa (no
> sustituye) a `00_INDEX.md`; las reglas duras viven en `09_CONVENTIONS_GOTCHAS.md`.

## 1. Antes de tocar NADA (15 minutos que ahorran días)
1. Lee `00_INDEX.md` (mapa de la carpeta) y `09_CONVENTIONS_GOTCHAS.md` COMPLETO.
2. Lee el `CHECKPOINT_*` más reciente (estado real del proyecto) y, si vas a refactorizar,
   `CHECKPOINT_SENIOR_refactor.md` (programa en curso — NO lo dupliques).
3. Localiza tu feature en los docs 03–08 y SU tabla "Key files". Pide los `.kt` que necesites;
   **nunca inventes el contenido de un archivo que no leíste**.

## 2. Las 7 reglas que rompen el juego si las ignoras
1. **Miembro vs extensión:** si una función existe como miembro del VM Y como extensión homónima,
   GANA EL MIEMBRO y la extensión está muerta. Edita el miembro; verifica ambos. (Detalle: 09 §12.)
2. **Estado inmutable:** siempre `_state.update { it.copy(...) }`. Las Views SOLO observan
   `collectAsState()` y emiten intenciones; jamás tocan repos/DAOs.
3. **CRLF:** los archivos existentes usan CRLF; consérvalo. Archivos nuevos en LF compilan igual,
   pero no "normalices" archivos que no son tuyos.
4. **Verifica con Read, no con bash:** el sandbox sirve copias TRUNCADAS de archivos grandes o
   recién editados. Tras cada edit, confirma con Read que la COLA del archivo sigue ahí.
5. **Strings de UI:** español en `values/strings.xml` + inglés en `values-en/strings.xml`, SIEMPRE
   en paridad 1:1. Claves `snake_case` con prefijo de feature.
6. **No re-introducir lo eliminado:** antes de "arreglar" algo revisa en 09 si fue una decisión
   (p. ej. shove de NPCs, mano zombi de ESCOM, submenú "Ir a…", broadcast global de apocalipsis).
7. **Perf gama baja es sagrado (09 §6):** nada de allocations por frame, caches LRU intactos, no
   subir topes de NPCs a mano (usa los factores).

## 3. Flujo de trabajo estándar (cualquier cambio)
1. **Plan chico:** un objetivo por sesión; pasos que dejen el repo COMPILABLE.
2. **Tests primero si tocas lógica pura** (routing, catálogos, guardado): están en
   `app/src/test/...`. Corre `gradlew.bat testDebugUnitTest`. Los tests son BLOQUEANTES en CI
   (`.github/workflows/pr-quality-gate.yml`).
3. Edita → verifica con Read → balance de llaves del archivo.
4. **Protocolo de docs (09 §13):** el cambio NO está terminado hasta actualizar los docs 00–09 +
   CAMPAIGN + README público (EN **y** ES). Una contradicción entre docs es un bug.
5. Entrega con lista de "qué probar en el dispositivo".

## 4. Dónde vive cada cosa (chuleta)
| Quiero tocar… | Empieza en… |
|---|---|
| Mapa exterior / NPCs / policía / vehículos | `04_MAP_EXTERIOR.md` → `features/map_exterior/` |
| Interiores / zombis / salas / matrices | `05_ZOMBIE_MINIGAME.md` → `features/interiores/` |
| Metro/Metrobús | `06_INTERIOR_METRO.md` → `TransitInteriorViewModel` + `TransitSystems` |
| Campaña / misiones / registro / replay | `CAMPAIGN/00..03` → `domain/models/campaign/` + `WorldMapMission*.kt` |
| Guardado / slots | `WorldMapSaveGame.kt` + `SaveGameRepository` (y su test) |
| Routing / snap-to-road | ⚠️ ZONA DELICADA: `domain/usecases/RoadRouter.kt` (puro, con tests) y la cadena del VM — ver `PLAN_dedup_routing.md` ANTES |
| Servidores | `08_SERVERS.md` (`Multiplayer/` mundo abierto, `MultiplayerInteriores/` interiores) |
| Ajustes / i18n / audio | `07_OTHER_FEATURES.md` |

## 5. Qué NO hacer sin supervisión/compilador
- De-dup de gemelos, package-moves, mover campos de estado entre clases, Hilt: TODO eso está
  planificado por etapas en `CHECKPOINT_SENIOR_refactor.md` + `PLAN_*.md`. Sigue el plan, paso a
  paso, UN paso por compilación. No improvises un refactor grande "de una vez".
- Find&Replace masivo (y NUNCA case-insensitive): ver el desastre documentado en 09 §0.
- Tocar `startGameLoop`/cadena de routing del VM fuera del plan de la Etapa 2/3.

## 6. Cómo pedir ayuda al dueño (mínima intervención)
Pídele SOLO: (a) `Rebuild Project`, (b) `gradlew.bat testDebugUnitTest`, (c) una prueba manual
concreta ("marca un destino y verifica que la ruta se dibuja"). Dale la lista exacta; él compila
en los puntos marcados `⏸️ CHECKPOINT COMPILACIÓN`.

## 7. Definición de "hecho" (idéntica al 09 §13)
Compila + tests en verde + docs (00–09, CAMPAIGN, README EN+ES) actualizados en el MISMO cambio +
lista de prueba manual entregada. Si no puedes cumplir una parte, DILO explícitamente.
