# PIPELINE · Agregar un movimiento nuevo o un personaje nuevo (2026-08-30)

> **Origen de este doc:** generaliza el proceso completo que se usó para agregar CONTRAATAQUE +
> DERRIBO CON PODER (hoja 30, 18 personajes) — diseño, motor, arte, 4 rondas de corrección,
> pipeline de recorte/empaquetado y verificación. Detalle línea a línea de ese caso concreto en
> `PROMPT_hoja30_contraataque_derribo.md`; este doc es la RECETA reutilizable para la próxima vez.

## Antes de empezar

Lee, en este orden: `../09_CONVENTIONS_GOTCHAS.md` (convenciones obligatorias), `00_SF_INDEX.md`
("reglas que más caro han salido" — todas se descubrieron a golpes, no las repitas), y
`DISENO_ARCADE_SF_POW.md` (estado actual del roster y el moveset). Si el trabajo toca las dos
plataformas, `../11_SEPARACION_IOS_ANDROID.md` también.

## ¿Cuál de los dos flujos necesitas?

| Si quieres... | Usa | Alcance |
|---|---|---|
| Un movimiento nuevo para los 18 peleadores dedicados que YA existen | **Flujo A** (abajo) | 1 hoja nueva × 18, motor nuevo, pipeline extendido |
| Un peleador nuevo (19º) | **Flujo B** (abajo) | Las ~30 hojas completas × 1, más su entrada en el roster de Kotlin |

## Quién hace qué (el reparto que de verdad funcionó)

| Rol | Hace | No hace |
|---|---|---|
| **Claude (IA de código)** | Diseña la mecánica ANTES del arte, implementa el motor Kotlin, escribe el prompt de arte, revisa CADA imagen que vuelve, escribe los prompts de corrección dirigidos, extiende el pipeline Python, corre recorte+empaquetado+QA+verificación, actualiza los docs. | Generar imágenes (no tiene esa herramienta) ni decidir solo las preguntas de diseño abiertas (mecánica exacta, costo de recurso, etc. — esas se le preguntan al dueño). |
| **ChatGPT / IA de imagen (Sol 5.6 u otra)** | Genera las hojas de sprites a partir del prompt exacto que da Claude, una hoja/tanda a la vez. | Decidir diseño, tocar código, decidir si una hoja "pasó" el QA (eso lo revisa Claude/el dueño visualmente, no la propia IA de imagen que la dibujó). |
| **Dueño (Gabriel)** | Corre la conversación con la IA de imagen (Claude no tiene acceso), pega el prompt, adjunta la hoja de referencia, sube el resultado; responde las preguntas de diseño abiertas; aprueba pasos riesgosos (sobrescribir assets de producción, commits, push). | Escribir el prompt de arte a mano (Claude ya lo deja listo para copiar/pegar) ni revisar el pipeline Python. |

---

## FLUJO A — Movimiento nuevo para los 18 peleadores existentes

### Fase 1 — Diseño (ANTES de tocar arte) · Claude, con input del dueño

**"Empieza por el diseño, no por el arte."** Si el arte ya existe pero no se diseñó primero
(pasó con la hoja 30 original: 18 hojas 8+8 generadas sin diseño previo), NO lo uses como punto
de partida — diseña la mecánica desde cero y regenera si hace falta. No encajes el diseño al
arte; encaja el arte al diseño.

Para cada movimiento nuevo, fija por escrito (y pregúntale al dueño lo que sea una decisión de
producto, no técnica — usa preguntas concretas de opción múltiple, no abiertas):

1. **Estado(s) nuevo(s)** en `SfFighterState` — nombre, y si el pago (derribo, aturdimiento,
   etc.) puede REUTILIZAR un estado que ya existe (p. ej. `THROW`/`THROWN`/`GET_UP` para
   cualquier variante de agarre) en vez de inventar arte nueva para eso.
2. **Input**: ¿cabe en un botón que ya existe, o necesita uno dedicado? (mirar qué paleta de
   colores/slots ya están reservados en `StreetFighterScreen.kt` antes de inventar uno nuevo).
3. **Desde qué estados se puede entrar** (`SfStateMachine.VALID_FROM`) — de pie, agachado, en el
   aire, ¿o varias variantes?
4. **Qué pasa si conecta** vs. **qué pasa si falla** — daño, empuje, si deja a alguien derribado,
   si consume recurso (medidor) y cuándo se gasta (al intentar vs. solo si conecta).
