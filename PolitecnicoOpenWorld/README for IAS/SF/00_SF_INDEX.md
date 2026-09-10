# 🥊 MODO PELEAS — "TITULACIÓN POR COMBATE" · Índice

> Este es el índice del **modo de peleas**. El otro modo del juego (mundo libre POW) vive
> en `../MUNDO/`. Lo compartido por ambos (arquitectura, capa de datos, convenciones) está
> en la raíz de `README for IAS/`.
>
> ⚠️ **En el CÓDIGO el modo se sigue llamando `street_fighter` / `Sf*`** (ids, paquetes,
> clases). "Titulación por Combate" es solo el nombre de cara al jugador. No renombres.
>
> 🆕 **(2026-09-05) El modo se llamaba "Huelum vs. Goya".** El nombre de cara al jugador vive en
> el string `menu_street_fighter` (los 4 archivos: ES/EN × `:app`/`:shared`), NO en el código.
> Si encuentras "Huelum vs. Goya" en `_ARCHIVO/`, en el changelog del README público o en el PR
> #136, **está bien así**: son registros históricos de cuando el modo se llamaba de esa forma y
> no se reescriben. El único identificador de código que llevaba el nombre comercial era
> `PowModo.HUELUM_VS_GOYA`, ahora `PowModo.STREET_FIGHTER` — se renombró precisamente para que el
> siguiente cambio de nombre no toque código.

## Por dónde empezar

1. `../09_CONVENTIONS_GOTCHAS.md` — convenciones OBLIGATORIAS (MVVM, estado inmutable
   `_state.update { it.copy(...) }`, strings ES+EN con paridad, CRLF en `.kt`).
2. `../_SESION_ACTUAL.md` — **qué se está haciendo AHORA MISMO** y qué sigue.
3. `DISENO_ARCADE_SF_POW.md` — decisiones cerradas del modo, por fecha. El documento vivo.
4. **¿Vas a agregar un movimiento o un personaje nuevo?** → `PIPELINE_agregar_movimientos_y_personajes.md`
   — la receta completa, con quién hace qué (Claude / IA de imagen / dueño).
5. El doc concreto de lo que vayas a tocar (tablas de abajo).

## Mapa de documentos

### Diseño y estado del modo

| Archivo | Contenido |
|---|---|
| `DISENO_ARCADE_SF_POW.md` | **El doc central.** Roster, escalera del arcade, moveset 3rd Strike, combos, tutorial, fatality, y el registro de cambios por fecha. |
| `SF_STAGES_MAPS_UNLOCK.md` | Escenarios y cómo se desbloquean. |
| `QA_SF_STAGES_2026-07-18.md` | QA de escenarios. |
| `AUDIT_SF_MULTIPLAYER.md` | Auditoría del 1v1 en red (WS y BT/LAN). Barrera "ambos listos" + endurecimiento LAN (2026-07-25). |
| `DISENO_MATCHMAKING_P2P.md` | 🆕 Diseño (2026-07-25): Render→matchmaking + host real. Recomienda Cloudflare Workers/DO; descarta el túnel en el teléfono (Play). |

### Assets gráficos (sprites)

| Archivo | Contenido |
|---|---|
| `FLUJO_ASSETS_SF.md` | **El pipeline completo** de hoja croma → atlas empaquetado. |
| `GUIA_regeneracion_sprites_croma.md` | Guía por peleador: qué hoja produce qué animación, y el histórico de cada regeneración. |
| `ASSETS_STREETFIGHTER_MIGRACION.md` | Migración de los assets originales a los propios de POW. |

### Audio y voces

| Archivo | Contenido |
|---|---|
| `AUDIO_INVENTARIO_SF.md` | Inventario de audio del modo. |
| `SF_SPECIAL_VOICES_SFX.md` | Voces de special y SFX. |
| `AUDIT_VOCES_SUBTITULOS.md` | **Generado**, no editar a mano: audit por clip (duración, LUFS, draft, subtítulo). |
| `PROMPT_traspaso_audio_subtitulos.md` | Traspaso listo para pegar a otra IA con el trabajo de audio pendiente. |

