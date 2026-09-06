# PROMPT · Hoja 30 NUEVA por personaje (Contraataque + Derribo con Poder) — 2026-08-29

> **✅ CORREGIDO (2026-08-29b):** la 1ª tanda (los 18 generados en una sola conversación con
> memoria) salió con los cuadros de **DERRIBO CON PODER muy pegados** (12 cuadros en UNA sola
> fila de 1672px) — confirmado visualmente en `Prankedy_30_...png` y `LaLlorona_30_...png`:
> "Contraataque" (6 cuadros en su fila) quedó bien espaciado, pero la fila de 12 quedó tan
> comprimida que el recorte automático fundiría cuadros vecinos. **Causa:** más cuadros por
> fila = menos espacio por cuadro en el mismo ancho fijo; 8 es el máximo que ya funciona bien
> en el catálogo (hoja 27/29). **Fix aplicado abajo:** Derribo con Poder pasa de 1 fila de 12 a
> **2 filas (4 + 8)**, y la regla de rejilla ahora exige un hueco mínimo explícito en vez de un
> rango "sugerido". Los 18 PNG ya generados con el prompt viejo hay que **regenerarlos** con
> la versión de abajo — no sirven para recortar tal como están.

> **Uso:** una CONVERSACIÓN NUEVA de IA de imagen (mismo flujo que `PROMPT_SOL56_TANDAS_NUEVAS.md`)
> POR PERSONAJE. El dueño adjunta **UNA sola hoja** del personaje (la `_01_Idle_Turn.png`) y pega
> el prompt de abajo con el nombre rellenado. Sale **1 imagen** (hoja 30) con las 2 poses nuevas
> del diseño de `DISENO_ARCADE_SF_POW.md` (Contraataque + Derribo con Poder). Complementa a
> `PROMPT_SOL56_TANDAS_NUEVAS.md` (hojas 20-29, mismo formato/reglas) — el catálogo 01-29 ya
> existe para los 18 peleadores dedicados.
>
> ⚠️ **No reutilices `newSFAssets/*_30_CounterGrab_ThrowKnockdownSpecial*`**: esas hojas se
> generaron SIN este diseño (miden 1672×941, el formato de las hojas 02-19, no el 1448×1086 de
> las hojas 20-29; y traen 8+8 cuadros sin corresponder a ningún `jsKey` del motor). Descártalas
> y genera la hoja 30 de cero con el prompt de abajo.
>
> 🆕 Hay DOS variantes del prompt más abajo: **por personaje** (conversación nueva + hoja 01
> adjunta, una imagen) y **en lote** (una conversación que ya tiene memoria de los 18
> personajes, pide las 18 imágenes de un tirón). Usa la que corresponda a tu flujo actual.
>
> ⚠️ **CORRECCIÓN 2 (2026-08-29c) — 9 de 18 dibujan al rival, rompe "oponente invisible":**
> revisé visualmente los 18 PNG de la 2ª tanda (3 filas, espaciado ya corregido — ESO quedó
> bien). Pero en la Fila 3 (Lanzamiento Conectado), 9 personajes dibujaron un cuerpo/maniquí/
> silueta siendo agarrado o lanzado, en vez de dejar las manos cerradas sobre el aire:
> **ESCOMROBOT** (maniquí blanco en brazos), **La Presidenta** (rival de traje con cara,
> volando), **Yoalli Ehécatl**, **Charro Negro**, **La Tzitzimime** (figura pequeña cayendo),
> **Policía Femenino CDMX** (carga un maniquí blanco 3 cuadros seguidos), **Policía Masculino
> CDMX** (cuerpo tirado en el piso), **Paramédico Cruz Roja** (carga una figura en brazos),
> **Policía Granadero Hombre** (tira a OTRO granadero al piso). Los otros 9 (Prankedy, La
> Llorona, Señor Tienda, Paparazzi 1, Rey Grupero, Paparazzi 5, ESCOMBOY, ESCOMGIRL, Policía
> Granadero Mujer) SÍ dejaron las manos vacías — sin rival, solo un efecto de impacto en el
> piso — y esos NO hace falta tocarlos. Prompt de regeneración SOLO para los 9 afectados, más
> abajo en "CORRECCIÓN 2".
>
> ✅ **CORRECCIÓN 4 RESUELTA Y VERIFICADA (2026-08-30).** 3 de 18 tenían la Fila 3 corta por 1
> cuadro (el efecto de impacto flotando solo en el hueco central en vez de acompañar a una
> pose) — un defecto que la revisión visual de las correcciones 2/3 no cazó porque no se nota a
> ojo, solo al recortar de verdad. **La Presidenta, Paramédico Cruz Roja y Policía Masculino
> CDMX** regeneraron su hoja con las 8 poses completas, se verificaron visualmente (Fila 3 con 8
> siluetas, efecto pegado a una pose real) y ya quedaron recortadas + empaquetadas. **Los 18
> personajes están completos, recortados y empaquetados** en `app/src/main/assets/STREETFIGHTER/`
> — motor verificado en verde (`gradle test`, detekt, nombres de test) después del empaquetado
> final. Esta hoja 30 queda cerrada.

