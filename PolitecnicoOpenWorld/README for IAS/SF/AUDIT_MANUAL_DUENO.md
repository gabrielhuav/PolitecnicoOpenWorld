# 👁️ AUDIT MANUAL DEL DUEÑO — hojas `_audit_sheets`

> Revisión **a ojo** de `tools/_audit_sheets/<char>_TODO.png`, peleador por peleador.
> Es la fuente de verdad: manda sobre cualquier audit automático.
>
> **EN CURSO** (2026-07-21). El dueño va dictando por tandas; esta tabla se va completando.

## Cómo leer esto (las 3 capas que NO son lo mismo)

Un "está mal recortado" puede vivir en tres sitios distintos. Distinguirlos es lo que evita
arreglar donde no toca:

| Capa | Qué es | Dónde |
|---|---|---|
| **Arte fuente** | La hoja croma que dibujó el artista | `newSFAssets/<PJ>/<PJ>_NN_*.png` |
| **Atlas empacado** | Lo que de verdad carga el juego | `assets/STREETFIGHTER/IMAGES/<Pj>.webp` |
| **Animación** | Qué cuadros encadena cada estado | `assets/STREETFIGHTER/DATA/<pj>.json` → `animations` |

**Caso ESCOMBOY (resuelto 2026-07-21):** el arte fuente está PERFECTO (14 poses limpias en
2 filas), la animación referencia los cuadros correctos, y la hoja de auditoría es FIEL.
El defecto está solo en el **atlas empacado**: el slicer fusionó cada pose con la de la fila
de abajo. Por eso "los assets se ven bien" y el juego se ve mal a la vez.

## Estado por peleador

| Peleador | Veredicto del dueño | Qué falla | Causa confirmada |
|---|---|---|---|
| **charronegro** | ✅ **Todo bien recortado** | — | — |
| **escomboy** | ❌ Mal | `hurtHeadLight`, `hurtHeadMedium`, `hurtHeadHeavy`, `fatality`, `superArt` (completo pero mal recortado) | **C1** en `hit-face-1..4`. Las 3 `hurtHead*` salen SOLO de `hit-face-*` → **un único defecto, no tres**. |
| **escomgirl** | ❌ Mal (lo demás bien) | `hurtHeadLight`, `hurtHeadMedium`, `hurtHeadHeavy` — "vienen dobles" | **C1** en `hit-face-1..4`, igual que escomboy. |
| **lallorona** | ⚠️ Parcial | `fatality` y `superArt`: **solo** `super-4`, `super-5`, `super-6`. El resto bien. | **C2**: los blobs 03/04/05 de la hoja 29 son los únicos SIN hueco entre ellos (601-823, 823-1037, 1037-1301). Salieron de un blob fusionado partido por `maybe_split`. **El arte está entero; los cortes están mal puestos.** |
| **lapresidenta** | ❌ Mal (bonus powers) | `bonusPower1` (solo anima en `bonus-1-1`), `bonusPower2` mal, `bonusPower3` (solo `bonus-3-1`), `bonusPower4` mal por poco, `bonusPower5` bien recortado pero el poder sale incompleto, `bonusPower6` mal por poco, `bonusPower7` muy bien pero **no cierra el círculo del poder**, `bonusPower8` **le falta por delante**, `bonusPower9` mal, `bonusPower10` **todo mal, el martillo no sale completo**. `fatality`: `super-4`, `super-5`, `bonus-1-1`. `superArt` igual. | **C3** confirmado por medición: los 20 cuadros de `bonus-7/8/9/10` **tocan el borde DERECHO** del lienzo de 256 px. Además `bonus-7/8/9/10` son cuadros **idénticos entre sí** (estáticos). |
| latzitzimime | *(pendiente)* | | |
| **latzitzimime** | ✅ **La mejor de todas** | `bonusPower3/4/5` traen arte de **Yoalli**, no suya (pero bien recortados). Todo lo demás muy bien. | ✅ **Resuelto**: `BONUS_FAST_POWERS` recorta `bonusPower4` a `bonus-4-5` y `bonusPower5` a `bonus-5-4/5-5`. `bonusPower3` **pendiente de decisión**. |
| **paparazzi1** | ⚠️ Muy bien salvo 1 | `fatality` y `superArt`: solo `super-6`, "un poco más de recorte a la **derecha**", mínimo | **C2** |
| **paparazzi5** | ⚠️ Muy bien salvo 1 | `fatality` y `superArt`: solo `super-6`, "falta recorte **antes**", muy leve | **C2** |
| **paramedicocruzroja** | ⚠️ Bien salvo 1 | `super-6` es **onda de poder pura**, sin personaje. Propuesta del dueño: encimarlo con el anterior; desde `special-4` debería seguir su onda | **Decisión de arte**, no fallo de recorte |
| **policiacdmx** (mujer) | ✅ **Bien** | — | — |
| **policiacdmxhombre** | ⚠️ Bien salvo 1 | `fatality` y `superArt`: solo `super-6` | **C2** |
| **policiagranaderohombre** | ✅ **Bien** | — | — |
| policiagranaderomujer | *(pendiente)* | | |
| **prankedy** | ✅ **Todo bien** | — | — |
| **reygrupero** | ⚠️ Bien salvo 1 | `fatality` y `superArt`: `super-6` y `super-7` **completan UN mismo asset partido en dos** | **C2** en su forma más clara: el corte partió una sola pose |
| **robot** (ESCOMROBOT) | ✅ **Todo bien** | — | — |
| **senortienda** | ❌→✅ | `hurtHeadLight/Medium/Heavy` dobles, igual que escomboy | **C1** · ✅ **CORREGIDO** |
| **yoalliehecatl** | ❌→✅ | `hurtHeadLight/Medium/Heavy` dobles, igual que escomboy | **C1** · ✅ **CORREGIDO** |