### Traspasos

| Archivo | Contenido |
|---|---|
| `PIPELINE_agregar_movimientos_y_personajes.md` | 🆕 (2026-08-30) **Receta reutilizable** para agregar un movimiento nuevo a los 18 peleadores o un peleador nuevo (19º): fases, reparto Claude/IA de imagen/dueño, gotchas ya conocidos. |
| `PROMPT_SOL56_TANDAS_NUEVAS.md` | Tandas de hojas nuevas (hojas 20-29, moveset 3rd Strike). |
| `PROMPT_hoja30_contraataque_derribo.md` | ✅ (2026-08-29/30) Prompt de la hoja 30: Contraataque + Derribo con Poder. 18/18 completos: motor, arte (4 rondas de corrección) y pipeline. |

## Herramientas del modo (en `tools/`)

| Herramienta | Para qué | Coste |
|---|---|---|
| `sf_audit_frames_auto.py --sheets` | **Audit AUTOMÁTICO** de los atlas ya empaquetados. Señala celdas con varias siluetas, cuadros duplicados, vacías, saltos de escala y figuras cortadas. Con `--sheets` dibuja `_PROBLEMAS_<char>.png` con SOLO lo sospechoso. | segundos |
| `sf_audit_sheets.py [--only <char>]` | Hojas de contacto COMPLETAS: todas las animaciones de un peleador. Para revisión humana exhaustiva. | ~1 min |
| `sf_contact_sheet.py <char>` | Hoja rápida: 1 cuadro por animación. | segundos |
| `sf_voice_subtitle_audit.py` | Audit de voces y subtítulos (SOLO LECTURA). | ~1 min |
| `sf_apply_subtitle_fixes.py` | Aplica al JSON las correcciones del dueño desde el CSV. | segundos |
| `slice_sf_chroma_sheets.py <hoja> <char> --list` | **Dry-run** del recorte: enseña los blobs sin escribir. | segundos |
| `pack_sf_character.py <char> <Titulo> --gen <STAGING>` | Empaqueta atlas + JSON. | ~30 s |
| `fix_llorona_projectile.py --gen <STAGING>` | Re-extrae los `proj-*` de La Llorona. | segundos |

**Salida de las hojas:** `tools/_audit_sheets/`
**MP3 para escuchar:** `tools/_audio_review/` (⚠️ en `.gitignore`, no viaja por git)

## 📐 EL ESTÁNDAR DE UN CUADRO (contrato del pipeline)

Todo cuadro que entra al atlas cumple esto, lo produzca el slicer o lo corrija alguien a
mano. Romper cualquiera de estos puntos hace que el personaje cambie de tamaño o "flote"
al pasar de una animación a otra.

| Propiedad | Valor | Por qué |
|---|---|---|
| Lienzo | **256 × 256 px**, RGBA transparente | Celda fija del atlas |
| Línea de pies | **y = 224** | Todos los peleadores pisan el mismo suelo |
| Centro horizontal | **x = 128** | Sin esto el personaje "salta" de lado entre cuadros |
| Altura de pie | **100 px** exactos | La fija la hoja 01 en `GEN/<char>/_scale.json`; el packer la verifica y avisa (`Tamano SF idle: rango 100-100px OK`) |
| Origen | `[128, 224]` en el JSON | Proyectiles y efectos usan `[128, 128]` (centrados) |
| Alfa | De la máscara **CRUDA** del croma | Evita verde atrapado dentro de la figura |

**Hoja fuente:** PNG (nunca JPG) con croma **`#00FF00`**. El detector acepta
`g > 170 && r < 140 && b < 140`, con tolerancia para el antialias.

### ⚠️ Si corriges una pose a mano

