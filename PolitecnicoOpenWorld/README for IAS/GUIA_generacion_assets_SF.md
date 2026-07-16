# GUÍA DETALLADA · Generación MANUAL de assets del modo de pelea 1v1 (ChatGPT u otra herramienta)

> Complemento práctico de `ASSETS_STREETFIGHTER_MIGRACION.md`: qué imagen generar, UNA POR UNA,
> con nombre exacto, tamaño, descripción de pose y prompt. Orden = prioridad real.
> **Al terminar cada bloque me pasas los PNG y yo empaqueto/cableo** (packer + temas).

> **🆕 (2026-07-15) YA NO HACE FALTA generar/empaquetar para personajes con set en el mundo:**
> los 11 peleadores compartidos se arman **EN RUNTIME** (`SfSharedSheets.kt`, port Kotlin de
> estos tools) desde `SPRITES/PLAYER|NPC/` — sus sheets/JSON empaquetados y `GEN/` se
> BORRARON del APK. Esta guía sigue vigente SOLO para personajes con **ARTE PROPIO de pelea**
> (hoja de referencia → `slice_sf_reference_sheet.py` → `pack_sf_character.py`, como
> Prankedy): eso SUSTITUYE las poses aproximadas ALPHA por poses reales. Nota:
> `gen_sf_frames_from_npc.py` ganó `PLAYER:<skin>` y flag `flip` por si se quiere empaquetar
> offline de nuevo (regla 09 §12: no committear esos sheets). Ver 07.

---

## ⭐ MODO SIMPLE (2026-07-10, el que usamos) — UNA hoja por personaje

La hoja `sprites Prankedy.png` (raíz del repo externo) demostró ser el formato IDEAL: secciones
IDLE(3) · CAMINAR(6) · CORRER(8) · SALTO(4) · AGACHARSE(3) · ATAQUE(6) · DAÑO(2-3) · DERRIBO(3)
· VICTORIA(3) sobre fondo negro. El script **`tools/slice_sf_reference_sheet.py`** la rebana
AUTOMÁTICAMENTE (detecta las figuras por silueta, quita el fondo, normaliza a 256² con pies en
128,224 y mapea a las 77 poses); luego `pack_sf_character.py` arma sheet+JSON. **Prankedy ya se
regeneró así — sin generar nada a mano.**

Para CADA personaje restante el flujo del dueño es UNA sola petición a ChatGPT:
1. Subir 2 imágenes: `sprites Prankedy.png` (plantilla) + `referencia <Personaje>.png` (raíz).
2. Pegar el prompt del §"prompt del modo simple" (abajo).
3. Guardar el resultado como `sprites <Personaje>.png` en la raíz del repo y avisarme →
   yo corro `slice_sf_reference_sheet.py` + `pack_sf_character.py`.

**Prompt del modo simple:**
> Te subo 2 imágenes: (1) una hoja de sprites PLANTILLA de un juego de peleas pixel art y (2) el
> personaje [NOMBRE]. Genera EXACTAMENTE la misma hoja que la plantilla — mismas secciones, mismos
> títulos, mismo número de cuadros por sección, mismo tamaño de figuras (~190 px de alto de pie),
> mismo estilo pixel art y mismo fondo negro — pero protagonizada por el personaje de la imagen 2,
> siempre mirando a la DERECHA. En "ATAQUE" usa su objeto característico: [escoba o caja de tienda /
> cámara con flash / megáfono]. Sin marcas de agua.

*(El resto de esta guía es el modo cuadro-por-cuadro, útil solo para RETOCAR poses sueltas.)*

---

## 0. Reglas GENERALES para TODOS los cuadros de personaje

- **Lienzo:** PNG **256×256**, fondo 100% transparente.
- **Ancla:** los PIES tocan el píxel **y=224**, centrados en **x=128**. (Excepción: proyectiles,
  centrados en 128,128; y los `fall-4/5` tumbados, con el CUERPO apoyado sobre y=224.)
- **Orientación:** el personaje mira SIEMPRE a la **DERECHA** (el motor lo espeja solo).
- **Tamaño:** ~100 px de alto de pie. La cabeza/torso NUNCA cambian de tamaño entre cuadros —
  cambia la POSTURA, no la escala (el error actual del agacharse).
- **Estilo:** pixel art 16-bit de juego de peleas, contorno oscuro 1 px, paleta IDÉNTICA entre
  cuadros, sin sombra en el piso (la pone el motor), sin texto/marcas.
