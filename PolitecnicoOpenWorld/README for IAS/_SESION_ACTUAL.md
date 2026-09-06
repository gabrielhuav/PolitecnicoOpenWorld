# 🧠 MEMORIA COMPARTIDA ENTRE IAs — estado vivo

> Único traspaso entre IAs. Ventana de 2 días; máximo 200 líneas. Aquí va estado medido,
> trabajo abierto y trampas caras. Diseño e historia viven en los documentos de cada área.

**Última actualización:** 2026-08-30 · Claude Sonnet 5 (Windows, escritorio) · rama `tooling/sf-asset-pipeline` (sin push)

> ⚠️ **El trabajo de iOS/mundo abierto medido hasta el 08-17 (ANRs en `NativeOsmMap`, fases 5/6
> del mundo, puente JS↔Swift) se purgó a `_ARCHIVO/HISTORIAL_sesiones_2026-08-17.md`** — pasaron
> 13 días sin que nadie lo tocara. Nada de esto se tocó en esta sesión. Si retomas ese hilo,
> verifica primero si sigue vigente antes de confiar en esas cifras.

## ✅ 08-30: "La Llorona se ve como una bola negra" — RESUELTO en 2 movimientos, auditados los 18

Reporte del dueño sobre su Especial Pesado. Resultaron ser DOS problemas separados, encontrados
uno tras otro — el primero no era "el" bug, solo lo tapaba parcialmente:

1. **Bug de pipeline (color de borde)**: `place_sf()`/`place_world()`
   (`tools/slice_sf_chroma_sheets.py`) escalaban con LANCZOS sin premultiplicar alfa — el borde
   semitransparente de un efecto OSCURO sale más oscuro/desaturado de lo que el arte de origen
   tenía. Corregido con `resize_premultiplied()`. Sin regresión (`idle-1` sale byte-idéntico).
   Detalle técnico en `SF/00_SF_INDEX.md` §9.
2. **El bug real — composición, no color**: en la hoja 12 original, el cuadro 4 de SPECIAL HEAVY
   dibujaba a La Llorona y el remolino YA MUY SEPARADOS dentro de la MISMA celda ancha. El
   recorte automático, al partir esa celda en 2 cuadros de animación, dejaba UN CUADRO CON SOLO
   EL EFECTO — en el juego, en ese instante, ella desaparecía por completo. Confirmado comparando
   contra `_11_SpecialLight_Medium.png` (ahí SÍ está visible en los 5 cuadros). Corregido
   regenerando el arte: cuadro 4 ancho → 2 cuadros parejos con ella SIEMPRE visible.
   **El mismo defecto apareció DESPUÉS en Special Medium** (hoja 11, cuadro 5: las calaveras
   dispersándose sin ella) — el dueño lo detectó jugando ("joystick+Y"). Mismo diagnóstico, mismo
   arreglo (cuadro 5 ensanchado, ella visible junto a las calaveras). Ambos ya corregidos y
   re-empaquetados.
- **Auditoría completa de los 18** (pedida por el dueño tras el 2º hallazgo): script que compara
  el área opaca de cada cuadro de special1Light/Medium/Heavy, superArt, bonusPower y el resto del
  moveset contra el idle de cada personaje — señala cuadros con <10-15% del área esperada (cuerpo
  ausente). **Resultado: SOLO La Llorona tenía el defecto** (ambos casos ya corregidos). Los
  otros 17 están limpios. Metodología en `SF/00_SF_INDEX.md` §10 por si hace falta repetirla.
- **Hallazgo aparte — TAMBIÉN CORREGIDO (a petición del dueño)**: `special-medium-3` de La
  Llorona detectaba 6 blobs en vez de 5 en su fila (su efecto quedaba MUY cerca del límite del
  umbral "fragmento angosto" de `merge_fragments`, no se fusionaba con su cuerpo, y `pick()`
  descartaba el efecto en silencio para completar el contrato de 5). Arreglado en CÓDIGO, no en
  arte: nuevo override `FORCE_EXACT_MERGE` en `slice_sf_chroma_sheets.py` — fusiona el par de
  blobs con el hueco más chico hasta llegar exacto al conteo esperado, pero SOLO para
  `(char, hoja)` explícitamente listados ahí (hoy solo `("lallorona", 11)`) — cero cambio de
  comportamiento para los otros 17. Verificado: "6/5 AVISO" → "5/5 OK", cuadro 3 ya muestra su
  brillo cian. Detalle en `SF/00_SF_INDEX.md` §12.
