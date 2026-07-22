# PROMPT · Fable 5 — motor SF pendiente (verificado contra el código 2026-07-22)

> **Este es tu plan de trabajo; síguelo de arriba abajo.** Los números de línea (verificados el 2026-07-22) son
> un **punto de partida para no explorar a ciegas** un archivo de 5 633 líneas — **no son dogma**:
> se desplazan en cuanto editas (sobre todo en el VM, donde una edición corre todas las citas
> posteriores). Por eso, antes de cada cambio, **localiza por el nombre del símbolo** (función,
> `val`, key) que se indica y lee el contexto local; el número solo te lleva al vecindario.
>
> Haz los bloques en el orden dado; dentro de un bloque respeta los sub-pasos (p.ej. 1.2 va
> **después** de 1.1). Sí necesitarás leer/entender código en el Bloque 0 (tests) y tocar
> `tools/` en los Bloques 2 y 3 (pipeline de arte) — eso es esperado, no lo evites; lo que se
> pide es **no re-explorar lo ya mapeado aquí**.
>
> ⚠️ Los Bloques 2 y 3 NO se pueden hacer "solo con lógica de motor": las variantes que hay que
> elegir **no están empacadas todavía** (comprobado contra el atlas y el packer). Van reescritos
> con la verdad y marcados como **trabajo futuro deferible**. Lo que SÍ tienes que garantizar:
> **el juego compila y corre sin ellos**.

## ⚡ Reglas para no quemar créditos

1. **NO leas `StreetFighterViewModel.kt` entero (5 633 líneas).** Solo los rangos que se indican.
2. **NO re-audites los assets.** Cerrados por el dueño (`VACIO 0 · MULTI_FIGURA 0 · ESCALA 0 · BORDE 1`).
3. **NO toques `tools/` ni re-empaques atlas** — **EXCEPCIÓN:** los Bloques 2 y 3, SOLO si el
   dueño decide importar ese arte ahora, y **solo para La Presidenta**. Si no, se quedan como
   trabajo futuro (ver abajo) y no tocas nada de `tools/`.
4. Verifica con **un solo** `gradlew` al final de cada bloque, no tras cada edición.

## Rutas