1. Trabaja sobre el recorte que da `tools/sf_extract_sheet_region.py` (trae el verde original).
2. Separa las poses **en horizontal** y rellena el hueco con **ese mismo verde**.
3. Reimporta con `tools/sf_import_fixed_pose.py <char> <archivo> <clave> [<clave>...]`:
   aplica escala, centrado y línea de pies solo. Varias claves = la misma pose ocupa
   varios cuadros (útil cuando dos "poses" eran en realidad un único asset).
4. **Cuidado con las auras circulares y los degradados:** su borde suavizado se queda
   FUERA del umbral del croma y sobrevive como halo verde. La limpieza automática solo
   cubre 2 px de contorno. Si la pose tiene un aura grande, pinta el fondo con verde
   PLANO (sin degradado) hasta tocar la figura.

## Las reglas que más caro han salido

1. **NUNCA re-recortes hojas que ya están bien.** Una sesión re-recortó las 520 hojas de los
   18 peleadores "por si acaso" y metió una regresión que hubo que revertir con `git checkout`.
   Recorta SOLO la hoja que falla, verifica con `--list` y con la hoja de contacto, y **solo
   entonces** empaqueta.
2. **La hoja 12 es especial** (rejilla 4×3, fila 3 = efectos del proyectil). Si la tocas, hay
   que re-aplicar `tools/fix_llorona_projectile.py`.
3. **`SHEETS` en `slice_sf_chroma_sheets.py` lo comparten los 18 peleadores.** Si una hoja
   concreta trae otra cantidad de poses, se anota en `SHEET_OVERRIDES` por `(personaje, hoja)`;
   tocar la tabla global rompe a los demás.
4. **Antes de dar por bueno un cambio de recorte**, corre el audit automático y compara contra
   el slicer commiteado: un cambio de umbral puede arreglar a uno y romper a otros diecisiete.
5. **La Llorona es el caso difícil**: su arte tiene efectos que el croma separa del cuerpo.
   Verificación visual obligatoria cuadro a cuadro.
6. **🆕 (2026-08-29) La IA de imagen dibuja al rival aunque el prompt diga "oponente invisible"
   explícitamente.** En la hoja 30 (Contraataque/Derribo con Poder), 9 de 18 personajes pintaron
   un maniquí, silueta o cuerpo siendo agarrado/lanzado en el cuadro de "conecta" — rompe el
   contrato de que el motor superpone al rival real como capa aparte. Pasó con la regla YA escrita
   en el prompt ("Las manos sujetan un espacio vacío coherente"); solo se corrigió reforzándola
   con ejemplos concretos de qué NO dibujar ("ni maniquí, ni muñeco de trapo, ni silueta") y
   pidiendo explícitamente verificar los cuadros 4-7 de cada fila de agarre. **Regla: en CUALQUIER
   hoja con agarre/lanzamiento, revisa VISUALMENTE los 18 personajes uno por uno — el formato
   (dimensiones, fondo, conteo de cuadros) puede estar perfecto y aun así tener un rival pintado.**
   Detalle y el prompt de corrección en `PROMPT_hoja30_contraataque_derribo.md` §"CORRECCIÓN 2".
7. **🆕 (2026-08-30) La caché GEN real NO vive en la ruta por defecto de los scripts.** El
   `--gen` por defecto de `slice_sf_chroma_sheets.py`/`pack_sf_character.py` apunta a
   `app/src/main/assets/STREETFIGHTER/GEN` (que no debe existir ahí — viajaría al APK). La
   caché de verdad, commiteada a git, vive en
   `newSFAssets/GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio/` (ver
   `FLUJO_ASSETS_SF.md` §0). Sin pasar `--gen` explícito ahí, `slice_sf_chroma_sheets.py`
   escribe una caché nueva a medias (sin las hojas 02-29) en la ruta equivocada, y
   `pack_sf_character.py` falla porque **reconstruye el personaje COMPLETO desde el GEN
   indicado** (exige las 77 claves de `sf_template.json`, no fusiona con el JSON ya existente).