> 🔑 **Observación del dueño sobre C1:** en esas tandas, "el último asset de la fila, o los
> dos últimos, vienen bien". Encaja con la causa: la fusión vertical empareja cada pose con
> la de la fila de abajo, y las últimas de la fila se quedan sin pareja.

## ⚠️ Las hojas `_TODO.png` se quedan OBSOLETAS

Reportar sobre una hoja vieja hace perder el tiempo a todos: pasó con `yoalliehecatl`, que ya
estaba corregido (atlas 17:48) pero su hoja era de las 15:45. **Regenera SIEMPRE antes de
auditar:**

```
python tools/sf_audit_sheets.py          # las 18 hojas completas
python tools/sf_audit_status_sheet.py    # el estado de lo ya reportado
```

## Las 3 causas raíz (C1, C2, C3)

Todo lo reportado hasta ahora se explica con **tres** defectos del pipeline, no con decenas
de assets malos. El arte fuente está bien en los tres casos.

### C1 · Dos figuras apiladas en `hit-face-*`

`merge_fragments` comparaba solo el solapamiento en **X**. Cuando la fila HURT HEAD viene en
**dos filas de 7 poses**, al aplanar y ordenar por X cada pose queda junto a la de abajo, con
solape horizontal casi total → las fusionaba en vertical.

- **Afecta a:** escomboy, escomgirl (confirmados a ojo), senortienda, yoalliehecatl.
- **Estado:** ✅ **arreglado** en `slice_sf_chroma_sheets.py` (fusión fila por fila, verificado
  sobre las 520 hojas con 0 regresiones). ⚠️ **Inerte:** falta re-recortar y re-empaquetar.
- escomboy y yoalliehecatl ya dan `HURT HEAD 14/14 OK`. senortienda y escomgirl dan 15/14
  porque sus hojas traen **16 poses, no 14**.

### C2 · Cortes arbitrarios cuando dos poses con efecto se tocan

Si dos poses de la fila se tocan (sus auras se solapan), el croma las detecta como UN blob y
`maybe_split` lo parte en cortes calculados por "valle de densidad". Esos cortes **atraviesan
el efecto**, así que cada cuadro se lleva un trozo del vecino y pierde el suyo.