## Rutas (mismas que el resto del catálogo)

| Qué | Ruta |
|---|---|
| Hoja que se ADJUNTA a la IA (identidad + regla de escala) | `newSFAssets/<Personaje>/<Personaje>_01_Idle_Turn.png` |
| Hoja 30 resultante | `newSFAssets/<Personaje>/<Personaje>_30_Contraataque_DerriboPoder.png` |
| Recortes intermedios | `newSFAssets/GEN_<char>_intermedio/` |
| Resultado FINAL empacado | `app/src/main/assets/STREETFIGHTER/IMAGES/<Titulo>.png` + `DATA/<char>.json` |

## ✅ Código del pipeline — YA HECHO (2026-08-30)

El motor (`SfFighterState.COUNTER/POWER_GRAB/POWER_THROW`, máquina de estados, IA, botones L3/R3)
**ya estaba implementado y con tests en verde** (ver commits de 2026-08-29). El pipeline de
imagen (`SHEETS[30]` en `tools/slice_sf_chroma_sheets.py`, `NEW_MOVE_ANIMATIONS`/
`reference_frame_key()` en `tools/pack_sf_character.py`) también quedó extendido, con 2 bugs
reales encontrados y corregidos en `merge_fragments`/`maybe_split` (ambos en
`slice_sf_chroma_sheets.py`): un fragmento suelto (el propio efecto de impacto de la Fila 3)
tumbaba la separación por filas y fusionaba Fila 2 con Fila 3; y `maybe_split` reordenaba todo
por X puro incluso sin partir nada, intercalando las 2 filas y rompiendo el mapeo
`power-grab-1..4`/`power-throw-1..8`. **15 de 18 personajes ya están recortados y empaquetados**
en `app/src/main/assets/STREETFIGHTER/` (motor verificado en verde después). Los otros 3
necesitan regenerar la hoja — ver CORRECCIÓN 4 más abajo.

⚠️ **La caché GEN real vive en `newSFAssets/GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio/`**
(commiteada a git, no en `app/src/main/assets/.../GEN` — esa ruta nunca debe usarse, viajaría al
APK). Cualquier `slice_sf_chroma_sheets.py`/`pack_sf_character.py` que se corra después de esto
necesita `--gen ../newSFAssets/GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio`.

---

## EL PROMPT (pegar en conversación NUEVA; rellenar [PERSONAJE]; adjuntar su hoja 01)