5. **Qué lo interrumpe** (¿entra a `SF_HURT_STATES`? ¿tiene hitbox propia y entra a
   `SF_NEW_ATTACK_STATES`?).
6. **Uso de la IA** — ¿en qué situación lo elige la CPU?, ¿con qué probabilidad relativa frente a
   sus alternativas ya existentes (parry, agarre normal, súper)?, ¿hay que EXCLUIRLO
   explícitamente de algún set existente para no volver tonta a la IA? (pasó con Contraataque:
   se excluyó a propósito de `cpuPunishStates` porque ese set no sabe si la ventana ya expiró).
7. **Cuadros de animación por estado** — este número sale de comparar con movimientos ya
   existentes de la misma familia (parry, agarre, súper), no se inventa. Este es el número que
   define la hoja de arte — no al revés.

Verifica el balance: ¿el movimiento nuevo hace obsoleto a uno que ya existe? Si sí, dale una
razón para seguir vivo (más barato, menos arriesgado, diferente alcance/telegraph).

### Fase 2 — Motor Kotlin · Claude

Con el diseño fijo, el motor se puede escribir, compilar y testear COMPLETO sin que exista arte
todavía: todo estado nuevo queda `hasAnim`-gated (igual que TODO el moveset 3rd Strike), así que
mientras no haya arte, el botón/estado nuevo simplemente no hace nada — cero regresión visual o
jugable mientras tanto. Archivos típicos que se tocan (confirmar los nombres exactos contra el
código actual, pueden moverse):

| Archivo (`:shared` salvo que se diga otra cosa) | Qué se le agrega |
|---|---|
| `domain/models/streetfighter/SfModels.kt` | Estado(s) nuevo(s) en `SfFighterState` (**siempre AL FINAL del enum** — viaja por red como `enum.name`, ver `09_CONVENTIONS_GOTCHAS.md`), sets `SF_NEW_MOVE_STATES`/`SF_NEW_ATTACK_STATES`/`SF_HURT_STATES`, campos nuevos en `SfInput`, constantes en `SfConstants`. |
| `domain/models/streetfighter/SfStateMachine.kt` | Entradas en `VALID_FROM` e `IDLE_RECOVERY_FROM`. |
| `domain/models/streetfighter/SfDamage.kt` | Entrada en `ATTACK_META` (sonido) y en `forAttack()` (daño). |
| `features/streetfighter/viewmodel/StreetFighterMaquinaEstados.kt` | Rama(s) nueva(s) en el `when` exhaustivo de `runStateHandler`; función `tryX()` si el movimiento tiene condición de entrada (medidor, rango). |
| `features/streetfighter/viewmodel/StreetFighterCombate.kt` | Función `applyX()` (mirror de `applyThrow`/`applyGrab` si reutiliza esa familia) llamada desde `applyAttackHit`. |
| `features/streetfighter/viewmodel/StreetFighterCpuAi.kt` | Rama de decisión en `smartCpuDecision`/`cpuNewMove`, gateada por `hasAnim`. |
| `features/streetfighter/data/SfCombos.kt` | Caso nuevo en `SfComboAction`. |
| `features/streetfighter/viewmodel/StreetFighterTutorial.kt` | Caso nuevo en `actionLabel()`. |
| `features/streetfighter/viewmodel/StreetFighterViewModel.kt` | `pendingX`/`onXPressed()`, incluido en `buildPlayerInput` y en los resets; `playerHasXMove()` para gatear la UI por ESTA hoja, no por todo el moveset. |
| `features/streetfighter/ui/StreetFighterScreen.kt` | Botón nuevo si hace falta (usar paleta ya reservada en el comentario del archivo antes de inventar una). |
| `features/streetfighter/ui/StreetFighterController.kt` + `OfflineStreetFighterController.kt` (`:shared`) + `AndroidStreetFighterController.kt` (`:app`) | Interfaz + 2 implementaciones del método nuevo. |
| `ui/SfTutorialOverlay.kt` | Color del chip si aplica. |
| `app/src/main/assets/STREETFIGHTER/DATA/combos.json` | Lección(es) básica(s) del tutorial. |

Verificación (los 3 comandos, en este orden, y **si algo falla, parar y reportarlo — no
parchear para que compile**):

