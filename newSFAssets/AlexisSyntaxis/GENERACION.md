# Reparación de Alexis Syntaxis — 2026-11-04

Se conserva el atlas y JSON originales de PR #158 en `atlas-pr158-original.*`.
`repair-14-poses.png` se generó con la herramienta integrada `image_gen` (habilidad
imagegen), con `transparent_background: true`, usando como referencia las siete
poses de `pr-screenshots/alexis-detail.png`, extraídas del atlas original.
El dueño autorizó regenerar al confirmar que no tiene hojas completas.

La receta `PolitecnicoOpenWorld/tools/sf_fighters/alexis_syntaxis.json` fija la rejilla
4×4, el destino de las 14 celdas y sus alturas. Las últimas dos celdas están vacías.
El recorte elimina alfa <=16 antes y después de normalizar para medir el cuerpo
visible con el mismo umbral del audit de Claude; las fuentes se conservan intactas.
Se usa interpolación premultiplicada y WebP lossless.
El resto de los cuadros se conserva, con corrección de geometría.

## Prompt exacto

Use case: identity-preserve. Production pixel-art sprite repair sheet for the 2D fighting game Politecnico Open World. Input image is reference only: the same male fighter Alexis Syntaxis, short swept brown hair, rectangular black glasses, blue suit, white shirt, blue tie, dark shoes, small handheld wired microphone. Match his existing chunky shaded arcade pixel art, proportions and colors exactly. Generate ONE transparent sprite sheet with exactly FOUR columns and FOUR rows of equal square cells, no grid lines or labels. Each isolated pose must fit comfortably inside its own cell with ample transparent margins, full head and both feet visible, no shadows on floor. Facing RIGHT in all action poses. Every pose same body scale, about 45% of cell height, soles same baseline at 85% of cell height; dramatic effects may extend to 85% cell width, NEVER touch edges. Row 1: FOUR full-body talking idle poses, subtle mouth/hand variations, microphone near mouth, relaxed standing, NO portraits/busts. Row 2: THREE successive light special poses thrusting right arm and casting glowing small blue/cyan musical notes (growing 1,2,3 notes), then ONE medium special starting musical note casting pose. Row 3: THREE medium special continuation poses casting progressively stronger cyan/blue musical notes toward right, then ONE throwing recovery pose with right arm extended and microphone cable close to fighter, NO opponent. Row 4: ONE ultimate attack finishing pose with bright golden musical staff swirling to right compactly but fully visible; ONE power throw recovery pose with right arm extended and small cyan sparks and microphone cable close to fighter, NO opponent; leave the final TWO cells empty transparent. Exactly 14 complete isolated figures, 2 empty cells. Do NOT draw enemy, mannequin, limbs of another figure, text, logo, green background, checkerboard. Real RGBA transparency.
