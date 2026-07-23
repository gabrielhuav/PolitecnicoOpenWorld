# PROMPT · Gemini 3.6 (PC de ESCRITORIO) — asistir las correcciones HUMANAS de audio/sprites

> ⚠️ **RUTAS.** Este prompt se escribió en la LAPTOP (raíz del repo:
> `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld\PolitecnicoOpenWorld`). En el
> ESCRITORIO la raíz es **distinta**; llámala `<RAÍZ>`. **Todas las rutas de abajo son RELATIVAS a
> la raíz del repo**, así que úsalas tal cual (ya corres dentro del proyecto). No hace falta ninguna
> ruta absoluta.
>
> **Tu rol:** eres el ASISTENTE MECÁNICO. Las decisiones que necesitan OÍR el audio o RECORTAR
> sprites a mano las hace el DUEÑO; tú (1) acotas/auditas para que él no busque a ciegas, y (2)
> ejecutas los cambios de repo (renombrar/copiar archivos, editar `voice_phrases.json`, recortar
> audio con segundos exactos, re-importar y re-empacar sprites). **No inventes contenido de audio.**

## Contexto verificado (2026-07-22, en la laptop)

`.ogg` presentes en `app/src/main/assets/STREETFIGHTER/SOUNDS/`:
- Paparazzi: `special_paparazzi_5.ogg`, `special_paparazzi_5_attack.ogg`, `special_paparazzi_5_hurt.ogg`, `special_papz1_hurt_1..3.ogg`
- Señor tienda: `special_senor_tienda.ogg`, `special_senor_tienda_attack_1.ogg`, `special_senor_tienda_attack_2.ogg`, `special_senor_tienda_hurt_1.ogg`, `special_senor_tienda_hurt_2.ogg`, `special_senor_tienda_win.ogg`
- Tzitzimime: `special_la_tzitzimime_attack.ogg`, `special_la_tzitzimime_hurt.ogg`, `special_la_tzitzimime_power.ogg`, `special_la_tzitzimime_win.ogg`

⚠️ Los `.mp3` de `tools/_audio_review/` de estos clips **ya no están**: el dueño escucha los `.ogg`
directo. Los subtítulos están en `app/src/main/assets/STREETFIGHTER/DATA/voice_phrases.json` (clave
`clips`, UTF-8, **CRLF**; NO tocar los `es` ya curados salvo lo que aquí se indique).

---

## 1 · Paparazzi 5 tiene un audio que en realidad es de Paparazzi 1

**Pista fuerte (verificar OYENDO):** el sospechoso #1 es **`special_paparazzi_5.ogg`**. Su frase
curada es un reclamo largo (`"¿Pero para qué o qué? | Pus dime de qué se trata | ¿Eh?..."`), del
mismo tono que los `special_papz1_hurt_*` de Paparazzi 1 — no un ataque/hurt de Paparazzi 5. El
dueño confirma cuál es el intruso escuchando los tres de Paparazzi 5 (`special_paparazzi_5`,
`_attack`, `_hurt`).

**Cuando el dueño confirme qué clip está mal y con qué debe quedar,** ejecuta el cambio en los
**tres lados** (deben quedar consistentes):
1. **ogg** — `app/src/main/assets/STREETFIGHTER/SOUNDS/`: reemplazar/renombrar el `.ogg` erróneo por
   el correcto (el dueño te dará el archivo bueno o el nombre destino).
2. **mp3** — `tools/_audio_review/`: si el dueño repone el mp3 de referencia, colócalo con el mismo
   nombre base.
3. **subtítulo** — `voice_phrases.json`: mover/corregir la entrada del clip para que el `es`/`en`
   correspondan al audio correcto (y borrar la del clip que desaparezca).

Verifica al final: `python -c "import json; json.load(open('app/src/main/assets/STREETFIGHTER/DATA/voice_phrases.json',encoding='utf-8'))"` (JSON válido) y `git status` limpio salvo lo tocado.

---

## 2 · Señor de la tienda: un clip donde se cuela la voz de Prankedy

El dueño escucha los 6 `special_senor_tienda*` e identifica en cuál aparece brevemente la voz de
Prankedy. **Tú recortas** ese fragmento cuando el dueño te dé los **segundos exactos** (inicio→fin a
conservar). Usa el mismo pipeline de audio del repo (revisa `tools/` — p.ej. `sf_normalize_voice.py`,
`build_sf_audio_v3.py`; si hay que cortar crudo, `ffmpeg -i <in> -ss <ini> -to <fin> -c copy <out>`
manteniendo formato ogg/vorbis y el nivel −16 LUFS del resto). Reescribe el `.ogg` in situ. Si al
recortar cambia el texto que se oye, ajusta su entrada en `voice_phrases.json`.

---

## 3 · La Tzitzimime: mal recortada (sprites) + audios a destiempo

**Sprites (recorte a mano = DUEÑO; tú auditas y re-integras):**
1. Corre la auditoría de su hoja para señalar EXACTAMENTE qué cuadros están mal (mira en `tools/`
   los `sf_audit_*` / `audit_sf_fighters.py` / `validate_sf_chroma_character.py`; ejecútalos sobre
   `latzitzimime`/`la_tzitzimime` y reporta VACIO/MULTI_FIGURA/ESCALA/BORDE por cuadro).
2. El dueño recorta a mano en Paint los cuadros que marques.
3. Tú los re-importas con `python tools/sf_import_fixed_pose.py <char> <cuadro.png> <clave>` y
   re-empacas SOLO a ella: `python tools/pack_sf_character.py <char> <Title> --gen <RAÍZ_GEN>`
   (el GEN vive FUERA del repo, en `../newSFAssets/GEN_*/<char>`; confirma el nombre real del char y
   del GEN antes). Regla de oro: tras re-empacar, `git status` debe mostrar SOLO esa peleadora
   (`DATA/<char>.json` + `IMAGES/<Char>.webp`); si aparece otra, algo se re-empacó de más →
   `git checkout -- <ruta>`.

**Audios a destiempo (recorte = DUEÑO da segundos, tú ejecutas):** sus `.ogg`
(`special_la_tzitzimime_*`) están corridos; el dueño te da inicio/fin de cada uno y los recortas
como en el punto 2.

---

## Reglas al cerrar
- **Verifica antes de afirmar** (este proyecto acumula informes falsos de "está al 100 %"). Mide:
  JSON válido, `git status` solo lo tocado, y si tocaste código: `.\gradlew.bat compileDebugKotlin`.
- Actualiza `README for IAS\_SESION_ACTUAL.md` con lo hecho y lo que quede pendiente.
- Lo que NO puedas resolver sin oír/recortar a mano, **déjalo listado para el dueño**, no lo inventes.