```bash
./gradlew :app:testDebugUnitTest :shared:testAndroidHostTest
```
```bash
./detekt-cli-1.23.8/bin/detekt-cli --config PolitecnicoOpenWorld/config/detekt/detekt.yml --build-upon-default-config --input PolitecnicoOpenWorld/app/src/main/java,PolitecnicoOpenWorld/shared/src/commonMain/kotlin --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml
```
```bash
bash tools/check_kmp_test_names.sh
```

### Fase 3 — Prompt de arte · Claude lo escribe, el dueño lo usa

El prompt debe fijar, sin dejar nada a la interpretación de la IA de imagen:

- **Identidad bloqueada**: mismo personaje de la hoja `_01_Idle_Turn.png` adjunta — sin
  variantes, sin reinterpretaciones.
- **Escala idéntica** a esa misma hoja de referencia.
- **Fondo croma `#00FF00` opaco**, sin degradados, sin HUD, sin marcas de agua.
- **Rejilla**: columnas de ancho igual por fila, hueco mínimo explícito en PÍXELES (no un rango
  "sugerido" — un rango se interpreta como "lo mínimo que se pueda", ver Corrección 1 más
  abajo), máximo ~8 cuadros por fila.
- **Conteo de cuadros exacto por fila** — el número que salió de la Fase 1, no un múltiplo bonito.
- **Oponente invisible** (si el movimiento involucra a un rival): regla explícita de qué NO
  dibujar, con ejemplos concretos ("ni maniquí, ni muñeco de trapo, ni silueta") — una regla
  genérica no basta, hay que nombrar las formas exactas en que se rompe.
- **Contacto físico vs. a distancia** (si aplica): si el movimiento es de agarre/contacto,
  decirlo así de explícito — una IA de imagen puede reinterpretar "lanza con fuerza" como un
  poder a distancia (fuego, orbes, proyectiles) sin que nada en el prompt lo pida.
- **Props que no se deben perder**: si algún personaje tiene un objeto de firma (escudo, bastón,
  cámara), nómbralo explícitamente para esa hoja — el arte nueva puede "olvidarlo".

Usa `PROMPT_hoja30_contraataque_derribo.md` como plantilla exacta (tiene las 2 variantes: por
personaje y en lote).

### Fase 4 — Generar el arte · Dueño + ChatGPT

El dueño pega el prompt en una conversación (nueva por personaje, o una con memoria de los 18
para hacerlo en lote) y sube la(s) imagen(es) resultante(s) a `newSFAssets/<Personaje>/`.

### Fase 5 — QA visual · Claude, uno por uno, los 18

**El formato correcto (dimensiones, fondo, conteo de cuadros a ojo) NO garantiza que el
contenido sea correcto.** Antes de dar cualquier hoja por buena, revisa VISUALMENTE (recorta la
fila con Python/PIL y mírala, no confíes solo en el conteo del slicer) cada uno de los 18 contra
esta lista — son los fallos que YA salieron caros:

- [ ] ¿Aparece el rival en algún cuadro (maniquí, silueta, cuerpo, muñeco de trapo)?
- [ ] ¿Un movimiento de contacto se reinterpretó como poder a distancia (fuego, orbes, granadas)?
- [ ] ¿Algún personaje perdió su prop de firma (escudo, arma, accesorio)?
- [ ] ¿El conteo de cuadros por fila es EXACTO (cuenta las siluetas del personaje a mano, un
      cuadro con solo un efecto y sin cuerpo NO cuenta como pose)?
- [ ] ¿El hueco entre cuadros es de verdad ≥ el mínimo pedido, o están casi tocándose?

### Fase 6 — Rondas de corrección · Claude escribe el prompt dirigido, dueño regenera

Si algo falla, **regenera la HOJA COMPLETA de los personajes afectados** (no le pidas a una IA
de imagen que edite un pedazo de un PNG ya generado — no es confiable). El prompt de corrección:

1. Nombra el defecto exacto y por qué rompe el diseño (no solo "está mal", di QUÉ está mal).
2. Lista SOLO los personajes afectados, en orden — los demás no se tocan.
3. Repite las reglas de siempre que SÍ salieron bien (para que no se rompan de nuevo) MÁS la
   regla nueva, reforzada con ejemplos concretos de la forma exacta en que se rompió.
4. Pide una verificación explícita antes de entregar (contar a mano, revisar cuadro por cuadro).