```text
Estas imágenes y este mensaje son las referencias permanentes de esta conversación para
AMPLIAR los sprites de mi juego de pelea 2D "Politécnico Open World: TITULACIÓN POR COMBATE".

La imagen adjunta es la hoja de sprites APROBADA "Idle + Idle Turn" de [PERSONAJE]. Es la
ÚNICA fuente de identidad y la REGLA DE ESCALA de toda esta producción.

REGLA PRINCIPAL — IDENTIDAD BLOQUEADA, SIN VARIANTES:
No generes variantes visuales ni reinterpretaciones. Conserva EXACTAMENTE el personaje de la
hoja adjunta: mismo rostro, anatomía, proporciones, ropa, paleta, peinado, sombreado, nivel de
detalle y grosor de contorno (~1 px). Esta hoja agrega ANIMACIONES nuevas del MISMO diseño
aprobado; el estilo es pixel art 16-bit de pelea 2D, vista lateral, formas legibles en celular,
sin ningún elemento de Street Fighter u otra franquicia.

ESCALA — IDÉNTICA A LA HOJA ADJUNTA:
Usa la hoja adjunta como regla: el personaje DE PIE debe medir EXACTAMENTE los mismos píxeles
de alto que en ella. Misma escala de cabeza, torso y extremidades en las dos animaciones.
PROHIBIDO agrandar o encoger al personaje para llenar o ahorrar espacio.

FONDO Y COMPOSICIÓN:
Fondo VERDE CROMA PURO, PLANO Y OPACO: #00FF00 en toda la imagen. NUNCA fondo transparente,
blanco ni de otro color, y nunca uses #00FF00 dentro del personaje ni de los efectos. Sin
suelo, sombras, texturas, degradados, HUD, logotipos, marcos, texto descriptivo, personajes
extra ni marcas de agua. Solo dos títulos pequeños en AMARILLO (uno por grupo).

REJILLA — SEPARACIÓN GENEROSA, PRIORIDAD SOBRE APROVECHAR ESPACIO:
Reparte cada fila en columnas de ANCHO IGUAL (ancho de la imagen ÷ número de cuadros de ESA
fila) y centra el personaje dentro de su columna — no los agrupes hacia la izquierda ni los
dejes casi tocándose. Deja un hueco de VERDE PURO de AL MENOS 70 px entre el borde de un
personaje y el borde del vecino (borde a borde, no centro a centro). Es MÁS IMPORTANTE dejar
espacio de sobra que aprovechar el ancho: el recorte automático agrupa cada cuadro por el hueco
de verde que lo separa del siguiente, así que dos cuadros casi tocándose se FUNDEN en un solo
recorte y arruinan la hoja completa. NINGUNA fila lleva más de 8 cuadros: si una animación
necesita más, repártela en VARIAS FILAS bajo el MISMO título (la fila siguiente NO lleva título
propio, es la continuación del mismo grupo). Usa el alto de imagen que haga falta para dar
cabida CÓMODA a todas las filas sin comprimir el espaciado — no fuerces más filas en la misma
altura. Ningún cuadro debe tocar o invadir a otro, a los títulos ni a los bordes de la imagen.
No recortes cabello, manos, pies ni efectos. La hoja contiene UN personaje y EXACTAMENTE DOS
grupos de animación (aunque uno de ellos ocupe varias filas) con la cantidad EXACTA de cuadros
indicada: sin cuadros extra ni combinados.

ORIENTACIÓN:
El personaje mira SIEMPRE a la DERECHA.

OPONENTE INVISIBLE (grupo de derribo):
NUNCA dibujes al rival. Las manos sujetan un espacio vacío coherente; el motor superpone al
oponente como capa aparte.

GENERA LA SIGUIENTE HOJA (una imagen, 3 FILAS en total):

[PERSONAJE]_30_Contraataque_DerriboPoder

FILA 1 — CONTRAATAQUE — 6 cuadros. Postura de EMBOSCADA lista para contraatacar: NO es un
desvío con el antebrazo como el parry, son las MANOS ABIERTAS al frente listas para atrapar. 2
cuadros de guardia coiled (piernas flexionadas, peso bajo, manos abiertas, mirada fija en el
rival) sosteniendo la posición, seguidos de 4 cuadros de recuperación VISIBLE (se endereza y
relaja poco a poco si nadie ataca, quedando expuesto más tiempo que en el parry). Sin destellos
ni efectos de contacto: si el contraataque conecta, el juego reutiliza la animación de AGARRE Y
LANZAMIENTO ya existente sobre el rival.

DERRIBO CON PODER — 12 cuadros TOTAL, repartidos en DOS FILAS bajo este MISMO título (la fila 3
es la CONTINUACIÓN de este grupo, no lleva su propio título):
FILA 2 — INTENTO — 4 cuadros. Versión MÁS LENTA Y CARGADA que el agarre normal (el doble de
cuadros que su intento, que es de 2): postura más baja y cargada —tensión visible en brazos o
en su objeto característico—, paso de avance decidido, ambas manos cerrándose con fuerza sobre
el espacio vacío del rival invisible, contacto. El rival debe poder LEER el intento antes de que
conecte.
FILA 3 — LANZAMIENTO CONECTADO — 8 cuadros. Sujeta el espacio a la altura del rival invisible,
carga el peso con MÁXIMA tensión —más marcada que el agarre básico—, gira con ímpetu, proyecta
con fuerza extrema, cuadro de impacto/onda pixel art en el punto de caída del rival invisible
—nunca verde, sin fuego realista—, pose de esfuerzo sostenido, recuperación agitada. El rival NO
aparece en ningún cuadro de ninguna de las 3 filas.

Verifica antes de entregar la imagen que (1) el fondo es #00FF00 opaco, (2) la altura del
personaje coincide con la hoja adjunta, (3) los conteos de cuadros son EXACTOS (fila 1: 6, fila
2: 4, fila 3: 8) y NINGUNA fila tiene más de 8, (4) los dos títulos amarillos coinciden con los
nombres de los grupos (CONTRAATAQUE / DERRIBO CON PODER, este último solo arriba de la fila 2),
(5) hay un hueco de verde puro de al menos 70 px entre cada cuadro y su vecino — si dos casi se
tocan en cualquier fila, vuelve a generar esa fila con más espacio.
```

---

## PROMPT · HOJA 30 EN LOTE (misma conversación, con memoria de los 18 personajes)