8. **🆕 (2026-08-30) Un grupo de 2 filas en `SHEETS[N]` (una sub-animación por fila, p. ej.
   4+8) rompe 2 asunciones de una sola fila en `slice_sf_chroma_sheets.py`.** `merge_fragments`
   descartaba la separación por filas si CUALQUIER fila (incluida una banda de 1 blob suelto,
   como el efecto de impacto) tenía menos de 3 items — fusionaba las 2 filas reales entre sí.
   Y `maybe_split` reordenaba TODO por X puro aunque no partiera nada, intercalando las 2 filas
   y rompiendo el mapeo cuadro→nombre. Ambos corregidos (contar solo filas ≥3 para decidir si
   hay 2+ filas reales, y no reordenar si el conteo ya es el esperado). Si se agrega otra hoja
   con este mismo patrón (2 sub-animaciones en 2 filas de un mismo grupo), verificar con
   `--list` que el conteo Y el orden salen bien antes de empacar — un conteo correcto no
   garantiza que el orden lo sea.
9. **🆕 (2026-08-30) `place_sf()`/`place_world()` escalaban sin premultiplicar alfa** — reporte
   del dueño: "el poder de La Llorona se ve como una bola negra". `Image.resize(..., LANCZOS)`
   interpola RGB y alfa por separado; en el borde semitransparente de un efecto, el RGB de los
   píxeles totalmente transparentes es basura sin limpiar (croma verde de origen), así que se
   mezcla hacia DENTRO del borde. En un efecto CLARO no se nota; en uno OSCURO y poco saturado
   (La Llorona: Especial Pesado hoja 12; La Tzitzimime: bonusPower5, hoja bonus Grok) el borde
   sale más oscuro/desaturado de lo que el arte de origen tenía — se lee como un bulto negro
   plano en vez del brillo real del borde. **El núcleo opaco del efecto no cambia** (ese color sí
   es el que el artista dibujó) — si el núcleo mismo es casi negro por diseño, el fix de brillo
   no alcanza para que se vea como "poder"; hace falta regenerar ese efecto con más color.
   Corregido con `resize_premultiplied()` en `slice_sf_chroma_sheets.py` (usado también por
   `pack_sf_character.py`'s `scale_around_origin` — no, ese tiene su propio `.resize()` sin
   corregir todavía — y por `tools/slice_sf_bonus_powers.py`, que importa `place_sf` directo).
   Verificado SIN regresión: un cuadro normal (`idle-1`) sale byte-idéntico antes/después.
   **Antes de re-recortar por este fix**, confirma que el efecto es genuinamente oscuro Y que
   el defecto se ve en pantalla — la mayoría de los efectos (colores vivos: cian, rosa, rojo,
   naranja) no lo necesitan, y "recorta todo por si acaso" ya causó una regresión real (regla 1).
10. **🆕 (2026-08-30) Un cuadro que combina "personaje + efecto ya separado de él" en UNA celda
    ancha rompe el recorte, aunque el conteo final salga correcto.** Pasó en la hoja 12 de La
    Llorona (Special Heavy): el cuadro 4 dibujaba a ella y el remolino ya lejos, en la misma
    celda. El recorte automático partió esa celda en 2 cuadros de animación para completar el
    conteo esperado (5) — y uno de los dos quedó CON SOLO EL EFECTO, sin ella. El juego, en ese
    instante de la animación, mostraba "una bola negra" en vez del personaje. El conteo de
    cuadros (5/5) salía perfecto, así que esto NO lo detecta ninguna verificación automática del
    slicer — solo mirar cada cuadro resultante y confirmar que el personaje sigue reconocible.
    **Regla: si una pose muestra al personaje soltando/lanzando algo que ya viaja lejos de él
    dentro de la MISMA celda, compara contra una animación hermana de la misma familia (aquí:
    Special Light/Medium, hoja 11) para confirmar que el personaje debe seguir visible en ESE
    momento — si la hermana lo mantiene visible y esta hoja no, es un defecto de composición del
    arte, no de recorte, y hay que regenerar esa celda con el personaje y el efecto MÁS CERCA
    (o pedir un cuadro extra parejo con los demás, como se hizo aquí: 1 celda ancha → 2 celdas).**
    El MISMO defecto reapareció en Special Medium (misma hoja 11, cuadro 5) — no asumas que
    corregir un movimiento significa que los demás de la misma hoja están bien; revísalos todos.