- **Revisado, NO tocado**: los otros 16. La Presidenta tiene el mismo patrón de borde en sus 11
  bonus powers pero sus colores base son brillantes (no se ve roto) — pendiente si el dueño lo
  pide. El "agujero negro" de La Tzitzimime (Special Heavy) es diseño intencional (eclipse), no
  el bug — confirmado contra el arte de origen.
- **Lección para la próxima**: si un cuadro de una hoja combina 2 momentos en una celda ancha (el
  personaje + un efecto ya lejos de él), NO asumas que el recorte automático los va a separar
  bien — revisa si el personaje sigue visible en AMBOS cuadros resultantes, comparando contra una
  animación hermana que sí esté bien. Detalle y la regla del audit en `00_SF_INDEX.md` §10.
- Nada de esto se ha commiteado.

## ✅ 08-29/30: CONTRAATAQUE + DERRIBO CON PODER — motor, arte y pipeline TERMINADOS (18/18)

Dos movimientos nuevos de la familia Agarre/Parry para "Huelum vs. Goya". Diseño completo en
`SF/DISENO_ARCADE_SF_POW.md` (registro por fecha); prompt de arte y su historial de correcciones
en `SF/PROMPT_hoja30_contraataque_derribo.md`.

**✅ Motor (`:shared`) implementado y verificado, SIN COMMITEAR:**
- `SfFighterState.COUNTER` / `POWER_GRAB` / `POWER_THROW` (al final del enum), tabla
  `SfStateMachine.VALID_FROM`, ramas en `runStateHandler` (`StreetFighterMaquinaEstados.kt`).
- `applyCounterThrow` / `applyPowerThrow` en `StreetFighterCombate.kt` (mirrors de `applyThrow`
  con su propio daño/empuje); ventana de tiempo `counterActiveUntilMs` (molde de
  `parryActiveUntilMs`); medidor parcial (`POWER_THROW_METER_COST = 40`) gastado al ENTRAR a
  `POWER_GRAB`, conecte o falle.
- IA en `StreetFighterCpuAi.kt` (contraataque como alternativa arriesgada al parry; derribo con
  poder preferido sobre el agarre normal cuando sobra medidor y no se guarda para la súper).
- Botones L3 (Esmeralda)/R3 (Rubí) en `StreetFighterScreen.kt` — la paleta YA estaba reservada
  para esto en el propio comentario del archivo. Gate por movimiento (`playerHasCounterMove`/
  `playerHasPowerThrowMove`), no por todo el moveset 3rd Strike.
- `./gradlew :app:testDebugUnitTest :shared:testAndroidHostTest` en verde, `detekt` exit 0,
  `bash tools/check_kmp_test_names.sh` OK. Sin arte, el motor es 100% inerte (`hasAnim` bloquea
  los 3 estados nuevos) — cero regresión visual/jugable mientras tanto.

**✅ Arte (hoja 30, 18 personajes dedicados) — TERMINADO Y VERIFICADO (2026-08-29d), 3 rondas de corrección:**
1. **Tanda 1** (layout de 2 filas, 12 cuadros de "Derribo con Poder" en una sola fila de 1672px):
   cuadros pegados. **Corregido:** layout de 3 filas (6 / 4 / 8), hueco mínimo ≥70px.
2. **Tanda 2** (2600×1412): 9 de 18 dibujaron al RIVAL en la Fila 3 (maniquí/silueta/cuerpo,
   rompe "oponente invisible"). **Corregido y verificado** en ESCOMROBOT, La Presidenta, Yoalli
   Ehécatl, Charro Negro, La Tzitzimime, Policía Femenino CDMX, Policía Masculino CDMX,
   Paramédico Cruz Roja, Policía Granadero (Hombre).