> **Uso:** si YA tienes una conversación con la IA de imagen que conserva la identidad
> aprobada de los 18 peleadores dedicados (por ejemplo, la que ya generó
> `ESCOMBOY_15_MediumKickRefined_HeavyKickRefined.png` o
> `LaLlorona_26_PatadaLarga_Overhead.png`), no hace falta abrir 18 conversaciones nuevas
> ni volver a adjuntar cada hoja 01: pega el prompt de abajo UNA vez y pide las 18
> imágenes en el mismo hilo. Si la memoria de algún personaje falló o dudas de su
> apariencia, el prompt le pide a la IA que PARE y pida esa hoja 01 en vez de adivinar
> (evita el riesgo real de este modo: identidad que se desvía sin que se note).

```text
En esta conversación ya tienes establecida la identidad APROBADA (rostro, anatomía,
proporciones, ropa, paleta, peinado, sombreado, altura de pie de referencia) de estos 18
peleadores de mi juego de pelea 2D "Politécnico Open World: TITULACIÓN POR COMBATE":

1. Prankedy
2. El Señor de la Tienda (SenorTienda)
3. Paparazzi 1
4. Rey Grupero
5. Paparazzi 5
6. Estudiante ESCOMBOY
7. Estudianta ESCOMGIRL
8. Robot Estudiantx (ESCOMROBOT)
9. Yoalli Ehécatl
10. El Charro Negro
11. La Llorona
12. La Tzitzimime
13. La Presidenta
14. Policía CDMX, femenino (PoliciaFemeninoCDMX)
15. Policía CDMX, masculino (PoliciaMasculinoCDMX)
16. Paramédico Cruz Roja
17. Policía Granadero, masculino (PoliciaGranaderoMasculinoCDMX)
18. Policía Granadero, femenino (PoliciaGranaderoFemeninoCDMX)

Vamos a AMPLIAR los sprites de LOS 18 con la MISMA hoja nueva (hoja 30), cada uno
manteniendo SU PROPIA identidad ya establecida — SIN mezclar rasgos, ropa ni paleta
entre personajes.

REGLA PRINCIPAL — IDENTIDAD BLOQUEADA POR PERSONAJE, SIN VARIANTES:
Para cada uno de los 18, conserva EXACTAMENTE el diseño que ya tienes de él/ella: mismo
rostro, anatomía, proporciones, ropa, paleta, peinado, sombreado, nivel de detalle y
grosor de contorno (~1 px) que en su hoja 01 ya vista en esta conversación. Estilo pixel
art 16-bit de pelea 2D, vista lateral, formas legibles en celular, sin ningún elemento de
Street Fighter u otra franquicia. Si tienes CUALQUIER duda sobre la apariencia de un
personaje de la lista, PARA en ese personaje y pídeme que te reenvíe su hoja 01 — NO
adivines ni te bases en otro personaje parecido.

ESCALA — IDÉNTICA A LA HOJA 01 DE CADA UNO:
El personaje DE PIE debe medir EXACTAMENTE los mismos píxeles de alto que en su propia
hoja 01 (cada personaje tiene su propia altura de referencia, NO la misma entre todos).
PROHIBIDO agrandar o encoger para llenar o ahorrar espacio.

FONDO Y COMPOSICIÓN:
Fondo VERDE CROMA PURO, PLANO Y OPACO: #00FF00 en toda la imagen. NUNCA fondo
transparente, blanco ni de otro color, y nunca uses #00FF00 dentro del personaje ni de
los efectos. Sin suelo, sombras, texturas, degradados, HUD, logotipos, marcos, texto
descriptivo, personajes extra ni marcas de agua. Solo dos títulos pequeños en AMARILLO
por hoja (uno por grupo).

REJILLA — SEPARACIÓN GENEROSA, PRIORIDAD SOBRE APROVECHAR ESPACIO:
Reparte cada fila en columnas de ANCHO IGUAL (ancho de la imagen ÷ número de cuadros de
ESA fila) y centra el personaje dentro de su columna — no los agrupes hacia la izquierda
ni los dejes casi tocándose. Deja un hueco de VERDE PURO de AL MENOS 70 px entre el borde
de un personaje y el borde del vecino (borde a borde, no centro a centro). Es MÁS
IMPORTANTE dejar espacio de sobra que aprovechar el ancho: el recorte automático agrupa
cada cuadro por el hueco de verde que lo separa del siguiente, así que dos cuadros casi
tocándose se FUNDEN en un solo recorte y arruinan la hoja completa. NINGUNA fila lleva
más de 8 cuadros: si una animación necesita más, repártela en VARIAS FILAS bajo el MISMO
título (la fila siguiente NO lleva título propio). Usa el alto de imagen que haga falta
para dar cabida CÓMODA a todas las filas sin comprimir el espaciado. Ningún cuadro debe
tocar o invadir a otro, a los títulos ni a los bordes de la imagen. No recortes cabello,
manos, pies ni efectos. Cada hoja contiene UN personaje y EXACTAMENTE DOS grupos de
animación (aunque uno ocupe varias filas) con la cantidad EXACTA de cuadros indicada.

ORIENTACIÓN:
El personaje mira SIEMPRE a la DERECHA.

OPONENTE INVISIBLE (grupo de derribo):
NUNCA dibujes al rival. Las manos sujetan un espacio vacío coherente; el motor superpone
al oponente como capa aparte.

LA HOJA A GENERAR — MISMA ESTRUCTURA PARA LOS 18 (3 FILAS), SOLO CAMBIA LA IDENTIDAD:
<Personaje>_30_Contraataque_DerriboPoder

FILA 1 — CONTRAATAQUE — 6 cuadros. Postura de EMBOSCADA lista para contraatacar: NO es un
desvío con el antebrazo como el parry, son las MANOS ABIERTAS al frente listas para
atrapar. 2 cuadros de guardia coiled (piernas flexionadas, peso bajo, manos abiertas,
mirada fija en el rival) sosteniendo la posición, seguidos de 4 cuadros de recuperación
VISIBLE (se endereza y relaja poco a poco si nadie ataca, quedando expuesto más tiempo
que en el parry). Sin destellos ni efectos de contacto: si el contraataque conecta, el
juego reutiliza la animación de AGARRE Y LANZAMIENTO ya existente sobre el rival.

DERRIBO CON PODER — 12 cuadros TOTAL, repartidos en DOS FILAS bajo este MISMO título (la
fila 3 es la CONTINUACIÓN de este grupo, no lleva su propio título):
FILA 2 — INTENTO — 4 cuadros. Versión MÁS LENTA Y CARGADA que el agarre normal (el doble
de cuadros que su intento, que es de 2): postura más baja y cargada —tensión visible en
brazos o en su objeto característico—, paso de avance decidido, ambas manos cerrándose
con fuerza sobre el espacio vacío del rival invisible, contacto. El rival debe poder LEER
el intento antes de que conecte.
FILA 3 — LANZAMIENTO CONECTADO — 8 cuadros. Sujeta el espacio a la altura del rival
invisible, carga el peso con MÁXIMA tensión —más marcada que el agarre básico—, gira con
ímpetu, proyecta con fuerza extrema, cuadro de impacto/onda pixel art en el punto de caída
del rival invisible —nunca verde, sin fuego realista—, pose de esfuerzo sostenido,
recuperación agitada. El rival NO aparece en ningún cuadro de ninguna de las 3 filas.

GENERA 18 IMÁGENES, UNA POR CADA PERSONAJE DE LA LISTA DE ARRIBA, en ese orden, con esa
MISMA hoja (mismo layout de 3 filas, mismas reglas, mismo conteo de cuadros) aplicada a
la identidad propia de cada uno. No te detengas a pedir aprobación entre personajes —
sigue con el siguiente inmediatamente; si alguna sale mal la corrijo puntualmente
después.

Antes de dar cada imagen por buena, verifica: (1) el fondo es #00FF00 opaco, (2) la
identidad y la altura del personaje coinciden EXACTAMENTE con su diseño ya aprobado en
esta conversación — si dudas, PARA y pide su hoja 01 en vez de adivinar, (3) los conteos
de cuadros son EXACTOS (fila 1: 6, fila 2: 4, fila 3: 8) y NINGUNA fila tiene más de 8,
(4) los dos títulos amarillos dicen CONTRAATAQUE / DERRIBO CON PODER (este último solo
arriba de la fila 2), (5) hay un hueco de verde puro de al menos 70 px entre cada cuadro
y su vecino en TODAS las filas — si dos casi se tocan, regenera esa fila con más espacio.
```

