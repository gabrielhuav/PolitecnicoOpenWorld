# PLAN DE NOCHE — Roadmap a "producción mantenida por seniors" (autónomo)

> Generado 2026-06-24. Objetivo: avanzar SIN intervención del dueño el roadmap de ANALISIS §9.

## Cómo usar esto
1. (30 s, antes de dormir) En **GitHub Desktop**: crea y cámbiate a una rama nueva, ej. `noche/senior-prod`.
   Así NADA de lo de la noche toca `main`.
2. Abre una conversación **NUEVA** con el asistente y pega el **PROMPT** de la siguiente sección, tal cual.
3. En la mañana: lee `CHANGELOG_NOCHE.md` (lo genera el asistente en la raíz del repo), haz **Rebuild** en
   Android Studio y revisa/commitea/descarta por tanda. Si algo no compila, el changelog dice dónde mirar.

---

## PROMPT (copia/pega TAL CUAL en la conversación nueva)

```
Trabaja en "Politécnico Open World" (POW), juego Android Kotlin + Jetpack Compose + MVVM estricto por
feature. Ruta: C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld\

MODO: AUTÓNOMO toda la noche, SIN intervención del usuario (está dormido). NO preguntes NADA; si dudas,
elige la opción MÁS CONSERVADORA y sigue. Hay rama de seguridad y commit de respaldo; acepto el riesgo.

OBJETIVO: avanzar el ROADMAP "a nivel producción senior" de `README for IAS/ANALISIS_codigo.md §9`
(tests, descomponer el god-object, retirar el patrón gemelo, DI, lint en CI, separar archivos grandes,
higiene). Haz TODO lo SEGURO de forma completa; lo RIESGOSO (requiere compilador) déjalo en PLANES
detallados, NO lo ejecutes a ciegas.

CONTEXTO OBLIGATORIO ANTES DE TOCAR NADA: lee la carpeta `README for IAS` (00–09 + ANALISIS + PROMPT_*).
En especial 09 §12 (GOTCHAS: gemelos miembro-vs-extensión; truncación del sandbox; z-order de overlays;
extraer Activity→función top-level), 04 (tabla Key files), ANALISIS §8 y §9.

RESTRICCIONES (CRÍTICAS, no las rompas):
- NO PUEDES COMPILAR. Verifica TÚ cada cambio: balance de llaves/paréntesis con un chequeo KOTLIN-AWARE
  (ignora strings, comentarios y plantillas `${}`); sin referencias colgantes; imports correctos.
- Conserva el FIN DE LÍNEA por archivo (fuentes en CRLF; algunos nuevos en LF). No 'truques' el archivo.
- GOTCHA de truncación: el sandbox/bash puede servir COPIAS TRUNCADAS de archivos grandes (~>120 KB).
  Tras CADA escritura de un archivo grande, verifica su COLA y el cierre de clase con la herramienta
  READ (otra ruta de acceso, ve el archivo real); recupera con `git show HEAD:<archivo>` si hace falta.
- NO crees gemelos miembro-vs-extensión NUEVOS (al mover una función a extensión, ELIMINA el miembro).
- Trabaja SOLO en la rama actual (el usuario ya creó una rama de noche; NO es main). NUNCA cambies de
  rama ni toques main. NO dependas de `git commit/checkout` (el índice del sandbox suele estar bloqueado:
  'index.lock: operation not permitted'): solo EDITA archivos; el usuario commitea en la mañana.
- Avanza POR PARTES verificables, UNA a la vez. Mantén un `CHANGELOG_NOCHE.md` en la RAÍZ del repo donde
  anotas CADA cambio (archivo, qué, por qué, verificación) y el estado. Es tu entregable.

ORDEN DE TRABAJO:

TANDA 1 — Higiene (riesgo ~nulo, NO cambia semántica):
- Normaliza EOL a CRLF en TODOS los .kt que estén en LF (parciales nuevos del VM, Transit*, AppNavGraph,
  SettingsSections…). Verifica con python que cada uno quede CRLF puro (0 lone-LF).
- Crea/ajusta `.editorconfig` en la raíz fijando `end_of_line = crlf` + indentación, para no re-mezclar.

TANDA 2 — Tests de lógica pura (ALTO valor):
- Primero intenta si SE PUEDEN correr: `./gradlew tasks` y `./gradlew testDebugUnitTest` (puede faltar el
  Android SDK en el sandbox). Si NO se puede correr, ESCRIBE los tests igual y verifica balance a mano,
  dejándolo CLARO en el changelog (necesitan Rebuild para confirmar).
- Identifica funciones PURAS testeables sin Android (geo/math: distancias, nearest-point/snap-to-road;
  MissionCatalog/CampaignObjective; utils de parsing). Donde la lógica esté enredada con Android,
  EXTRAE la parte pura a una función/objeto testeable (extracción conservadora) y testéala.
- Escribe tests JUnit en `app/src/test/...`. Conservador: solo lógica clara y APIs estables. Documenta
  qué quedó cubierto y qué NO (por dependencia de Context/Android).

TANDA 3 — Lint/análisis estático (config ADITIVA, no toca código de app):
- Añade **detekt** como plugin Gradle + `config/detekt/detekt.yml` base. Verifica la sintaxis del .kts
  y del .yml. No cambies dependencias de la app.
- Añade a `.github/workflows/` (workflow NUEVO o un job) que corra `detekt` + `testDebugUnitTest` en cada
  PR como gate. NO toques ni rompas el workflow de deploy a Play existente (`android-release.yml`).

TANDA 4 — KDoc + constantes (bajo riesgo):
- Añade KDoc a las APIs públicas de los archivos clave (VMs/managers principales).
- Saca magic numbers EVIDENTES a `const val` nombradas (sin cambiar el valor; cuidado con el balance).

TANDA 5 — Lo RIESGOSO: PLANEA, NO ejecutes a ciegas. Escribe planes detallados en `README for IAS/`:
- `PLAN_DI_hilt.md`: migración a Hilt (módulos, qué inyectar, pasos, riesgos, orden).
- `PLAN_descomponer_WorldMapViewModel.md`: god-object → managers/use-cases con sub-estado propio
  (CombatManager, RoutingUseCase, CollectiblesManager, CampaignManager…), orden incremental, qué se
  mueve, cómo verificar sin compilador, y qué necesita compilador.
- `PLAN_dedup_routing.md`: de-duplicar la cadena routing (updateDestinationRoute/calculateRouteOnNetwork/
  nearbyRoadNodes/rebuildRoadNodeGrid) CON tests de respaldo primero.
- (Item A — colapsar pantallas/overlays de transporte — ya está planeado en ANALISIS §2.2/§8; cítalo.)
- OPCIONAL: si y SOLO si encuentras UNA extracción del god-object 100% aislada y verificable sin compilar,
  ejecútala como demostración; ante cualquier duda, déjala en el plan.

PROTOCOLO DE DOCS (09 §13): actualiza ANALISIS §9 (marca qué del roadmap quedó HECHO vs en plan), 09 si
añades gotchas, y lo que toque. README raíz bilingüe SOLO si hay algo user-facing (casi nada de esto lo es).

ENTREGABLE FINAL en `CHANGELOG_NOCHE.md`:
- Qué hiciste por tanda, archivo por archivo, con la verificación de cada uno (balance, EOL, refs).
- Qué quedó PENDIENTE y por qué (sobre todo lo riesgoso que dejaste en plan).
- Riesgos para el Rebuild de la mañana: dónde mirar primero si algo no compila.

Trabaja hasta agotar lo SEGURO + dejar los PLANES. Sé conservador, NO rompas el build, documenta todo.
Estilo: conciso y directo.
```