Repite Fase 5 → 6 hasta que los 18 pasen. Guarda cada ronda como una sección numerada
("CORRECCIÓN 2", "CORRECCIÓN 3"...) en el mismo doc de prompt, con lo que falló y lo que se
corrigió — es el historial que evita repetir el mismo error dos sesiones después.

### Fase 7 — Extender el pipeline Python · Claude

Dos archivos, siempre los mismos:

| Archivo | Qué se le agrega |
|---|---|
| `tools/slice_sf_chroma_sheets.py` | Entrada `SHEETS[N]` (2 grupos lógicos MÁXIMO por hoja — si el diseño tiene 3+ sub-animaciones, agrupa las que comparten fila/bloque contiguo como una sola secuencia, ver hoja 27 y hoja 30 como precedente). Entrada en `SF_POSE_TARGET_H` (altura fija en px, o `None` si la pose no debe normalizarse — barridas, derribos, poses con escala propia). |
| `tools/pack_sf_character.py` | Entrada en `NEW_MOVE_ANIMATIONS` (prefijo de archivo, conteo de cuadros, delays de frame) y en `reference_frame_key()` (qué cuadro clásico de hitbox/hurtbox reusa cada cuadro nuevo). |

⚠️ **Gotchas que YA costaron una sesión completa depurar** (ver `00_SF_INDEX.md` §7-8 para el
detalle):

- **La caché GEN real no está en la ruta por defecto.** Vive en
  `newSFAssets/GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio/` (commiteada a
  git), no en `app/src/main/assets/.../GEN` (esa ruta nunca debe usarse — viajaría al APK). TODO
  comando de `slice_sf_chroma_sheets.py`/`pack_sf_character.py` necesita
  `--gen ../newSFAssets/GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio` explícito.
- **`pack_sf_character.py` reconstruye el personaje COMPLETO desde el GEN indicado** (exige las
  77 claves de `sf_template.json`) — no fusiona con el JSON/atlas ya existente. No sirve
  procesar solo la hoja nueva sin la caché completa de las hojas anteriores.
- **Un grupo de 2 filas en `SHEETS[N]`** (una sub-animación por fila dentro del mismo grupo
  lógico, p. ej. 4+8) puede romper `merge_fragments` (fusiona las 2 filas si una banda suelta de
  <3 blobs tumba la detección de "2+ filas reales") y `maybe_split` (reordena todo por X puro
  aunque no parta nada, intercalando las filas). Si tu hoja nueva usa este patrón, verifica con
  `--list` que el conteo Y el orden salen bien antes de empacar.

### Fase 8 — Dry-run del recorte · Claude, los 18

```bash
python tools/slice_sf_chroma_sheets.py "<ruta_hoja>" <char> --gen <GEN_real> --list
```

Repite para los 18 ANTES de escribir un solo archivo real. Si alguno da un conteo distinto al
esperado, para ahí — no sigas a empaquetar con un conteo roto (puede ser un bug del slicer, como
pasó dos veces en la hoja 30, o un defecto de arte que la Fase 5 no cazó a ojo).

### Fase 9 — Recorte + empaquetado real · Claude (con permiso del dueño — toca assets de producción)

```bash
python tools/slice_sf_chroma_sheets.py "<ruta_hoja>" <char> --gen <GEN_real>
python tools/pack_sf_character.py <char> <Titulo> --gen <GEN_real>
```

`<char>` = nombre de la carpeta dentro del GEN (todo minúsculas, p. ej. `policiacdmxhombre`);
`<Titulo>` = nombre exacto de archivo en `IMAGES/`/`DATA/` (ver la tabla de nombres canónicos en
`PROMPT_hoja30_contraataque_derribo.md` si hay dudas de cuál es cuál). Esto sobrescribe
`IMAGES/<Titulo>.webp` y `DATA/<char>.json` de PRODUCCIÓN — confirmar con el dueño antes de
correrlo para 18 personajes de un tirón.

### Fase 10 — QA del atlas empacado · Claude

```bash
python tools/sf_contact_sheet.py <char> mid
```

Revisa al menos 3-4 personajes representativos (uno con prop de firma, uno del género/tipo que
tuvo más rondas de corrección) — el empaquetado puede introducir sus propios bugs (mapeo de
`reference_frame_key()` equivocado, escala rara) independientes de si el recorte dio bien.

### Fase 11 — Reverificar el motor · Claude