---

## CORRECCIÓN 2 — solo 9 personajes (dibujaron al rival en la Fila 3)

> **Uso:** misma conversación con memoria. Pega esto UNA vez; solo se listan los 9 personajes
> afectados — los otros 9 quedaron bien y NO hay que tocarlos. Regenera la HOJA COMPLETA de
> cada uno de estos 9 (no solo la fila 3: pedirle a una IA de imagen que edite un pedazo de un
> PNG ya generado no es confiable) y sustituye el archivo existente.

```text
En la tanda anterior de la hoja 30 (Contraataque + Derribo con Poder), estos 9 personajes
dibujaron al RIVAL en la Fila 3 (Lanzamiento Conectado) — un maniquí, silueta o cuerpo siendo
agarrado/lanzado/tirado al piso. Eso rompe una regla del diseño: el motor del juego superpone
al rival REAL como una capa aparte encima del sprite, así que un maniquí ya pintado ahí
significa que en el juego se verían DOS rivales superpuestos.

REGENERA LA HOJA COMPLETA (3 filas, igual formato y conteo que ya usaste: 6 / 4 / 8) de estos
9, en este orden, usando su identidad YA establecida en esta conversación:

1. Robot Estudiantx (ESCOMROBOT)
2. La Presidenta
3. Yoalli Ehécatl
4. El Charro Negro
5. La Tzitzimime
6. Policía CDMX, femenino (PoliciaFemeninoCDMX)
7. Policía CDMX, masculino (PoliciaMasculinoCDMX)
8. Paramédico Cruz Roja
9. Policía Granadero, masculino (PoliciaGranaderoMasculinoCDMX)

Mismas reglas de siempre (identidad bloqueada, escala idéntica a la hoja 01 de cada uno, fondo
#00FF00 opaco, huecos de al menos 70px entre cuadros, máximo 8 cuadros por fila, el personaje
mira a la derecha) MÁS esta regla reforzada, que es la que se rompió:

OPONENTE INVISIBLE — REGLA ABSOLUTA:
NUNCA dibujes al rival, en NINGUNA forma: ni su cuerpo, ni una silueta, ni un maniquí, ni un
muñeco de trapo, ni una versión genérica o sin rostro de una persona — NADA que se pueda leer
como "otro personaje". Los brazos y manos del atacante se cierran, giran y se extienden HACIA
EL AIRE VACÍO, como si sostuvieran y lanzaran algo invisible; el motor dibuja al rival real
encima, en su propia capa. Si una pose te tienta a dibujar "a quién le está pegando" o "a quién
está cargando en brazos", NO lo hagas: dibuja el MISMO gesto de brazos/piernas/torso pero con
las manos vacías, cerradas sobre el aire. Un cuadro de impacto en el punto de caída (polvo,
ondas, grietas en el piso, SIN cuerpo dentro) SÍ está permitido — cualquier figura con cabeza,
torso y extremidades reconocibles como persona NO.

Antes de dar cada imagen por buena, verifica ADEMÁS de lo de siempre: ningún cuadro de las 3
filas contiene una segunda figura humanoide — revisa especialmente los cuadros 4 a 7 de la
Fila 3, que es donde se coló el rival la vez pasada.
```