---

## Plan de acción (resumen, por valor × seguridad)

| Tanda | Qué | Riesgo | Se ejecuta de noche |
|---|---|---|---|
| 1 | Normalizar EOL a CRLF + `.editorconfig` | ~Nulo | SÍ, completo |
| 2 | Tests JUnit de lógica pura (+ extraer funciones puras) | Bajo (aislado) | SÍ (escribir; correr si el sandbox deja) |
| 3 | detekt + gate de lint/tests en CI | Bajo (aditivo) | SÍ |
| 4 | KDoc + magic numbers → const | Bajo | SÍ |
| 5 | DI (Hilt), descomponer god-object, de-dup routing, item A | **Alto (necesita compilador)** | NO se ejecuta: se deja en PLANES detallados |

## Por qué lo riesgoso se PLANEA y no se ejecuta a ciegas
DI (Hilt), partir el `WorldMapViewModel` (un solo `_state` + ~25 parciales que son extensiones de la MISMA
clase) y de-duplicar la cadena de routing **necesitan un compilador** para no dejar el build roto toda la
noche. Sin red de tests todavía, ejecutarlos a ciegas es justo lo que rompería `main` al hacer el PR. Por
eso la noche construye PRIMERO la red de seguridad (tests + lint) y deja esos refactors en planes
ejecutables para hacerlos después, paso a paso y con compilador.

## Reglas duras (las repite el prompt, aquí como recordatorio)
- Solo rama de noche, nunca `main`. Solo editar archivos, no `git`. Changelog de todo.
- Verificación Kotlin-aware del balance, EOL por archivo, herramienta Read para colas de archivos grandes.
- Una parte verificable a la vez; conservador ante la duda.

## En la mañana (tú)
1. Lee `CHANGELOG_NOCHE.md`. 2. **Rebuild** en Android Studio. 3. Si algo no compila, el changelog dice
dónde; lo afinamos. 4. Commitea por tanda lo que te sirva; descarta lo que no. 5. Los PLAN_*.md quedan
como guía para los refactors grandes (DI, god-object) cuando los quieras hacer con compilador.