- **Firma para detectarlo:** blobs contiguos SIN hueco entre ellos (`x1` de uno == `x0` del
  siguiente). Con `--list` se ve al instante.
- **Afecta a:** lallorona `super-4/5/6`, lapresidenta `super-4/5`, y probablemente el resto de
  `superArt` reportados como "completo pero mal recortado".
- **Estado:** ❌ **sin arreglar.**

### C3 · El slicer NO busca el cuerpo en los cuadros de poder

`slice_sf_chroma_sheets.py:659` → `anchor_body=label.startswith("SPECIAL")`.

La función que localiza el cuerpo (`dense_body_center_x`, que ignora el efecto y se queda con
el primer grupo denso de columnas) se aplica **SOLO** a las etiquetas que empiezan por
`SPECIAL`. La hoja 29 se llama `SUPER ART`, y los bonus powers van por otro script
(`slice_sf_bonus_powers.py`). En esos casos `place_sf` hace `x = CX - w // 2`: centra la
**caja entera**, efecto incluido. Resultado: el personaje se descoloca y **el poder se sale
del lienzo de 256 px**.

Medido sobre los cuadros ya empaquetados (arte que llega justo al borde = se cortó):

| prefijo | tocan el borde | total |
|---|---|---|
| `bonus-*` | **21** | 130 (16 %) |
| `special-*` | 15 | 270 (6 %) |
| `super-*` | 0 | 144 |

De los 21 de `bonus-*`, **todos son de La Presidenta** y **todos por la DERECHA** — que es
justo hacia donde lanza el poder: `bonus-7-1..5`, `bonus-8-1..5`, `bonus-9-1..5`,
`bonus-10-1..5`. Coincide exactamente con "le falta cerrar el círculo", "le falta más por
delante" y "el martillo no sale completo".

- **Estado:** ❌ **sin arreglar.** El arreglo es anclar el cuerpo también en `SUPER ART` y en
  los bonus powers, y/o permitir que el lienzo no recorte el efecto.
- ⚠️ Tocar esto afecta a los 18 peleadores a la vez → **delegar a Sol 5.6 o Fable 5**.

## Correlación con el audit automático

`tools/sf_audit_frames_auto.py` marcó `MULTI_FIGURA` en `hit-face-*` de exactamente cuatro
peleadores: **senortienda, escomboy, escomgirl, yoalliehecatl**. La revisión manual de
escomboy lo confirma de forma independiente, y `charronegro` (que el automático da por
limpio) también sale limpio a ojo. Las dos vías coinciden hasta ahora.

## Qué animaciones dependen de qué cuadros (para no arreglar de más)

Un cuadro roto rompe TODAS las animaciones que lo usan. En escomboy:

```
hit-face-1  -> hurtHeadLight, hurtHeadMedium
hit-face-2  -> hurtHeadLight, hurtHeadMedium
hit-face-3  -> hurtHeadMedium, hurtHeadHeavy
hit-face-4  -> hurtHeadHeavy
stun-1/2/3  -> hurtHeadHeavy   (además: los 3 son el MISMO pixel en los 18 peleadores)
super-1..8  -> superArt, fatality
victory-*   -> fatality
```

Para recalcular esto en otro peleador: mirar `animations` en `DATA/<pj>.json`.

## Herramienta para inspeccionar una fila concreta

Las hojas `<char>_TODO.png` miden ~1728×7050 px. Para mirar UNA animación:

- Fila = índice en `sorted(a for a, v in animations.items() if v)` — **alfabético**, y las
  animaciones vacías no ocupan fila.
- Alto de fila = `CELL + 16` = **128 px**, la rejilla arranca en `y = 6`.

⚠️ Equivocarse en cualquiera de los dos recorta una fila vecina y hace creer que el defecto
está en otra animación (pasó en esta sesión: se pidió `fatality` y salió `sweep`).