---

## CORRECCIÓN 3 — solo 4 personajes (cambiaron el agarre por un ataque a distancia)

> ✅ **RESUELTO Y VERIFICADO (2026-08-29d).** Los 4 se regeneraron y se revisaron uno por uno:
> sin fuego/orbes/granadas/escudo holográfico, agarre físico con las manos vacías hacia el aire,
> Policía Granadero (Hombre) conserva su escudo antimotines en las 3 filas, y la Fila 2 de
> Policía Masculino CDMX quedó en 4 cuadros exactos.
>
> ⚠️ **Esto NO era todo: ver CORRECCIÓN 4 más abajo.** La revisión visual (¿hay rival?, ¿es a
> distancia?, ¿cuántos cuadros por fila a ojo?) dio bien en los 18, pero al recortar de verdad
> con el pipeline (2026-08-30) aparecieron 3 personajes con la Fila 3 corta por UN cuadro cada
> uno — un defecto que la revisión visual no cazó porque no se nota contando a ojo si el efecto
> de impacto flota solo en vez de acompañar a una pose. **15 de 18 sí quedaron completos y ya
> están recortados + empaquetados en `app/src/main/assets/STREETFIGHTER/`** (motor probado en
> verde). Los otros 3 necesitan otra ronda — detalle en CORRECCIÓN 4.

> **Uso:** misma conversación. La corrección 2 SÍ eliminó al rival en los 9 (✅, no tocar eso
> de nuevo), pero 4 de esos 9 reinterpretaron "Derribo con Poder" como un poder A DISTANCIA
> (fuego, orbes de energía, granadas) en vez de un agarre físico — mecánicamente el movimiento
> exige estar PEGADO al rival (mismo rango que el agarre normal, 62px), así que un efecto que
> viaja por el aire se ve fuera de lugar. Regenera la HOJA COMPLETA de estos 4.