11. **🆕 (2026-08-30) Audit automático para la regla 10 (no hay que mirar las 18 hojas a mano).**
    Compara el área de píxeles opacos (`alpha > 30`) de cada cuadro de
    special1Light/Medium/Heavy, superArt y el resto del moveset (bonusPower aparte, ver abajo)
    contra el promedio de los cuadros de `idle` del mismo personaje; un cuadro con menos del
    ~15 % de esa área, cuando la animación tiene 3+ cuadros, casi seguro le falta el cuerpo.
    Excluir `ko`/`thrown`/`getUp` (poses legítimamente muy chicas) y `bonusPower*` (tienen su
    propio patrón de fundido, hay que revisarlos aparte con un umbral más estricto y solo marcar
    caídas A MITAD de secuencia, no al inicio/final). Corrido el 2026-08-30 contra los 18: dio
    2 falsos positivos en `superArt` (poses de recuperación agachadas, genuinamente chicas — se
    descartan mirando el cuadro) y 1 verdadero positivo (`special-medium-3` de La Llorona, ver
    regla 12). Repetir este audit cada vez que se sospeche este defecto en un personaje nuevo, en
    vez de revisar las hojas fuente a ojo una por una.
12. **🆕 (2026-08-30) Un umbral de fusión "al filo" (`merge_fragments`) puede dejar un efecto
    completo fuera del cuadro final, aunque el conteo total no dispare ningún AVISO fatal.** En
    `special-medium-3` de La Llorona, su efecto (146px) mide apenas más que el 60 % del ancho
    típico de la fila (~141px) — falla por muy poco el criterio "fragmento angosto" de
    `merge_fragments` y queda como su propia "pose" en vez de fusionarse con el cuerpo. Eso deja
    6 blobs para 5 cuadros esperados; `pick()` silenciosamente DESCARTA uno (hoy, el efecto) para
    cumplir el contrato, y el cuadro final muestra el cuerpo SIN su brillo. El slicer imprime
    "AVISO: conteo distinto al esperado" pero no aborta — ese aviso es la única pista, y hay que
    tratarlo como sospechoso de ESTE patrón, no solo del patrón de la regla 3 (hoja con otra
    cantidad de poses). Preexistente, encontrado durante el audit de la regla 11.

    ✅ **CORREGIDO (2026-08-30), en código, no en arte.** Nuevo dict `FORCE_EXACT_MERGE` en
    `slice_sf_chroma_sheets.py`: cuando `(personaje, hoja)` está en esa lista, `merge_fragments`
    fusiona el par de blobs ADYACENTE con el hueco más chico (mayor solapamiento primero) hasta
    llegar exacto a `n_expected`, en vez de dejar que `pick()` descarte uno en silencio. Activado
    HOY solo para `("lallorona", 11)` — para cualquier otro `(char, hoja)` el parámetro
    `force_exact` nace en `False` y el código nuevo no se ejecuta (cero cambio de comportamiento
    para los otros 17, verificado corriendo `escomboy`/hoja 11 y `prankedy`/hoja 12 antes y
    después: mismos conteos). **No agregues un `(char, hoja)` a esta lista sin antes confirmar
    que la fila de verdad tiene UN blob de más de lo esperado** (mira el `--list`) **y que el par
    más cercano es el fragmento y no dos poses reales** — fusionar dos poses reales por error las
    convierte en un cuadro Frankenstein con dos personajes encimados.