- **Consistencia:** genera el primer cuadro, y para los siguientes ADJUNTA ese cuadro como
  referencia en el chat ("mismo personaje, misma paleta, solo cambia la pose a …").
- **Nombres y destino:** guarda cada PNG con el nombre EXACTO indicado, en
  `app/src/main/assets/STREETFIGHTER/GEN/<personaje>/` (`prankedy`, `senortienda`,
  `paparazzi1`, `paparazzi5`, `reygrupero`).
- **Integración (por personaje):** `python tools/pack_sf_character.py <carpeta> <Titulo>`
  (p. ej. `prankedy Prankedy`) y Rebuild. El packer rearma sheet+JSON solo.

### Plantilla de prompt (rellena los [] por cuadro)

> Pixel art 16-bit de juego de peleas 2D, vista lateral. Personaje: [adjunta cuadro de
> referencia / descríbelo]. UN solo cuadro: **[POSE EXACTA]**. Lienzo 256×256, fondo
> transparente, mira a la DERECHA, ~100 px de alto, pies tocando y=224 centrados en x=128.
> Misma paleta que la referencia, contorno oscuro de 1 px, sin sombra en el piso, sin texto.

---

## 1. PRIORIDAD 1 — Arreglar las poses MALAS de PRANKEDY (17 cuadros)

Referencia: adjunta `GEN/prankedy/forwards-1.png` (su look correcto). Carpeta: `GEN/prankedy/`.

| Archivo | Pose exacta |
|---|---|
| `idle-1` | Guardia de combate en reposo: puños semilevantados frente al pecho, rodillas apenas flexionadas |
| `idle-2` | Igual, torso 2-3 px más abajo (respiración) |
| `idle-3` | Igual, punto más bajo de la respiración, hombros relajados |
| `idle-4` | Igual que idle-2 (subiendo) |
| `crouch-1` | Empieza a agacharse: rodillas dobladas a medio camino, guardia arriba |
| `crouch-2` | Media sentadilla: muslos casi horizontales, torso ligeramente adelante |
| `crouch-3` | EN CUCLILLAS profundas con guardia: cabeza a ~60 px del piso. MISMO tamaño de cabeza/torso, solo postura |
| `crouch-turn-1..3` | Voltearse EN CUCLILLAS (3 pasos, misma altura que crouch-3) |
| `jump-start-land-1` | Flexión de rodillas para impulsarse/aterrizar (como crouch-1 pero brazos abajo-atrás) |
| `hit-face-1..4` | Recibe golpe en la CARA: cabeza y hombros hacia atrás, progresivo (4 = casi recuperado), pies plantados |
| `hit-stomach-1..4` | Golpe al ESTÓMAGO: doblado hacia adelante sujetándose el abdomen, progresivo |
| `stun-3` | Aturdido: tambaleándose, ojos de espiral/estrellitas opcionales |
| `fall-1` | Nocaut: pierde el equilibrio hacia atrás, brazos al aire, un pie despegado |
| `fall-2` | Cayendo de espaldas a ~45° |
| `fall-3` | Casi horizontal en el aire |
| `fall-4` | TENDIDO boca arriba en el piso (cuerpo horizontal apoyado en y=224) |
| `fall-5` | Igual que fall-4, brazos caídos (pose final) |

*(El resto de Prankedy — caminar, saltos, volteretas, golpes, victoria, especial, proyectil — ya está bien.)*

## 2. PRIORIDAD 2 — Los 4 ALPHA: golpes propios + las mismas poses de arriba

Para `senortienda`, `paparazzi1`, `paparazzi5` y `reygrupero` (referencia: su `GEN/<x>/idle-1.png`
actual para el look, aunque la pose sea mala). Por personaje, además de la TABLA del §1 (los
mismos 17), genera sus **13 cuadros de ataque** (hoy reciclan su animación "Special" y se ve
repetido):

| Archivo | Pose exacta |
|---|---|
| `light-punch-1` | Preparación de jab: puño atrás junto a la mejilla |
| `light-punch-2` | Jab: brazo delantero extendido a la altura de la cara |
| `med-punch-1..3` | Directo medio: carga → extensión completa → recogida |
| `heavy-punch-1` | Puñetazo fuerte en máxima extensión (torso rotado, peso adelante) |
| `light-kick-1..2` | Patada baja rápida: rodilla arriba → extensión a la espinilla |
| `med-kick-1` | Patada frontal media en extensión (a la altura del pecho) |
| `heavy-kick-1..5` | Roundhouse: carga → giro → pierna extendida arriba (3) → recogida → guardia |
| `victory-1..4` | Celebración CON SU PERSONALIDAD (tienda: alza una caja; paparazzi: se toma selfie; grupero: micrófono al aire) |