```text
En la corrección anterior, estos 4 personajes convirtieron "Derribo con Poder" en un ataque A
DISTANCIA en vez de un agarre físico: ESCOMROBOT (orbes de energía y un escudo holográfico),
Yoalli Ehécatl (bolas de fuego y un aro de fuego), Policía Masculino CDMX (lanza una granada que
explota lejos de él) y Policía Granadero Hombre (también lanza una granada, y además perdió su
escudo antimotines, que SÍ debe llevar). Este movimiento es un AGARRE: el rival está pegado a ti,
a la misma distancia que el agarre normal — no es un hechizo ni un arma arrojadiza.

REGENERA LA HOJA COMPLETA (3 filas: 6 / 4 / 8, igual que ya usaste) de estos 4, en este orden:

1. Robot Estudiantx (ESCOMROBOT)
2. Yoalli Ehécatl
3. Policía CDMX, masculino (PoliciaMasculinoCDMX)
4. Policía Granadero, masculino (PoliciaGranaderoMasculinoCDMX)

Mismas reglas de siempre (identidad bloqueada, escala idéntica a la hoja 01, fondo #00FF00
opaco, huecos de al menos 70px, máximo 8 cuadros por fila, mira a la derecha, OPONENTE
INVISIBLE — sin cuerpo/silueta/maniquí del rival, eso quedó bien y no se toca) MÁS esta regla
nueva, que es la que se rompió esta vez:

CONTACTO FÍSICO CERCANO — REGLA REFORZADA:
Este agarre es de CONTACTO DIRECTO, no un poder a distancia. El rival invisible está PEGADO al
personaje, literalmente a un brazo de distancia — la misma cercanía que el agarre normal. NO
dibujes fuego, bolas de energía, orbes, escudos holográficos, discos flotantes, granadas,
proyectiles, ni ningún efecto que viaje lejos del cuerpo: eso es el lenguaje visual de un
ataque a distancia, y este movimiento NO lo es. Las manos se cierran, giran y proyectan HACIA
EL FRENTE INMEDIATO, a la distancia de un brazo extendido, como si empujaran o lanzaran algo
que ya tienen sujeto ahí mismo — nunca algo que sale disparado hacia otra parte de la pantalla.
Aunque el personaje tenga fuego, energía u otro elemento en sus specials o su súper, este
movimiento NO es el lugar para usarlo: es un agarre, no un hechizo ni un arma.

Para Policía Granadero (Hombre) en particular: mantén su ESCUDO ANTIMOTINES en las 3 filas,
igual que en el resto de su catálogo (hojas 01-29) y que en la hoja anterior a esta corrección
— agarra con el brazo/mano LIBRE mientras sostiene el escudo con el otro; lo único que cambia
es que esa mano libre ahora se cierra sobre el AIRE VACÍO en vez de sobre un cuerpo.

Para Policía Masculino CDMX en particular: su FILA 2 (INTENTO) salió con 6 cuadros la vez
pasada — debe ser EXACTAMENTE 4, ni más ni menos.

Antes de dar cada imagen por buena, verifica ADEMÁS de lo de siempre: (1) ningún cuadro tiene
fuego, energía, orbes, escudos brillantes, granadas ni proyectiles — solo manos/brazos vacíos y,
como mucho, un cuadro de impacto en el piso (polvo/ondas, sin objeto dentro); (2) Policía
Granadero Hombre conserva su escudo en las 3 filas; (3) los conteos de cuadros son EXACTOS por
fila (6 / 4 / 8) — cuenta cada fila a mano antes de entregar, en especial la Fila 2 de Policía
Masculino CDMX.
```

---

## CORRECCIÓN 4 — solo 3 personajes (Fila 3 con 7 poses en vez de 8)

> ✅ **RESUELTO Y VERIFICADO (2026-08-30).** Los 3 se regeneraron y se revisaron uno por uno:
> Fila 3 con 8 siluetas completas, el efecto de impacto pegado a una pose real (no flotando
> solo). Recortados y empaquetados. **Con esto, los 18 personajes están completos.**

> **Uso (histórico, ya resuelto):** misma conversación. Este defecto era DISTINTO a los de las
> correcciones 2 y 3 (que eran sobre QUÉ se dibuja) — aquí lo que fallaba era el CONTEO real de
> poses de la Fila 3, algo que la revisión visual a ojo no cazó porque el cuadro de impacto, al
> estar solo en el hueco central, se leía como "un cuadro más" aunque no acompañara a ningún
> cuerpo.

