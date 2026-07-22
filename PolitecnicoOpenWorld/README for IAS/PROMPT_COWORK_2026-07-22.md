# 🤝 TRASPASO A COWORK — PolitécnicoOpenWorld (2026-07-22)

> Pega este archivo COMPLETO al arrancar la sesión nueva. Trabaja **LOCALMENTE** en
> Windows/PowerShell, con acceso de escritura a git. Nada de nube ni contenedores.

## 0 · Rutas (⚠️ CAMBIARON DE PC)

| Qué | Dónde |
|---|---|
| **Repo raíz** | `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld` |
| **Proyecto Android** (aquí está `gradlew.bat` y `tools\`) | `...\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| **Documentación / contexto** | `...\PolitecnicoOpenWorld\README for IAS` |
| Hojas fuente de sprites | `<raíz>\newSFAssets\` |
| Audios que lee el juego | `<proyecto>\app\src\main\assets\STREETFIGHTER\SOUNDS\` |
| MP3 para escuchar | `<proyecto>\tools\_audio_review\` ⚠️ en `.gitignore` |

**Rama:** `fix-audio-add-newFightAssets` · **Último commit:** `bc610291` "QA Changes 7/9"

⚠️ Las rutas de los docs y los scripts que digan `Documents\GitHub Desktop\...` son de la PC
anterior. Los scripts de `tools/` calculan sus rutas solas (`os.path.dirname(__file__)`), así
que funcionan sin tocar nada; solo hay que ajustar las que se escriban a mano.

## 1 · Empieza por aquí, en este orden

1. **`README for IAS\_SESION_ACTUAL.md`** — estado vivo y a quién delegar cada cosa.
2. **`README for IAS\09_CONVENTIONS_GOTCHAS.md`** — convenciones OBLIGATORIAS: MVVM, estado
   inmutable `_state.update { it.copy(...) }`, strings ES+EN con paridad, **CRLF en `.kt`**.
3. El índice del área que toques: `README for IAS\SF\00_SF_INDEX.md` (peleas) o
   `README for IAS\MUNDO\` (mundo abierto).

## 2 · Dónde está el proyecto ahora mismo

### ✅ ASSETS — cerrados y auditados por el dueño

Audit automático: `VACIO 0 · MULTI_FIGURA 0 · ESCALA 0 · BORDE 1`. El único BORDE es
`lapresidenta/fatality-4` y **no es un fallo**: el haz del súper es más ancho que los 256 px
del lienzo.

Para re-auditar en cualquier momento:
```
python tools/sf_audit_frames_auto.py --sheets    # señala celdas sospechosas
python tools/sf_audit_sheets.py                  # hojas completas por peleador
python tools/sf_audit_hitboxes.py                # cabeza y cuerpo marcados
```

### ✅ AUDIO — 71 clips normalizados, 64 subtítulos ES+EN

Pero **hay 2 huecos de integración**. Sin ellos, el trabajo de subtítulos NO se ve en juego:

**a) El motor no entiende el delimitador `|`.**
29 clips llevan `|` para partir el subtítulo en varias líneas, pero
`StreetFighterScreen.kt:2907` hace `sub.split(' ')` (por espacios), y antes `sfHudSanitize`
convierte el `|` en un espacio con `.replace(Regex("[^A-Z0-9 ]"), " ")`. Resultado: no se
rompe nada visualmente, pero **el salto de línea se pierde** y sale una tira continua.
→ Partir por `|` ANTES de sanitizar y dibujar una línea por tramo.

**b) Los subtítulos siguen apagados.**
`StreetFighterViewModel.kt:330` → `voiceSubtitlesEnabled = false`.
La condición para encenderlo era paridad ES+EN, y **ya se cumple (64/64)**. Encenderlo
DESPUÉS de arreglar (a), o se activa perdiendo el formato multilínea.

**Dos clips NO pueden normalizarse a −16 LUFS** sin comprimir, y está medido:
`special_charro_attack_2` necesita +2.7 dB y tiene **0.0 dB** de margen sobre el techo de
−1.5 dBTP; `special_rey_grupero` necesita +2.4 dB y caben +1.3. Comprimir cambia el carácter
del grito: **es decisión del dueño**, no algo que se aplique por defecto.

### ❌ MOTOR — lo que falta

| Tarea | Detalle |
|---|---|
| Elemento aleatorio | El poder `fat_*` de La Presidenta elige elemento al lanzar (fuego/aire/tierra/ultimate) |
| Fatality aleatoria | Entre V1 (mazo + haz tricolor) y V2 (libro + dinero). Los cuadros de V2 ya están importados como `fatality2-1..4` |
| Proyectil aleatorio | Entre 3 variantes de energía (`fx-energy-1`, `fx-energy-2`, y una tercera sin importar) |
| Metamorfosis | 2 secuencias con disparadores nuevos. **Spec completa en `SF\SPEC_metamorfosis_lapresidenta.md`** |
| Tutorial | Lecciones para el fatality y los movimientos nuevos (hojas 20-29) |
| Audios nuevos | Las poses nuevas suenan con grito genérico. Lista por prioridad en `SF\PROMPT_traspaso_audio_subtitulos.md` |

## 3 · ⚠️ Antes de tocar el motor, lee esto

`StreetFighterViewModel.kt` tiene **5 271 líneas**, **65 funciones públicas**, **7 banderas de
modo consultadas 104 veces** y **CERO tests**. Los 6 modos (Arcade, VS, IA vs IA, Showcase,
Tutorial, Multiplayer) comparten ese archivo.

**Meter features ahí sin red de seguridad es cómo se metió la última regresión grande** de
este proyecto (una sesión re-recortó las 520 hojas "por si acaso" y hubo que revertir).

Plan por fases medido y escrito: **`SF\PLAN_refactor_motor_compartido.md`**.
La Fase 1 son tests de caracterización, **riesgo cero** porque es pura adición. Hacerla antes
de las features nuevas ahorra el doble de tiempo después.

## 4 · A quién delegar

| Dificultad | IA | Cuándo |
|---|---|---|
| **Alta** | **Sol 5.6** · **Fable 5** | Refactor del motor, features que tocan los 6 modos, cambios en el slicer o el packer (afectan a los 18 peleadores a la vez) |
| **Media** | **Opus 4.8** | Features acotadas, auditorías, herramientas de `tools/`, un bug localizado, documentación técnica |
| **Baja** | **Gemini 3.6** | Pipelines que YA existen, recortar/normalizar audio con instrucciones exactas, aplicar un CSV, tareas repetitivas |

Al delegar, escribe siempre: rutas absolutas, el comando exacto, cómo se verifica que salió
bien, y **qué NO debe tocar**.

## 5 · Verificación antes de cerrar CUALQUIER sesión

```
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

```
..\detekt-cli-1.23.8\bin\detekt-cli.bat --config "config\detekt\detekt.yml" --input "app\src\main\java"
```

⚠️ **NO** uses `--build-upon-default-config` en detekt: sube el conteo a 16 porque añade
reglas que el repo no adoptó. El baseline real son **5 smells preexistentes**
(`CachingWebViewClient`, `NpcAiManager`, `RoadRouter`, `CatSpriteManager` ×2). Varios docs
afirman "0 smells" y **es falso**.

`git status` debe mostrar **solo** lo que tocaste a propósito. Si aparece un peleador que no
estabas tocando, algo se re-recortó de más → `git checkout -- <ruta>`.

**Y actualiza `_SESION_ACTUAL.md` antes de terminar.** Es lo único que garantiza que la
siguiente IA continúe en vez de alucinar.

## 6 · Las trampas que ya han costado caro

1. **Nunca re-recortes hojas que ya están bien.** Solo la que falla, verifica con `--list` y
   con la hoja de contacto, y **solo entonces** empaqueta.
2. **La hoja 12 es especial** (rejilla 4×3, fila 3 = efectos del proyectil). Si la tocas, hay
   que re-aplicar `tools/fix_llorona_projectile.py`.
3. **`SHEETS` en `slice_sf_chroma_sheets.py` lo comparten los 18 peleadores.** Si una hoja
   concreta trae otra cantidad de poses, va en `SHEET_OVERRIDES` por `(personaje, hoja)`.
4. **`sf_audio_audit.py` SOLO se ejecuta con el Python del venv:**
   `..\.codex-tmp\pow-audio-venv\Scripts\python.exe`. Con el del sistema, que no tiene
   `faster-whisper`, la columna de transcripción sale vacía y el siguiente
   `build_voice_phrases_catalog.py` **borra los drafts**.
5. **Verifica los resúmenes ajenos antes de repetirlos.** En este proyecto varios informes de
   "está todo al 100 %" resultaron falsos al medirlos. Mide.
