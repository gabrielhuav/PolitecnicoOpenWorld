# PROMPT · 10 hojas NUEVAS por personaje (moveset 3rd Strike) — Sol 5.6, 2026-07-20

> **Uso:** una CONVERSACIÓN NUEVA de ChatGPT (Sol 5.6) POR PERSONAJE. El dueño adjunta
> **UNA sola hoja** del personaje (la `_01_Idle_Turn.png`) y pega el prompt de abajo con
> el nombre rellenado. Salen **10 imágenes** (hojas 20–29) con poses NUEVAS del MISMO
> personaje. Complementa a `GUIA_regeneracion_sprites_croma.md` (proceso vigente de
> recorte); el catálogo base 01–19 ya existe para los 17 peleadores.

## Rutas (dónde vive cada cosa)

| Qué | Ruta |
|---|---|
| **Hojas ORIGINALES** (19 por personaje, croma verde, 2 grupos c/u) | `newSFAssets/<Personaje>/<Personaje>_01..19_*.png` (raíz del repo, fuera del proyecto Android) |
| **Hoja que se ADJUNTA a Sol 5.6** (identidad + regla de escala) | `newSFAssets/<Personaje>/<Personaje>_01_Idle_Turn.png` |
| **Recortes intermedios** (256², transparentes, pies en 128,224) | `newSFAssets/GEN_<char>_intermedio/` (se generan en `app/src/main/assets/STREETFIGHTER/GEN/<char>/` y se SACAN del APK al terminar) |
| **Resultado FINAL empacado** (lo que usa el juego) | `PolitecnicoOpenWorld/app/src/main/assets/STREETFIGHTER/IMAGES/<Titulo>.png` + `DATA/<char>.json` |
| **Proceso de recorte** (lo corrió GPT Sol 5.6 la vez pasada) | `tools/slice_sf_chroma_sheets.py` (hoja 01 SIEMPRE primero: fija `_scale.json`) → `tools/pack_sf_character.py <char> <Titulo>` — detalle paso a paso en `GUIA_regeneracion_sprites_croma.md` §0 |
| Validación post-recorte | `tools/validate_sf_chroma_character.py` + QA visual `tools/sf_contact_sheet.py <char> mid` |

## Garantía de calidad/estándar (por qué NO van a salir chiquitos)

1. **En el prompt:** la hoja adjunta es la **REGLA DE ESCALA** — se exige altura de pie
   idéntica a la referencia, misma línea base y prohibido reescalar para llenar espacio.
2. **En el pipeline:** el slicer calibra CADA secuencia contra `_scale.json` de la hoja 01
   del personaje (`SF_POSE_TARGET_H`): un dibujo 15–25% más chico se re-normaliza solo.
3. **Post-recorte:** `validate_sf_chroma_character.py` (siluetas anómalas) + hoja de
   contacto (`tools/_contact_sheets/`) para verlo todo de un vistazo antes de empacar.

## ⚠️ Código pendiente antes de poder RECORTAR las hojas nuevas

`slice_sf_chroma_sheets.py` solo conoce las hojas 1–19 (dict `SHEETS`). Cuando lleguen
las primeras hojas 20–29 hay que: añadir sus entradas al dict (label + conteos + claves
de frame nuevas), definir los `jsKey` nuevos en `SfFighterState` y sus handlers (ver
`DISENO_ARCADE_SF_POW.md` §P1). Los combos base (cancels+contador) YA están (2026-07-20).

---

## EL PROMPT (pegar en conversación NUEVA; rellenar [PERSONAJE]; adjuntar su hoja 01)