```text
En estos 3 personajes, la Fila 3 (Lanzamiento Conectado) tiene solo 7 poses del personaje en
vez de 8: el cuadro de impacto en el punto de caída del rival invisible quedó FLOTANDO SOLO en
el hueco central de la fila, sin acompañar a ninguna pose del personaje, en vez de aparecer
JUNTO a la 8ª pose. Resultado: falta una pose real de personaje en esa fila.

REGENERA LA HOJA COMPLETA (3 filas: 6 / 4 / 8, igual formato que ya usaste) de estos 3, en este
orden:

1. La Presidenta
2. Paramédico Cruz Roja
3. Policía CDMX, masculino (PoliciaMasculinoCDMX)

Mismas reglas de siempre (identidad bloqueada, escala idéntica a la hoja 01, fondo #00FF00
opaco, huecos de al menos 70px, máximo 8 cuadros por fila, mira a la derecha, OPONENTE
INVISIBLE, CONTACTO FÍSICO CERCANO — todo eso ya quedó bien y no se toca) MÁS esta regla nueva:

OCHO POSES REALES EN LA FILA 3 — REGLA REFORZADA:
La Fila 3 son OCHO cuadros y los OCHO deben mostrar al personaje en una pose de lanzamiento
completa (sujetar, cargar peso, girar, proyectar, impacto, recuperación) — no siete poses más
un cuadro suelto. El cuadro de impacto (polvo/ondas en el punto de caída, sin cuerpo dentro)
va SIEMPRE junto al cuerpo del personaje en ESA MISMA pose, nunca como un cuadro aparte que
reemplaza a una pose completa. Antes de entregar, cuenta las siluetas del personaje en la Fila
3 una por una: deben ser exactamente 8, sin contar el cuadro de impacto como si fuera una de
ellas.

Antes de dar cada imagen por buena, verifica ADEMÁS de lo de siempre: cuenta a mano las 8
poses de la Fila 3 (no los "cuadros" — un cuadro con solo el efecto de impacto y sin cuerpo NO
cuenta como pose), y confirma que el efecto de impacto, si aparece, está pegado a una de esas
8 poses y no solo en un hueco vacío entre dos de ellas.
```

### Tabla de nombres canónicos (para guardar los archivos igual que el resto del catálogo)

| # | Personaje en juego | `<Personaje>` (nombre de carpeta/archivo) |
|---|---|---|
| 1 | Prankedy | `Prankedy` |
| 2 | El Señor de la Tienda | `SenorTienda` |
| 3 | Paparazzi 1 | `Paparazzi1` |
| 4 | Rey Grupero | `ReyGrupero` |
| 5 | Paparazzi 5 | `Paparazzi5` |
| 6 | Estudiante | `ESCOMBOY` |
| 7 | Estudianta | `ESCOMGIRL` |
| 8 | Robot Estudiantx | `ESCOMROBOT` |
| 9 | Yoalli Ehécatl | `YoalliEhecatl` |
| 10 | El Charro Negro | `CharroNegro` |
| 11 | La Llorona | `LaLlorona` |
| 12 | La Tzitzimime | `LaTzitzimime` |
| 13 | La Presidenta | `LaPresidenta` |
| 14 | Policía CDMX | `PoliciaFemeninoCDMX` |
| 15 | Policía CDMX (Hombre) | `PoliciaMasculinoCDMX` |
| 16 | Paramédico Cruz Roja | `ParamedicoCruzRoja` |
| 17 | Policía Granadero CDMX (Hombre) | `PoliciaGranaderoMasculinoCDMX` |
| 18 | Policía Granadero CDMX (Mujer) | `PoliciaGranaderoFemeninoCDMX` |

Guardar cada imagen como `newSFAssets/<Personaje>/<Personaje>_30_Contraataque_DerriboPoder.png`
identificando el personaje POR EL DIBUJO (y el título amarillo), no por el nombre de descarga.

⚠️ **No incluye a Lázaro, Granadero ni Paramédico** (los 3 peleadores COMPARTIDOS, arte del
mundo abierto): no tienen ninguna hoja del batch 3rd Strike (20-29) todavía, así que la hoja 30
tampoco les aplica hasta que se les dé ese batch primero.

## Al recibir la imagen (por personaje)

1. Guardarla en `newSFAssets/<Personaje>/` como `<Personaje>_30_Contraataque_DerriboPoder.png`
   (verificar por los títulos amarillos, NO por el nombre de descarga).
2. Extender `SHEETS[30]` en `tools/slice_sf_chroma_sheets.py` y `NEW_MOVE_ANIMATIONS`/
   `reference_frame_key()` en `tools/pack_sf_character.py` (ver "Código pendiente" arriba) —
   los `jsKey` son `counter`, `powerGrab`, `powerThrow` (ya existen en `SfFighterState`, no hay
   que tocar Kotlin de nuevo). ⚠️ La hoja tiene 3 BANDAS de píxeles (fila 1 Contraataque, fila 2
   Derribo-intento, fila 3 Derribo-lanzamiento) pero solo 2 GRUPOS lógicos: al definir `SHEETS[30]`
   trata las filas 2+3 como una sola secuencia continua de 12 cuadros (4 `power-grab-*` + 8
   `power-throw-*`), leyendo la fila 2 completa y luego la fila 3, no como dos grupos separados.
3. Recortar con `slice_sf_chroma_sheets.py <char>` (hoja 01 del personaje ya debe existir para
   fijar `_scale.json`) y empacar con `pack_sf_character.py <char> <Titulo>`.
4. QA visual con `tools/sf_contact_sheet.py <char>` antes de dar por bueno.
