# TRASPASO · Sesión nueva (2026-07-22) — cerrar recortes de La Llorona y pendientes

> Estado del repo al entregar: rama `fix-audio-add-newFightAssets`, **árbol LIMPIO**
> (0 archivos sucios), último commit `d33f680d "QA Changes 5/9"`. Todo lo descrito como
> "hecho" ya está commiteado. Compila, tests en verde, detekt 0 smells.

---

## 0. LEE ESTO ANTES DE TOCAR NADA (error cometido en la sesión anterior)

Al arreglar unos recortes malos, la sesión anterior **re-recortó las 520 hojas de los 18
peleadores**. Eso metió una REGRESIÓN: el atlas de La Llorona acabó con cuadros que
contenían **4 figuras apretadas en una celda de 256 px** en vez de una. Se revirtió con
`git checkout` (por eso el árbol está limpio), pero la lección es:

> **NUNCA re-recortes hojas que ya estaban bien.** Recorta SOLO la hoja concreta que falla,
> revisa el resultado con la hoja de contacto, y solo entonces empaqueta.

La Llorona es el personaje que **siempre se recorta mal** (su arte tiene efectos que el
croma separa del cuerpo, y varias de sus hojas llegaron como JPG con croma sucio). Trátala
como caso especial, con verificación visual obligatoria cuadro a cuadro.

---

## 1. LO QUE FALTA POR ARREGLAR (auditado por el dueño, solo La Llorona)

De `tools/_audit_sheets/lallorona_TODO.png`, el dueño confirmó que **todo lo demás está
bien**. Solo estos están mal:

| Animación | Cuadros malos | Hoja fuente |
|---|---|---|
| `superArt` | `super-4`, `super-5`, `super-6` | `newSFAssets/LaLlorona/LaLlorona_29_SuperArt_DanoAgachado.png` |
| `hurtHeadLight` | sobre todo `hit-face-1` | `newSFAssets/LaLlorona/LaLlorona_09_HeavyKick_HurtHead.png` |
| `hurtHeadMedium` | sobre todo `hit-face-1` | idem hoja 09 |
| `crouchTurn` | **orientación invertida** (no es recorte, es espejo) | `newSFAssets/LaLlorona/LaLlorona_02_Crouch_Turn.png` |

**El dueño se ofrece a regenerar assets**: decide si conviene pedirle la hoja 09 y/o la 29
de La Llorona regeneradas (en **PNG con croma limpio**, nunca JPG) antes de pelear con el
slicer. Pregúntale ANTES de invertir mucho en el recorte automático.

### Sobre `crouchTurn`
No es un problema de recorte sino de **orientación**. El slicer ya tiene
`detect_orientation_flips` + `_frame_meta.json` (`flipX`) pero **solo se aplica a la
secuencia KO**. Para `crouchTurn` habría que:
- o extender esa detección a los giros,
- o marcar a mano los cuadros en `GEN/lallorona/_frame_meta.json` con `{"flipX": true}`
  (el packer ya lee ese archivo y la View lo respeta vía `SfFrameDef.flipX`).

---

## 2. CÓMO RECORTAR **SOLO** LO QUE FALLA (flujo correcto)

```bash
cd PolitecnicoOpenWorld
GEN="../newSFAssets/GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio"

# 1) Ver qué detecta ANTES de escribir nada (dry-run, no toca archivos)
python tools/slice_sf_chroma_sheets.py \
  "../newSFAssets/LaLlorona/LaLlorona_29_SuperArt_DanoAgachado.png" lallorona \
  --gen "$GEN" --sheet-num 29 --list

# 2) Si los blobs cuadran, recortar SOLO esa hoja
python tools/slice_sf_chroma_sheets.py \
  "../newSFAssets/LaLlorona/LaLlorona_29_SuperArt_DanoAgachado.png" lallorona \
  --gen "$GEN" --sheet-num 29

# 3) ⚠️ La hoja 12 de La Llorona NO usa el formato estándar (rejilla 4x3, fila 3 = efectos).
#    Si tocas la 12, hay que re-aplicar SIEMPRE este fix o su especial "se lanza a sí misma":
python tools/fix_llorona_projectile.py --gen "$GEN"

# 4) Empaquetar y MIRAR el resultado antes de dar nada por bueno
python tools/pack_sf_character.py lallorona LaLlorona --gen "$GEN"
python tools/sf_audit_sheets.py --only lallorona
# -> abre tools/_audit_sheets/lallorona_TODO.png y compara cuadro a cuadro
```

**Verificación obligatoria antes de cerrar:** `git diff --stat` debe mostrar SOLO
`LaLlorona.webp` y `lallorona.json`. Si aparece cualquier otro personaje, algo se re-recortó
de más → revertir con `git checkout -- <ruta>`.

---

## 3. ESTADO DEL SLICER (qué se cambió y por qué)

`tools/slice_sf_chroma_sheets.py` tiene dos piezas nuevas de la sesión anterior. Funcionan
bien para la mayoría, pero **son la causa probable de la regresión de La Llorona**:

- **`merge_fragments(grp, n_expected)`** — fusiona trozos de una misma pose. Criterio:
  solapamiento >30 % del más estrecho, **o** vecino anormalmente estrecho (<60 % del ancho
  típico) que toca al anterior. (Fusionar por simple tangencia encadenaba filas enteras.)
- **`maybe_split`** reescrito — calcula cuántas poses caben (`ancho / ancho_esperado`) y
  corta en **k tramos uniformes afinados al valle más cercano**, en vez de partir de dos en
  dos por el mínimo de densidad. Los blobs fusionados (`bid=None`) quedan exentos.

