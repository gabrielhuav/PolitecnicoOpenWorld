# Prompt para regenerar los sprite sheets en ChatGPT

> Copia el bloque de abajo en ChatGPT (modo imagen). Cambia lo que está en **[corchetes]**.
> Genera **un personaje a la vez** (sale mejor que un sheet gigante). Si al revisarlo dos
> figuras se tocan o no hay hueco claro entre frames, pídele que lo regenere con MÁS separación.

---

Genera un **sprite sheet** para un videojuego 2D *top‑down* en **pixel‑art**, estilo caricatura, del personaje: **[describe el personaje, p. ej. "policía antimotines (granadero) mexicano con casco, chaleco y escudo"]**.

**FONDO:** transparente (PNG con canal alfa). Si no puedes transparente, usa un **fondo de un solo color plano y vivo que NO aparezca en el personaje** (magenta puro `#FF00FF`), incluido en los huecos entre frames.

**LAYOUT (crítico para poder recortarlo por programa):**
- Organiza el sheet como una **cuadrícula regular**. **Cada animación va en su PROPIA FILA.**
- Dentro de cada fila, los frames van en **celdas del MISMO tamaño**, igualmente espaciadas.
- **Deja un hueco vacío CLARO entre CADA frame** (separación mínima ≈ 40 % del ancho del personaje). **Las figuras NO deben tocarse ni encimarse jamás.**
- **Un solo personaje por celda**, centrado horizontalmente, a la **misma escala** en todas las celdas y en todas las filas, con los **pies sobre la misma línea base** (alineados abajo).
- El personaje **mira hacia la DERECHA** en caminar y correr.

**NO INCLUIR (esto rompe el recorte automático):**
- ❌ Nada de **texto ni etiquetas** (no escribas "IDLE", "CORRER (8 FRAMES)", nombres, etc.).
- ❌ Nada de **logo, marca, retrato grande, ni paleta de colores**.
- ❌ Nada de **accesorios/objetos sueltos** fuera de las celdas.
- Solo los frames del personaje; el resto del lienzo, vacío/transparente.

**CONSISTENCIA:**
- Mismo diseño, ropa y colores del personaje en **todos** los frames.
- Mismo **tamaño de personaje** en todas las animaciones (que no se vea más grande en unas que en otras).
- Alta resolución por frame; contornos nítidos.

**ANIMACIONES (una fila cada una, en este orden):**
- Idle (quieto): **[N]** frames
- Caminar: **[N]** frames
- Correr: **[N]** frames
- Especial — **[describe la acción, p. ej. "golpe con el escudo"]**: **[N]** frames

Entrega el sheet final como **PNG con fondo transparente** (o magenta plano).

---

## Conteos sugeridos por personaje (para llenar las [N])

| Personaje | Idle | Caminar | Correr | Especial |
|---|---|---|---|---|
| Paparazzi #1 | 3 | 8 | 8 | tomar foto (4) |
| Paparazzi #5 | 3 | 6 | 8 | tomar foto (4) |
| Policía CDMX | 4 | 6 | 8 | disparar (5) |
| Granadero | 4 | 6 | 8 | golpe con escudo (4) |
| Paramédico | 4 | 6 | 8 | comunicar por radio (3) |

## Tips para que salga bien
- Pídeselo **por personaje** (o incluso **una fila/animación por imagen**) — los modelos respetan mejor el espaciado en imágenes más simples.
- Si quieres asegurar el corte perfecto, pide: *"pon cada frame dentro de su propia celda con un borde fino de 1 px de color cian alrededor de cada celda"* — esos bordes me sirven de guía exacta y los elimino al recortar.
- Cuando lo tengas, pásamelo (PNG) y yo lo recorto, le quito el fondo si hace falta, y lo integro como el resto.