```text
Estas imágenes y este mensaje son las referencias permanentes de esta conversación para
AMPLIAR los sprites de mi juego de pelea 2D "Politécnico Open World: HUELUM VS. GOYA".

La imagen adjunta es la hoja de sprites APROBADA "Idle + Idle Turn" de [PERSONAJE]. Es la
ÚNICA fuente de identidad y la REGLA DE ESCALA de toda esta producción.

REGLA PRINCIPAL — IDENTIDAD BLOQUEADA, SIN VARIANTES:
No generes variantes visuales ni reinterpretaciones. En TODAS las hojas conserva EXACTAMENTE
el personaje de la hoja adjunta: mismo rostro, anatomía, proporciones, ropa, paleta, peinado,
sombreado, nivel de detalle y grosor de contorno (~1 px). Las hojas nuevas agregan ANIMACIONES
nuevas del MISMO diseño aprobado; el estilo es pixel art 16-bit de pelea 2D, vista lateral,
formas legibles en celular, sin ningún elemento de Street Fighter u otra franquicia.

ESCALA — IDÉNTICA A LA HOJA ADJUNTA:
Usa la hoja adjunta como regla: el personaje DE PIE debe medir EXACTAMENTE los mismos píxeles
de alto que en ella. Misma escala de cabeza, torso y extremidades en todas las acciones y las
10 hojas. Los pies de los cuadros terrestres comparten una misma línea base por grupo. En
saltos y aéreos se conserva la escala del cuerpo (solo cambia la posición vertical). PROHIBIDO
agrandar o encoger al personaje para llenar o ahorrar espacio.

FONDO Y COMPOSICIÓN:
Fondo VERDE CROMA PURO, PLANO Y OPACO: #00FF00 en toda la imagen. NUNCA fondo transparente,
blanco ni de otro color, y nunca uses #00FF00 dentro del personaje ni de los efectos. Sin
suelo, sombras, texturas, degradados, HUD, logotipos, marcos, texto descriptivo, personajes
extra ni marcas de agua. Solo dos títulos pequeños en AMARILLO por hoja (uno por grupo).

REJILLA — COMPACTA PERO SEGURA PARA RECORTE AUTOMÁTICO:
Cuadros de izquierda a derecha, separación UNIFORME de unos 40–60 píxeles entre cuadros y
entre filas (más juntos que en la producción anterior, que quedó muy espaciada). NINGÚN
cuadro debe tocar o invadir a otro, a los títulos ni a los bordes de la imagen. No recortes
cabello, manos, pies ni efectos. Cada hoja contiene UN personaje y EXACTAMENTE DOS grupos de
animación con la cantidad EXACTA de cuadros indicada: sin cuadros extra ni combinados.

ORIENTACIÓN:
El personaje mira SIEMPRE a la DERECHA, incluso cuando la acción retrocede (backdash, ser
lanzado): el desplazamiento es hacia atrás pero el sprite no se voltea.

OPONENTE INVISIBLE (hojas de agarre):
NUNCA dibujes al rival. Las manos sujetan un espacio vacío coherente; el motor superpone al
oponente como capa aparte.

GENERA LAS 10 HOJAS SIGUIENTES, UNA IMAGEN POR HOJA (10 imágenes separadas, mismas reglas
todas). No te detengas a pedir aprobación entre hojas; si alguna sale mal la corregiré
puntualmente después.

[PERSONAJE]_20_Dash_Backdash
DASH FORWARD — 4 cuadros. Impulso corto y explosivo hacia adelante: anticipación, empuje,
deslizamiento con torso bajo y frenado en guardia. NO es correr (es arranque de 2-3 pasos);
máximo 2-3 líneas de velocidad pixel art.
BACKDASH — 4 cuadros. Hop defensivo hacia ATRÁS mirando a la derecha: impulso, vuelo corto
bajo, aterrizaje estable en guardia.

[PERSONAJE]_21_BlockAlto_BlockBajo
BLOCK ALTO — 4 cuadros. Bloqueo de pie: entrar en guardia cerrada (antebrazos cubren cara y
pecho), absorber un impacto (torso retrocede 2-3 px, pies plantados), mantener, volver a
guardia. Sin atacante ni chispas.
BLOCK BAJO — 4 cuadros. Lo mismo en cuclillas cubriendo torso y piernas; la cabeza NO cambia
de tamaño, solo la postura.

[PERSONAJE]_22_ParryAlto_ParryBajo
PARRY ALTO — 3 cuadros. Desvío de pie: antebrazo/palma adelanta seco y confiado con paso
mínimo, contacto del desvío, regreso a guardia. Se permite un destello MUY pequeño
blanco/azul en la mano (nunca verde).
PARRY BAJO — 3 cuadros. El mismo desvío en cuclillas, desviando hacia abajo.

[PERSONAJE]_23_CrouchPunch_CrouchKick
CROUCH PUNCH — 4 cuadros. Golpe de puño EN CUCLILLAS (misma altura del crouch aprobado):
guardia baja, extensión rápida al frente a media altura, contacto, regreso.
CROUCH KICK — 4 cuadros. Patada a ras de piso desde cuclillas: preparación, extensión
completa de la pierna delantera, contacto, recogida.

[PERSONAJE]_24_CrouchAntiaereo_Barrida
CROUCH HEAVY PUNCH (ANTIAÉREO) — 5 cuadros. Desde cuclillas, golpe fuerte ascendente en
diagonal arriba-adelante que cubre el aire: carga baja, ascenso del puño (o su objeto
característico), máxima extensión arriba, recuperación a cuclillas. Los pies no despegan.
BARRIDA (SWEEP) — 6 cuadros. Patada fuerte agachada que DERRIBA: giro amplio y bajo
barriendo el suelo, anticipación clara, máxima extensión, seguimiento con peso, regreso a
cuclillas. Su patada baja de MAYOR alcance.

[PERSONAJE]_25_AirPunch_AirKick
AIR PUNCH — 4 cuadros. Golpe de puño EN EL AIRE hacia abajo-adelante (cuerpo suspendido a
media altura de salto, sin línea base): guardia aérea, carga, extensión, regreso.
AIR KICK — 4 cuadros. Patada aérea en diagonal descendente (la clásica de salto para abrir
combos): rodilla al pecho, extensión completa, seguimiento, regreso.

[PERSONAJE]_26_PatadaLarga_Overhead
PATADA LARGA — 7 cuadros. Su patada DE PIE de MÁXIMO ALCANCE (roundhouse extendida con paso
adelante): carga de cadera visible, paso de avance, giro, extensión TOTAL de la pierna a la
altura del pecho (la silueta más larga y horizontal de todos sus golpes), seguimiento con
peso, recogida, guardia. Pierna de apoyo firme.
OVERHEAD — 5 cuadros. Golpe por ENCIMA que rompe guardia baja: se alza en puntas y deja caer
un golpe descendente en arco (puño de martillo o su objeto característico), contacto
abajo-adelante, recuperación.

[PERSONAJE]_27_AgarreLanzamiento_Taunt
AGARRE Y LANZAMIENTO — 8 cuadros. 2 de intento (paso corto con ambas manos cerrándose sobre
el espacio vacío del rival invisible) + 6 del lanzamiento conectado (sujeta el espacio a la
altura del cuello del rival invisible, carga el peso, gira y proyecta con fuerza hacia
adelante, pose de seguimiento). El rival NO aparece en ningún cuadro.
TAUNT — 6 cuadros. Burla/provocación en bucle con TODA la personalidad del personaje. Sin
texto ni efectos grandes.

[PERSONAJE]_28_SerLanzado_Levantarse
SER LANZADO — 6 cuadros. Es proyectado hacia atrás (sin agresor visible): despega, vuela de
espaldas con brazos al aire, gira, impacta el suelo, queda tendido boca arriba. Sin voltear
el sprite; sin sangre.
LEVANTARSE (WAKE-UP) — 6 cuadros. Del suelo a la guardia: tendido boca arriba, gira/apoya un
brazo, rodilla al piso, impulso, recuperación completa. Pies terminan en la línea base.

[PERSONAJE]_29_SuperArt_DanoAgachado
SUPER ART — 8 cuadros. Su ataque MÁXIMO cinematográfico: 2 cuadros de activación (pose de
concentración con aura/destello pixel art temático del personaje, nunca verde) + gran
anticipación + 2-3 cuadros de ejecución con su objeto característico al límite + clímax con
su efecto más grande (pixel art contenido, sin fuego realista) + recuperación exhausta.
DAÑO AGACHADO — 4 cuadros. Recibir un golpe ESTANDO EN CUCLILLAS: contracción, retroceso
corto del torso, protección, recuperación. Sin sangre ni atacante.

[PERSONAJE]_20 a _29: verifica antes de entregar cada imagen que (1) el fondo es #00FF00
opaco, (2) la altura del personaje coincide con la hoja adjunta, (3) los conteos de cuadros
son EXACTOS, (4) los dos títulos amarillos coinciden con los nombres de los grupos.
```

## Al recibir las 10 imágenes (por personaje)

1. Guardarlas en `newSFAssets/<Personaje>/` con los nombres canónicos `_20`…`_29` (verificar
   por los títulos amarillos, NO por el nombre de descarga).
2. AVISAR a la IA de código: falta extender `SHEETS` (20–29) en el slicer + estados nuevos
   en `SfFighterState` antes de recortar/empacar (ver arriba).
