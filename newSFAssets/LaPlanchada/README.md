# La Planchada: fuentes y construcción reproducible

El arte de este personaje se genera desde cero con `imagegen` integrado. El diseño
es una enfermera espectral del IMSS, con uniforme blanco y elementos verdes. Las
hojas fuente RGBA conservan el arte original completo de la enfermera espectral.
No reutilizan píxeles de otro peleador. `moveset.json` reutiliza únicamente el contrato de datos
(cajas, tiempos y eventos) del moveset dedicado de Alexis Syntaxis.

La receta `PolitecnicoOpenWorld/tools/sf_fighters/laplanchada.json` asigna cada uno
de los 258 cuadros a una celda fuente distinta. Las hojas tienen 8 columnas y 6
filas. Las filas describen animaciones continuas de 8 muestras; la receta selecciona
las muestras requeridas por el motor sin repetir una celda como otro cuadro.

| Hoja | Filas de arriba abajo |
|---|---|
| 01 | idle, forwards, backwards, run, idle-relaxed, talk |
| 02 | idle-turn, crouch, crouch-turn, inicio/aterrizaje, jump-up, jump-roll |
| 03 | jump-back, light-punch, med-punch, heavy-punch, light-kick, med-kick |
| 04 | heavy-kick, hit-face, hit-stomach, stun, fall, victory |
| 05 | special-light, special-medium, special, proj-fly, proj-hit, super |
| 06 | dash, backdash, block-high, block-low, parry-high, parry-low |
| 07 | crouch-punch, crouch-kick, crouch-hp, sweep, air-punch, air-kick |
| 08 | long-kick, overhead, grab, throw, taunt, thrown |
| 09 | getup, hurt-crouch, counter, power-grab, power-throw, vacía |

El origen de cada celda final de combate es `[128,224]` dentro de un lienzo 256²;
los proyectiles usan `[128,128]`. El cuerpo erguido mide 100 px. Las alturas y anchos
de poses dobladas, aéreas o tumbadas están calibrados en la receta; no se estira un
KO a 100 px de alto. `grid-sheets` rechaza fuentes opacas, celdas incompletas, bordes
cortados, cuadros faltantes, celdas compartidas y poses sin calibración explícita. Los rectángulos `frame_rects`
recuperan figuras que cruzan la cuadrícula nominal; las semillas `frame_components`
extraen todas sus islas alfa, incluida el aura desconectada, sin arrastrar píxeles
de poses vecinas. Los PNG originales permanecen intactos. `source-qa.json` registra
hashes, límites y calibración de cada cuadro.

## Reproducir

Desde la raíz del repositorio:

```powershell
python PolitecnicoOpenWorld/tools/build_la_planchada.py --output .codex-tmp/la-planchada
```

El resultado en staging incluye:

- `IMAGES/LaPlanchada.webp`: atlas único de combate, WebP lossless.
- `DATA/laplanchada.json`: 258 cuadros y 58 animaciones, incluidos los estados completos.
- `SPRITES/NPC/LaPlanchada/`: 25 recortes derivados de ese mismo atlas para el loader
  existente del mundo abierto. No son fuentes independientes: se regeneran siempre.
- `qa/`: contacto de todas las poses e informes de geometría de combate/mundo.

Los recortes del mundo usan lienzo 512², cuerpo erguido de 360 px, pies y=456 y centro x=256.
Todas las poses se amplían por el mismo factor 3.6 respecto a combate: las poses
agachadas, de correr y especiales conservan su escala anatómica. Se derivan:
6 Idle, 6 Walk, 8 Run y 5 Special. No agregan sonidos: La Planchada comparte los clips
existentes de La Llorona en el registro de voces del juego.

Revisar cada pose y sus vecinas en el contacto antes de instalar; la geometría por
sí sola no verifica continuidad, identidad ni que no aparezca un rival dibujado.
Después de esa revisión:

```powershell
python PolitecnicoOpenWorld/tools/build_la_planchada.py --output .codex-tmp/la-planchada --install
python PolitecnicoOpenWorld/tools/sf_fighter_pipeline.py check PolitecnicoOpenWorld/tools/sf_fighters/laplanchada.json
python -m unittest discover -s PolitecnicoOpenWorld/tools/tests -p test_sf_fighter_pipeline.py
```

El gate `SF Fighter Assets` reconstruye el atlas con la receta y compara sus píxeles
y JSON con producción. Las pruebas Android y la comprobación en dispositivo siguen
siendo necesarias para validar selección, voces, transiciones y escala en combate.
