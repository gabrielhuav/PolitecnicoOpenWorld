# PROMPT DE TRASPASO → GPT 5.6 Sol (Alto) — 1º GIT/COMMIT (producción), 2º AUDIO

> Sesión anterior: Claude Fable 5 (2026-07-18, cambios "18j" + "18k"). **La app se lanza YA a
> producción**: el flujo de release vive en el CI/CD del repo y el dueño te ayudará con el
> primer commit. Este prompt es tu punto de partida completo.

## Contexto del proyecto (LEE PRIMERO)

- Repo: "Politécnico Open World" (POW), juego Android 2D top-down (Kotlin + Compose + MVVM
  estricto por feature). Proyecto Android en `PolitecnicoOpenWorld/PolitecnicoOpenWorld/`.
- Contexto COMPLETO para IAs: `PolitecnicoOpenWorld/README for IAS/` (archivos 00–09).
  Mínimo: `00_INDEX.md`, `09_CONVENTIONS_GOTCHAS.md` (MVVM, CRLF, gotcha miembro-vs-extensión,
  protocolo de docs), `07_OTHER_FEATURES.md` §HUELUM VS. GOYA, `DISENO_ARCADE_SF_POW.md`
  (secciones Fix 18g–18k), `SF_SPECIAL_VOICES_SFX.md` (voces).
- REGLAS OBLIGATORIAS: estado inmutable `_state.update { it.copy(...) }`; Views solo observan;
  comentarios/strings UI en español; strings en `values/` y `values-en/` con PARIDAD ES+EN;
  conservar CRLF en .kt; verificar llaves; gana el MIEMBRO sobre la extensión homónima;
  NO deshacer los cambios 18g–18k; NO borrar `raw_yt/` ni `out_diarized/`; NO re-scrapear YT.

## PASO 1 — GIT + PR a producción (el dueño te ayuda; PRIMERO probar el commit)

Estado: el working tree tiene TODOS los cambios de la sesión 18j+18k **sin commitear**:

- `app/src/main/java/.../features/streetfighter/viewmodel/StreetFighterViewModel.kt` (IA variada,
  showcase completo+v2, auditoría estática, skip, mapas hogar, audio de pasos forzados)
- `app/src/main/java/.../features/streetfighter/viewmodel/StreetFighterState.kt`
  (`showcaseRunning`, `gauntletMapFile`)
- `app/src/main/java/.../features/streetfighter/ui/StreetFighterScreen.kt` (fondo del gauntlet,
  botón "Saltar animación")
- `app/src/main/res/values/strings.xml` + `values-en/strings.xml` (`sf_showcase_skip`)
- Docs: `README.md` (bilingüe), `README for IAS/00_INDEX.md`, `07_OTHER_FEATURES.md`,
  `DISENO_ARCADE_SF_POW.md`, y este prompt.

CI/CD (`.github/workflows/`, ya revisado):

1. **`pr-quality-gate.yml`** — corre al ABRIR/actualizar PR hacia `main`:
   `gradle :app:assembleDebug :app:testDebugUnitTest` (GATE DURO) + **detekt BLOQUEANTE**
   (baseline perdona deuda vieja; el CÓDIGO NUEVO debe pasar limpio — ojo con funciones
   largas/complejidad en lo que agregues).
2. **`android-release.yml`** — al MERGEAR el PR a `main`: APK debug → GitHub Release
   (`debug-latest`) y **AAB firmado → Play Store track `alpha` (prueba cerrada)**. Es decir:
   **aprobar/mergear el PR = deploy a producción**; después solo se espera la revisión de
   Play Store (PS).

Flujo sugerido: rama `feat/sf-ai-showcase-audio` → commit con mensaje claro (qué es 18j/18k;
ver `DISENO_ARCADE_SF_POW.md` §18j/§18k para el resumen) → push → PR a `main` → esperar el
quality gate en verde → el dueño aprueba/mergea. Si detekt falla por código nuevo, corrige el
issue (no toques el baseline sin permiso del dueño).

## PASO 2 — AUDIO (feedback del dueño en dispositivo, 2026-07-18)

Resultado del QA del dueño con el Showcase: **prácticamente todos los assets BIEN**. Solo:

1. **LA LLORONA — poses repetidas (arte, no código).** Sus sonidos están BIEN. El detalle
   visual está localizado con análisis del JSON (`lallorona.json`, 123 frames ✓ — la hoja 12
   ya llegó): son las animaciones de DAÑO, con poses de relleno:
   - `hurtHeadLight`: 4 pasos, solo 2 poses (`hit-face-1`×2 + `hit-face-2`×2)
   - `hurtBodyLight`: 3 pasos, **1 sola pose** (`hit-stomach-1`×3)
   - `hurtBodyMedium`: 4 pasos, solo 2 poses (`hit-stomach-1`×2 + `hit-stomach-2`×2)
   Fix = regenerar los frames `hit-face-*`/`hit-stomach-*` de sus hojas croma (pipeline:
   `GUIA_regeneracion_sprites_croma.md`, validador `tools/validate_sf_chroma_character.py`).
   NOTA: el dueño dijo "ataques", pero el análisis muestra que lo repetido son los HURT
   (que se ven durante los ataques del rival). Verifícalo en el Showcase.
2. **LA PRESIDENTA — voz correcta pero MUY CORTA.** `special_la_presidenta.ogg` ya es su voz
   ("YA ES SU VOZ" — dueño); solo hay que elegir/recortar un clip MÁS LARGO desde
   `tools/sf_voice_scrape/out_diarized/` (fuentes ya en disco; NO re-scrapear YouTube).
   Reempaquetar con el pipeline de `SF_SPECIAL_VOICES_SFX.md`.
3. **TZITZIMIME y YOALLI: perfectos** (sonido y animaciones). No tocar.
4. **PENDIENTE DE ARTE (pedido nuevo): metamorfosis inversa Yoalli → Claudia (La Presidenta).**
   Hoy solo existe Presidenta→Yoalli (BONUS_POWER_11 en `lapresidenta.json`). El dueño quiere
   la animación del regreso/conversión vista desde Yoalli. Requiere frames nuevos (arte);
   cuando existan, cablear como bonus/estado nuevo de YOALLI y añadirla al guion del showcase
   (`showcaseExtraStates`/`forceShowcaseState` en el VM).

Mejora de CÓDIGO opcional (pequeña, útil): en `auditFighterAssets` (VM) añadir detección de
"anims rellenas": si una animación tiene ≥3 pasos y ≤1–2 `src` únicos → reportarla
(habría cazado lo de La Llorona sola). Umbral estricto (`únicos == 1 && pasos ≥ 3`) para no
llenar el reporte de falsos positivos (los terminadores `-1` repiten frame a propósito).

## Cómo probar

Menú de modos → **Showcase** (botones Saltar animación / Detener; cada peleador en su mapa
hogar de día; reporte final en pantalla + .txt en `Android/data/ovh.gabrielhuav.pow/files/`).
IA vs IA para la pelea variada. Al terminar cambios: protocolo de docs del 09 (07 §HUELUM +
DISENO + README bilingüe EN+ES).