Ideas de identidad por personaje (para los prompts): **Señor de la Tienda** = mandil, golpea con
escoba/caja; **Paparazzi 1 y 5** = cámara colgada, flashazo en el especial; **Rey Grupero** =
sombrero y abrigo de leopardo, megáfono. *(Si algún día quieren proyectil propio: 2 cuadros
`proj-fly-1/2` + 3 `proj-hit-1..3` centrados en 128,128; si no existen, el motor usa el del tema.)*

## 3. ESCENARIO POW "Explanada ESCOM" (7 imágenes → me las pasas y yo armo `POW_THEME`)

Carpeta sugerida: `GEN/powstage/`. Pixel art 16-bit, paleta de atardecer, SIN personajes.

| Archivo | Tamaño EXACTO | Contenido |
|---|---|---|
| `fondo.png` | 768×176 (opaco) | Cielo de atardecer + skyline lejano de Zacatenco/IPN |
| `edificio.png` | 521×180 (transparente) | Fachada de la ESCOM con su letrero, plano medio |
| `piso.png` | 896×56 | Adoquín/concreto de explanada (franja vista casi de frente) |
| `piso-borde.png` | 896×16 | Borde inferior del piso, más oscuro |
| `bandera-1/2/3.png` | 40×40 ×3 | Bandera de MÉXICO ondeando (3 cuadros de ciclo) |
| `jardinera.png` | 31×24 | Jardinera/maceta (decoración, 2 se dibujan en primer plano) |
| `bote.png` | 21×16 | Bote de basura chico (2 al fondo) |
| `puesto.png` | 151×96 | Puestito de comida/lonchería lateral (sustituye a los barriles) |

## 4. HUD POW (piezas sueltas → yo las ensamblo en `powhud.png`)

Pixel art plano estilo arcade. Carpeta `GEN/powhud/`:

| Archivo | Tamaño | Contenido |
|---|---|---|
| `barra-vida.png` | 145×11 | Marco de barra de vida (relleno amarillo; el daño rojo lo pinta el motor) |
| `digitos-tiempo.png` | 160×16 | Los 10 dígitos 0-9 en fila, cada uno en celda de 16×16 (blanco) |
| `digitos-tiempo-flash.png` | 160×16 | Igual en color de alerta (rojo/naranja) |
| `digitos-score.png` | 120×10 | 0-9 en celdas de 12×10 |
| `letra-p.png` | 10×10 | Letra "P" (para P1/P2) |
| `ko.png` + `ko-alt.png` | 32×14 ×2 | Icono "K.O." en 2 colores (parpadeo) |
| `tag-<personaje>.png` | ~30×9 c/u | Nombre corto por peleador: PRANKEDY, TIENDA, PAPZ1, PAPZ5, GRUPERO (+ opcional RYU/KEN) |
| `wins-<personaje>.png` | 70×9 c/u | Texto "<NOMBRE> WINS" por peleador |

**NO generes:** la sombra (43×9) ni los splashes de impacto — esos los hago YO por código
(elipse y estrellitas/confeti programáticos) cuando armemos el tema.

## 5. SONIDOS (12 .ogg, libres de copyright)

Cortos (<1 s salvo música). Se pueden grabar o hacer con jsfxr/ChipTone/Audacity. Mismos nombres
de clave (van en `STREETFIGHTER/SOUNDS/` del tema POW): `light/medium/heavy-attack` (silbido de
golpe al aire ×3 intensidades), `light/medium/heavy-punch-hit` y `light/medium/heavy-kick-hit`
(impactos), `land` (caer al piso), `hadouken` (el grito/efecto del especial — para Prankedy puede
ser risa + spray), y la MÚSICA de pelea en loop (~1-2 min, estilo chiptune/banda).

## 6. Orden recomendado y cierre

1. §1 (Prankedy queda ✅ completo) → me avisas → `pack_sf_character.py prankedy Prankedy`.
2. §2 personaje por personaje (cada uno se integra igual, sin esperar a los demás).
3. §3+§4 juntos → yo armo `powstage.png`/`powhud.png`, el `POW_THEME` (con sombra y splashes
   programáticos) y los `winnerRows`/`nameTags` nuevos.
4. §5 al final → se cambian rutas en el tema.
   Cuando §3-§5 estén, el modo queda **100% libre de assets de Street Fighter** y se puede
   quitar Ryu/Ken del roster (o dejarlos solo en dev).