3. **Tanda 3**: de esos 9, otros 4 habían reinterpretado el agarre como un PODER A DISTANCIA
   (ESCOMROBOT con orbes/escudo holográfico, Yoalli Ehécatl con fuego, Policía Masculino CDMX
   y Policía Granadero Hombre lanzando una granada) en vez de un agarre físico de corto alcance
   (62px, pegado al rival) — y Policía Granadero Hombre había perdido su escudo antimotines.
   **Corregido y verificado**: agarre físico limpio en los 4, escudo del Granadero restaurado en
   las 3 filas, Fila 2 de Policía Masculino CDMX en 4 cuadros exactos (había salido en 6).
   Prompt completo de las 3 rondas en `SF/PROMPT_hoja30_contraataque_derribo.md`.

**✅ Pipeline Python — HECHO para los 18/18 (2026-08-30):**
1. `tools/slice_sf_chroma_sheets.py` (`SHEETS[30]`) y `tools/pack_sf_character.py`
   (`NEW_MOVE_ANIMATIONS`/`reference_frame_key()`) ya extendidos. 2 bugs reales encontrados y
   corregidos en `merge_fragments`/`maybe_split` (mismo archivo, `slice_sf_chroma_sheets.py`):
   un fragmento suelto (el efecto de impacto de la Fila 3) tumbaba la separación por filas y
   fusionaba Fila 2 con Fila 3; y `maybe_split` reordenaba todo por X puro incluso sin partir
   nada, intercalando las 2 filas. Detalle técnico en
   `SF/PROMPT_hoja30_contraataque_derribo.md` ("Código del pipeline").
2. **Recortados + empacados los 18/18**: CharroNegro, EscomBoy, EscomGirl, Robot (ESCOMROBOT),
   LaLlorona, LaTzitzimime, Paparazzi1, Paparazzi5, PoliciaCDMX, PoliciaCDMXHombre,
   PoliciaGranaderoMujer, PoliciaGranaderoHombre, Prankedy, ReyGrupero, SenorTienda,
   YoalliEhecatl, LaPresidenta, ParamedicoCruzRoja. Los 3 últimos necesitaron una 4ª ronda de
   arte (CORRECCIÓN 4: Fila 3 traía 7 poses en vez de 8, el efecto de impacto flotaba solo en
   el hueco central en vez de acompañar a una pose) — ya regenerados, verificados visualmente
   (8 siluetas, efecto pegado a una pose real) y empaquetados. QA visual con
   `sf_contact_sheet.py` en 6 muestras total: sin rival, sin fuego, escudo del Granadero
   conservado.
3. Verificación del motor repetida tras el empaquetado final: `./gradlew :app:testDebugUnitTest
   :shared:testAndroidHostTest` BUILD SUCCESSFUL, detekt exit 0, `check_kmp_test_names.sh` OK.
4. `combos.json` ya tiene las 2 lecciones básicas del tutorial (`b_counter`/`b_powerthrow`); no
   hace falta nada más ahí salvo que se quiera un combo universal/de firma con estos movimientos.

**CONTRAATAQUE + DERRIBO CON PODER quedan completos: motor, arte y pipeline para los 18
personajes.** Nada de esto se ha commiteado.

**🆕 Nuevo doc: `SF/PIPELINE_agregar_movimientos_y_personajes.md`** — generaliza todo este
proceso (diseño→motor→prompt→arte→correcciones→pipeline→verificación→docs) como receta
reutilizable para la próxima vez que se agregue un movimiento o un peleador nuevo, con el
reparto de quién hace qué (Claude / IA de imagen / dueño). Léelo antes de repetir algo de esto.

⚠️ **La caché GEN real vive en
`newSFAssets/GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio/`** (commiteada a
git), NO en `app/src/main/assets/.../GEN` (esa ruta nunca debe usarse — viajaría al APK; si
aparece localmente sin querer, es basura y se borra). `pack_sf_character.py` **reconstruye el
personaje COMPLETO desde el GEN indicado** (exige las 77 claves de `sf_template.json`) — no
fusiona con el JSON/atlas ya existente, así que siempre hace falta la caché completa, no solo
la hoja que se está agregando.