**Si La Llorona sigue saliendo mal**, lo más probable es que `merge_fragments` esté
fusionando poses vecinas suyas (su vestido/pelo se solapan entre cuadros). Prueba a
desactivar la fusión solo para ella antes de tocar los umbrales globales — cualquier cambio
en esos umbrales afecta a los 17 personajes que YA están bien.

---

## 4. LO QUE YA ESTÁ HECHO (no rehacer)

Todo commiteado y verificado (compila, tests, detekt 0 smells, strings ES+EN con paridad):

- **Moveset 3rd Strike (21 estados)**: dash/backdash, bloqueo alto-bajo, parry, golpes
  agachado, antiaéreo, barrida, aéreos, patada larga, overhead, agarre→lanzamiento, burla,
  Super Art con medidor, daño agachado, ser lanzado, levantarse. + `RUN` (dash-run),
  `IDLE_RELAXED`, `TALK`. Todos con guarda `hasAnim`.
- **Combos data-driven**: `assets/STREETFIGHTER/DATA/combos.json` (21 básicos + 12
  universales + 18 firmas) → `SfCombos.kt`. Los usan la IA (`queueCombo`) y el tutorial.
- **FATALITY 18/18**: secuencia compuesta con arte existente (`fatality_animation` en el
  packer). Comando: **súper EN CARRERA** con medidor lleno. Daño 70, derriba, y el atacante
  **cruza al otro lado**. En IA y tutorial.
- **Tutorial interactivo**: menú SF → "COMBOS Y TUTORIAL". Lecciones validadas contra el
  estado real, chips del color del botón, cartel "ESO NO ERA" con lo hecho vs lo pedido,
  muñeco inerte (sin reloj, no pierde vida).
- **Arcade Difícil** ya da recompensa: coleccionable del rival → Coleccionables → pestaña
  **PELEADORES**, con la fuente del SF (`SfBitmapText`) y "Ver Historia" → "Próximamente".
- **Multiplayer auditado**: corregido que el daño de fatality/súper/agarre no viajaba
  (`damageForAttack`) y añadida la sincronía de `superMeter` (`meter` en `SfNetMsg` + las 3
  implementaciones del transporte).
- **Gama baja**: atlas submuestreados a ½ (`sheetFor(..., sampleSize)` + `sheetScale`),
  73 → 18 MB de RAM por peleador. Atlas a **WebP lossless**: IMAGES 102 → 88.6 MB.
- **Guardado del arcade**: checkpoint entre peleas, dificultad base guardada sin inferencia.
- **Menú principal**: etiquetas ALPHA/BETA ya no saltan de renglón (falta confirmarlo en un
  S24 real).

---

## 5. PENDIENTES ADEMÁS DE LA LLORONA

1. **`super-7` sale como efecto puro** en ESCOMBOY, Policía CDMX Hombre y Rey Grupero.
   Sospecha: el artista dibujó ese cuadro **sin personaje** (mismo caso que los
   `bonusPower` de La Presidenta). **Verificar abriendo la hoja 29 fuente**; si es así, NO
   es un fallo de recorte → declararlos como cuadro de efecto (igual que
   `BONUS_PROJECTILE_POWERS` en `pack_sf_character.py`) o pedir la hoja regenerada.
2. **Bonus powers sin recortar**: La Tzitzimime tiene 5 implementados con 3 hojas grok
   (hasta 9) y Yoalli 10 con 5 hojas. Requiere inspeccionar el layout de filas de cada hoja
   a mano (`tools/slice_sf_bonus_powers.py`).
3. **Frases de voz**: `assets/STREETFIGHTER/DATA/voice_phrases.json` tiene 69 clips con
   transcripción borrador de Whisper. Curar a oído (mp3 en `tools/_audio_review/`) y luego
   poner `voiceSubtitlesEnabled = true` en el VM.
4. **Audios escom**: los 4 `special_escom*` son gritos sin habla; `special_escomboy_attack`
   dura 10.7 s. Falta el oído del dueño para decidir recorte/reasignación.
5. **Mapas UAM AZC/Cuajimalpa**: bloqueados esperando videos nuevos del dueño.
6. **Probar en dispositivo** todo el moveset, el fatality y el tutorial.

---

## 6. HERRAMIENTAS ÚTILES

| Herramienta | Para qué |
|---|---|
| `tools/sf_audit_sheets.py [--only <char>]` | Hojas de auditoría: TODAS las animaciones por personaje + `_FATALITIES.png` + `_RESUMEN.png` |
| `tools/sf_contact_sheet.py <char> [first\|mid]` | Hoja de contacto rápida (1 cuadro por animación) |
| `tools/slice_sf_chroma_sheets.py ... --list` | **Dry-run**: muestra los blobs detectados sin escribir |
| `tools/fix_llorona_projectile.py --gen <GEN>` | Re-extrae los 5 `proj-*` de La Llorona por silueta |
| `tools/atlas_to_webp.py [--dry-run]` | PNG → WebP lossless con verificación de píxeles visibles |
| `tools/sf_audio_audit.py` | Audit de voces (mapeo real, duración, pitch, Whisper) |

Flujo completo de assets: **`README for IAS/FLUJO_ASSETS_SF.md`**.
Estado y decisiones del modo: **`README for IAS/DISENO_ARCADE_SF_POW.md`** (§2026-07-21a…e).