| Qué | Dónde |
|---|---|
| Repo raíz | `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld` |
| Proyecto (aquí está `gradlew.bat`) | `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| ViewModel (VM) | `app\src\main\java\ovh\gabrielhuav\pow\features\streetfighter\viewmodel\StreetFighterViewModel.kt` |
| Screen (SCR) | `app\src\main\java\ovh\gabrielhuav\pow\features\streetfighter\ui\StreetFighterScreen.kt` |
| Modelos (MDL) | `app\src\main\java\ovh\gabrielhuav\pow\domain\models\streetfighter\SfModels.kt` |
| Catálogo de frases | `app\src\main\assets\STREETFIGHTER\DATA\voice_phrases.json` (UTF-8, **CRLF**) |
| Atlas manifiesto La Presidenta | `app\src\main\assets\STREETFIGHTER\DATA\lapresidenta.json` |
| Packer | `tools\pack_sf_character.py` |
| Importador de poses | `tools\sf_import_fixed_pose.py` |
| Fuente cruda sin importar (V2) | `tools\_para_corregir\ORIGINALES_lapresidenta_bonus\` |
| Poses metamorfosis (sin importar) | `tools\_para_corregir\metamorfosis_yoalli_a_presidenta\` · `tools\_para_corregir\metamorfosis_HACIA_presidenta_seleccion\` |
| Spec metamorfosis | `README for IAS\SF\SPEC_metamorfosis_lapresidenta.md` |
| Plan de refactor | `README for IAS\SF\PLAN_refactor_motor_compartido.md` |
| Estado de sesión (actualízalo al final) | `README for IAS\_SESION_ACTUAL.md` |

Rama `fix-audio-add-newFightAssets`. Convenciones: `README for IAS\09_CONVENTIONS_GOTCHAS.md`
(MVVM, estado inmutable `_state.update { it.copy(...) }`, strings ES+EN con paridad, **CRLF en `.kt`**).

---

## BLOQUE 1 · Subtítulos (rápido, alto impacto — sin bloqueo de assets, a diferencia de 2 y 3)

Hay **64 subtítulos `es` curados** en `voice_phrases.json` que hoy **no se ven**. El propio
README del archivo dice: *"Al terminar de curar: poner `voiceSubtitlesEnabled = true`."* Ya se
curó. Faltan tres cosas.

### 1.1 · El delimitador `|` se pierde

29 clips usan `|` para partir el subtítulo en varias líneas sincronizadas con la voz.

- **VM:319** `sfHudSanitize` hace `.replace(Regex("[^A-Z0-9 ]"), " ")` → convierte `|` en espacio.
- **SCR:2907** `sub.split(' ')` parte por espacios, no por `|`.

**Qué hacer:** partir por `|` **antes** de sanitizar, sanitizar cada tramo por separado, y
dibujar **una línea por tramo**. El estado lo lleva `specialSubtitleHud` (**VM:337**, se dibuja
en **SCR:2897**). Ojo: en SCR **2903-2918 ya existe un word-wrap a ~24 chars/línea (máx 3)** —
combínalo: cada tramo `|` empieza línea nueva y, si es largo, se sigue envolviendo. No borres el
word-wrap, encádenalo.

### 1.2 · Encender el flag

**VM:330** `private val voiceSubtitlesEnabled = false` → **`true`**, DESPUÉS de 1.1 (si no, se
activa perdiendo el formato multilínea).

⚠️ **Ignora dos fuentes desactualizadas** que dicen que los subtítulos no están listos: el
comentario **VM:325-329** (del 07-19, previo a la curación) y `README for IAS\SF\AUDIT_VOCES_
SUBTITULOS.md` (lee la fuente vieja inline, dice "7 curados"). La verdad vigente es
`voice_phrases.json` (64 `es` curados). Puedes actualizar/borrar el comentario VM:325-329.

### 1.3 · Completar el track EN (para que la paridad sea real, no solo de conteo)

La "paridad 64/64" es **solo por conteo**: **32 de los campos `en` siguen en español** (se
copiaron del `es`). Un jugador en inglés vería español.

**Qué hacer en `voice_phrases.json`:** traducir al inglés los `en` que aún estén en español.
- Un `en` está sin traducir si contiene `¿ ¡ ñ` o acentos, o es idéntico a un `es` con palabras
  españolas. Ejemplos: `special_papz1_hurt_2`, `special_pol_h_win`, `special_llorona_attack`.
- **NO traduzcas gritos/onomatopeyas** (`Grrr`, `Kiaa`, `Aaaah`, `Jajaja`): déjalos igual.
- **Conserva la estructura de tramos `|`** y el **CRLF**. **No toques los `es`.** No toques `draft`.

---

## BLOQUE 2 · Azar de poderes (La Presidenta) — ⚠️ BLOQUEADO POR PIPELINE DE ARTE

NO es cierto que "los cuadros ya están empacados y solo falta que el motor elija". Se verificó
contra `DATA\lapresidenta.json` (el manifiesto del atlas) y contra `tools\pack_sf_character.py`:

- El atlas tiene **una sola** variante de cada cosa: `fatality-1..4` (mazo + haz tricolor) y
  `fx-energy-1`. **No existe `fatality2-*` ni `fx-energy-2` ni `fat_*`.**
- El packer fija a mano `fatality_keys = fatality-1..8` y `fx_keys = ("fx-energy-1",)`
  (`pack_sf_character.py:548-552`). No emite ninguna V2.
- Las segundas variantes existen **solo como fuente cruda en chroma verde, sin recortar**, en
  `tools\_para_corregir\ORIGINALES_lapresidenta_bonus\` (`suoerFatV2_*` = libro + dinero;
  `super_fat_*` = la V1; `*_power_energy` = destellos). El directorio `GEN/` ni está en el repo.

Por tanto cada azar (fatality V2, elemento `fat_*`, proyectil V2) exige **pipeline de arte**:
1. Chroma-key + recorte + importar a `GEN\lapresidenta\` (con `tools\sf_import_fixed_pose.py`).
2. Extender el packer: añadir las keys V2 a `fatality_keys`/`fx_keys` y emitir una animación
   `fatalityV2` junto a `fatality`.
3. Re-empacar **solo** La Presidenta.
4. **Recién entonces** el motor puede elegir al azar.

### 🎯 Lo que SÍ debes hacer ahora (obligatorio) — que funcione sin el arte V2

El motor **ya degrada bien**: `tryBonusPower` no entra a un poder cuyo `animations[jsKey]` esté
vacío (**VM:2082**), y `onBonusPowerPressed` los salta. **Respeta ese guard (VM:2075).** Si
escribes la lógica de azar, **déjala guardada**: si `fatalityV2`/`fx-energy-2` no existen en las
animaciones, cae a la única variante que hay. Así el juego **compila y corre igual** aunque el
arte V2 nunca se importe.

### 📋 Trabajo futuro (déjalo anotado en `_SESION_ACTUAL.md`, NO lo fuerces)

Si el dueño no importa el arte en esta sesión, los tres azares quedan **pendientes** con la nota
"requiere importar `suoerFatV2_*` / `fat_*` / destellos extra + extender packer + re-empacar".

---

## BLOQUE 3 · Metamorfosis (2 secuencias nuevas) — ⚠️ MISMO BLOQUEO DE ARTE

**Spec del dueño: `README for IAS\SF\SPEC_metamorfosis_lapresidenta.md`** (léela: trae orden y poses).

- **Larga (9 poses)** `metamorfosis_yoalli_a_presidenta\_01..09`, Yoalli ➜ Presidenta: al
  **iniciar la última pelea del arcade**, tras derrotar a Yoalli. Secuencia `_01`…`_09`, sin azar.
- **Corta (6 poses → 3 pasos)** `metamorfosis_HACIA_presidenta_seleccion\_01..06`, ➜ Presidenta:
  al seleccionarla **fuera de arcade**. `random(_01.._04)` → `_05` → `_06`.

Las poses están recortadas y numeradas en `tools\_para_corregir\metamorfosis_*\`, **pero AÚN NO
importadas al atlas** (conservan el rótulo "STEP N"; el importador `sf_import_fixed_pose.py` lo
borra). → Mismo pipeline que el Bloque 2: importar + re-empacar antes de que el motor las use. Si
no se importa el arte ahora, la **lógica de disparo** queda como trabajo futuro; el juego debe
**compilar y correr sin ella**.

⚠️ **No las confundas con las metamorfosis que YA existen** (van en sentido CONTRARIO y a mitad
de combate):
- **VM:1705** `completePresidentaMetamorphosis` (BONUS_POWER_11): Presidenta ➜ Yoalli al 50 % HP.
- **VM:1711** `completeYoalliMetamorphosis` (BONUS_POWER_10): Yoalli ➜ Presidenta en reversa.

Las dos NUEVAS se disparan **al EMPEZAR** (inicio de última pelea / selección), no durante.

---

## BLOQUE 4 · Tutorial

`assets/STREETFIGHTER/DATA/combos.json` ya tiene el catálogo, y el tutorial ya existe. El
currículo por peleador (`SfCombos.curriculum`) es `basics` (**21**) + `universal` (**12**) +
la `signature` del peleador (**1**) ≈ **34 lecciones**, leídas de `combos.json`. **Falta:**

- Lección del **FATALITY**: cómo se saca (súper **EN CARRERA** con el medidor lleno).
- Lecciones de los movimientos nuevos (hojas 20-29) que no tengan.
- Explicar el **medidor de súper**, que YA existe: `SfFighter.superMeter` + `superReady`
  (**`SfModels.kt:511-523`**, paquete `domain\models\streetfighter`) y ya se dibuja
  (**SCR:2858**). **NO crees UI nueva.**

---

## ⚠️ BLOQUE 0 — léelo antes de tocar el motor

`StreetFighterViewModel.kt`: **5 633 líneas, decenas de funciones públicas, varias banderas de
modo consultadas por todo el archivo.** Los 6 modos (Arcade, VS, IA vs IA, Showcase, Tutorial,
Multiplayer) comparten ese archivo. **El ViewModel no tiene tests propios** — el único test de SF
(`app\src\test\java\...\domain\models\streetfighter\SfArcadeCampaignAuditTest.kt`) audita la
campaña arcade (datos), NO el motor. Ahí puedes poner tus tests de caracterización.

La última regresión grande salió de tocar algo compartido sin red de seguridad.
Plan por fases: `README for IAS\SF\PLAN_refactor_motor_compartido.md`.
**Recomendación fuerte:** antes de tocar el motor, escribe los tests de caracterización de la
Fase 1 (física de un tick, `changeState`, `damageForAttack`, poderes bonus). Son **riesgo cero**
(pura adición) y son lo único que detecta si rompes uno de los 6 modos. Si te los saltas,
**prueba a mano los 6 modos** tras cada bloque.

---

## Verificación

```
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

```
..\detekt-cli-1.23.8\bin\detekt-cli.bat --config "config\detekt\detekt.yml" --input "app\src\main\java"
```

⚠️ **NO** uses `--build-upon-default-config`. El baseline real son **5 smells preexistentes**
(`CachingWebViewClient`, `NpcAiManager`, `RoadRouter`, `CatSpriteManager` ×2). Varios docs dicen
"0 smells" y es falso.

`git status` debe mostrar **solo** lo que tocaste. Si aparece un peleador que no estabas tocando,
algo se re-empaquetó de más → `git checkout -- <ruta>`. (En esta sesión solo debería cambiar
La Presidenta, y solo si se hizo el pipeline de los Bloques 2/3.)

**Actualiza `README for IAS\_SESION_ACTUAL.md` antes de terminar**, aunque te quedes a medias —
incluyendo qué quedó como TRABAJO FUTURO (Bloques 2/3 si no se importó el arte). Es lo único que
garantiza que la siguiente sesión continúe en vez de alucinar.

## Última regla

**Verifica los resúmenes ajenos antes de repetirlos.** En este proyecto varios informes de "está
todo al 100 %" resultaron falsos al medirlos. **Mide.**
