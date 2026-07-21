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
| **escomboy** | ❌ Mal | `hurtHeadLight`, `hurtHeadMedium`, `hurtHeadHeavy`, `fatality`, `superArt` (completo pero mal recortado) | `hit-face-1..4` con **2 figuras apiladas** por celda (`MULTI_FIGURA`). Las 3 animaciones `hurtHead*` salen SOLO de `hit-face-*`, así que **es un único defecto, no tres**. `superArt`/`fatality`: pendiente de aislar. |
| escomgirl | *(pendiente)* | | |
| lallorona | *(pendiente)* | | |
| lapresidenta | *(pendiente)* | | |
| latzitzimime | *(pendiente)* | | |
| paparazzi1 | *(pendiente)* | | |
| paparazzi5 | *(pendiente)* | | |
| paramedicocruzroja | *(pendiente)* | | |
| policiacdmx | *(pendiente)* | | |
| policiacdmxhombre | *(pendiente)* | | |
| policiagranaderohombre | *(pendiente)* | | |
| policiagranaderomujer | *(pendiente)* | | |
| prankedy | *(pendiente)* | | |
| reygrupero | *(pendiente)* | | |
| robot | *(pendiente)* | | |
| senortienda | *(pendiente)* | | |
| yoalliehecatl | *(pendiente)* | | |

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