Repite los 3 comandos de la Fase 2. El JSON es nuevo para las claves agregadas — vale la pena
confirmar que nada se rompió, aunque no se haya tocado Kotlin en esta fase.

### Fase 12 — Actualizar docs · Claude

Por el protocolo de `09_CONVENTIONS_GOTCHAS.md` §13 ("una contradicción es un bug"):

- `DISENO_ARCADE_SF_POW.md` — nueva entrada de changelog con fecha.
- `00_SF_INDEX.md` — fila nueva en la tabla de traspasos si generaste un prompt dedicado; regla
  nueva en "reglas que más caro han salido" si encontraste un gotcha no documentado.
- `_SESION_ACTUAL.md` — estado actual, para que otra sesión/IA/PC pueda retomar sin releer todo.
- El prompt de arte mismo, con cada ronda de corrección documentada.

### Fase 13 — Commit · Solo si el dueño lo pide explícitamente

Nada de esto se commitea automáticamente. `git status`, revisar qué se tocó, y solo entonces
`git add`/`git commit` si el dueño lo confirma.

---

## FLUJO B — Personaje nuevo (18 → 19)

Mismo reparto de roles, pero el alcance es mayor: un personaje nuevo necesita las **~30 hojas
completas** (no solo una), no las 18 hojas × 1 movimiento del Flujo A.

1. **Diseño del personaje**: identidad visual, ¿es un peleador dedicado (moveset 3rd Strike
   completo) o usa `isAlpha`/silueta compartida como `GRANADERO`/`PARAMEDICO`? Fijarlo antes de
   pedir arte — cambia cuántas hojas hacen falta.
2. **Arte desde cero** (hojas 01-19): sigue `GUIA_regeneracion_sprites_croma.md` — es la guía
   específica de esta fase, con el histórico de qué produjo qué para cada peleador existente
   como referencia.
3. **Moveset 3rd Strike** (hojas 20-29): sigue `PROMPT_SOL56_TANDAS_NUEVAS.md`.
4. **Cualquier movimiento agregado DESPUÉS del roster base** (como Contraataque/Derribo, hoja
   30+): Flujo A de este documento, pero con el personaje nuevo incluido en la tanda de 1 en vez
   de 18.
5. **Registro en Kotlin**: entrada nueva en el enum de personajes (`SfFighterId` o el que exista
   en ese momento — buscar el patrón de una entrada existente y clonarlo) con sus rutas de
   `DATA`/`IMAGES`; si tiene proyectiles, entrada en `PROJECTILE_PROFILES`
   (`tools/pack_sf_character.py`); revisar si necesita entradas propias en `BONUS_REMOVED_POWERS`/
   `BONUS_FAST_POWERS` si algún bonusPower no le aplica.
6. **QA + verificación**: Fases 5, 10 y 11 del Flujo A, para las ~30 hojas en vez de 1.
7. **Docs**: además de la Fase 12 del Flujo A, actualizar el roster en `DISENO_ARCADE_SF_POW.md`
   y `FLUJO_ASSETS_SF.md` si el nuevo personaje introduce un patrón de carpeta/nombre distinto.

---

## Comandos de referencia rápida

```bash
# Dry-run (nunca escribe nada)
python tools/slice_sf_chroma_sheets.py "<ruta_hoja>" <char> --gen <GEN_real> --list

# Recorte real
python tools/slice_sf_chroma_sheets.py "<ruta_hoja>" <char> --gen <GEN_real>

# Empaquetado (atlas + JSON de producción)
python tools/pack_sf_character.py <char> <Titulo> --gen <GEN_real>

# QA visual del atlas ya empacado
python tools/sf_contact_sheet.py <char> mid

# Verificación del motor (los 3, en este orden)
./gradlew :app:testDebugUnitTest :shared:testAndroidHostTest
./detekt-cli-1.23.8/bin/detekt-cli --config PolitecnicoOpenWorld/config/detekt/detekt.yml --build-upon-default-config --input PolitecnicoOpenWorld/app/src/main/java,PolitecnicoOpenWorld/shared/src/commonMain/kotlin --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml
bash tools/check_kmp_test_names.sh
```

`<GEN_real>` = `../newSFAssets/GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio`
(confirmar que sigue siendo esta carpeta — si se crea una caché nueva más completa, actualizar
esta ruta aquí y en `00_SF_INDEX.md` §7).