## 1. Reglas vivas

- Leer `00_INDEX.md`, `09_CONVENTIONS_GOTCHAS.md` y `10_ARQUITECTURA_SEPARACION.md`. **Si toca las
  DOS plataformas, `11_SEPARACION_IOS_ANDROID.md` es obligatorio** (costuras y trampas del simulador).
- `PowJson` imita Gson a propósito. `encodeToString(Map)` compila y falla en runtime: usar
  `jsonOf`/`jsonArrayOf`. No cambiar saves ni la ruta Android de la BD.
- Kotlin queda en **2.3.21** (KSP no existe para 2.4); AGP 9 usa `android.builtInKotlin=true`;
  tests shared = `testAndroidHostTest`. `iosX64` fuera: Compose MP 1.11.1 no publica ese target.
- Los nombres de tests de `commonTest` no admiten `(`, `)` ni `,`: `bash tools/check_kmp_test_names.sh`.
- En parciales, los campos viven en la clase y nunca se repite allí una función del parcial:
  ganaría la clase en silencio (`10_ARQUITECTURA_SEPARACION.md` §4).
- Un estado nuevo de `SfFighterState` va SIEMPRE al final del enum (viaja por red como
  `enum.name`) y necesita rama en el `when` exhaustivo de `runStateHandler` + entrada en
  `SfStateMachine.VALID_FROM` + gate `hasAnim` antes de poder jugarse (09 §12).
- **🆕 (08-29) Las IAs de generación de imagen NO respetan "oponente invisible" de forma
  confiable, aunque el prompt lo diga explícito**: la mitad de una tanda de 18 dibujó un
  maniquí/silueta del rival en el cuadro donde el agarre "conecta". Corregido eso, una
  fracción de esos mismos personajes reinterpretó el agarre como un PODER A DISTANCIA (fuego,
  orbes, granadas) — dos fallos DISTINTOS de la misma tanda, cada uno necesitó su propia
  corrección explícita. Revisar SIEMPRE VISUALMENTE cada personaje (no solo dimensiones/fondo/
  conteo de cuadros) antes de dar por buena una hoja con agarre/lanzamiento — y no asumir que
  arreglar un fallo no introdujo otro. Ver `SF/00_SF_INDEX.md` §"reglas que más caro han salido".

## 🖥️ Rutas por PC

| PC | Raíz del proyecto (`gradlew` y `tools/`) |
|---|---|
| Laptop | `C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| Escritorio (MEDIDO 07-29) | `C:\Users\gabri\Documents\GitHub Desktop\PolitecnicoOpenWorld\PolitecnicoOpenWorld` |
| Mac | `/Users/gabrielhuav/Documents/GitHub/PolitecnicoOpenWorld/PolitecnicoOpenWorld` |

La carpeta es doble; los docs usan rutas relativas a esta raíz. El GEN de sprites vive fuera, en
`..\newSFAssets\GEN_*`. En Windows la ruta del escritorio lleva espacio: entrecomillarla.
PC nueva: `SETUP_PC_NUEVA.md`; `gradle-wrapper.jar` y `secrets.properties` no viajan por Git.

## Verificación al cerrar (motor SF; no toca arte/pipeline Python)

```bash
./gradlew :app:testDebugUnitTest :shared:testAndroidHostTest
```

```bash
./detekt-cli-1.23.8/bin/detekt-cli --config PolitecnicoOpenWorld/config/detekt/detekt.yml --build-upon-default-config --input PolitecnicoOpenWorld/app/src/main/java,PolitecnicoOpenWorld/shared/src/commonMain/kotlin --baseline PolitecnicoOpenWorld/config/detekt/baseline.xml
```

```bash
bash tools/check_kmp_test_names.sh
```

Esperado: 0 fallos, detekt exit 0, nombres de test compatibles con Kotlin/Native. Los tres ya
estaban en verde el 08-29 antes de tocar el arte. Antes de push: `git status`, actualizar este
archivo y hacer `git pull` inmediatamente antes del push.
