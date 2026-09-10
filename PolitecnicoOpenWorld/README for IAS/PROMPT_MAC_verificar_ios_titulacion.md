# 🍏 TRASPASO A LA MAC — verificar iOS con "Titulación por Combate"

**Escrito el 2026-09-05 desde Windows (Opus 5).** Rama: `tooling/sf-asset-pipeline` (PR #141).

> **Qué hay que probar y por qué importa:** esta rama toca **el menú entero** (todos los botones
> cambiaron de forma de pintar el texto) y **el modo pelea**, que en iOS es el único modo jugable.
> Nada de esto se ha visto nunca en un simulador: se escribió y se verificó en Windows.

---

## 0. Lo que YA está verificado desde Windows — no lo repitas

| Comprobado | Resultado |
|---|---|
| `:shared:compileKotlinIosSimulatorArm64` | **BUILD SUCCESSFUL** (42 s). El `autoSize` nuevo SÍ existe en Kotlin/Native. |
| `:app:testDebugUnitTest` + `:shared:testAndroidHostTest` | 342 tests (125 + 217), 0 fallos |
| detekt · `check_kmp_test_names.sh` | exit 0 · OK |
| Recorte/empaquetado de los 18 peleadores | 18/18, QA visual con `sf_contact_sheet.py` |

**Lo que Windows NO puede decir y por eso existe este doc:** cómo se VE el texto en pantalla, si la
memoria aguanta, y si se juega bien. Compilar no es ver.

---

## 1. Qué cambió que a iOS le afecta

### A · El modo se renombró (cambio VISIBLE para el jugador)

"Huelum vs. Goya" → **"Titulación por Combate"**. El nombre vive en el string
`menu_street_fighter` de `composeResources` — el mismo que usa iOS. **En el código NO cambia nada**:
sigue siendo `street_fighter`/`Sf*`. Lo único que se renombró fue `PowModo.HUELUM_VS_GOYA` →
`PowModo.STREET_FIGHTER`, que es justo el enum que consulta el menú de iOS para decidir qué botones
pinta (`modosDe(IOS)`).

### B · TODOS los botones del menú cambiaron cómo pintan su texto

Este es **el riesgo real de esta rama en iOS**. El nombre nuevo (22 caracteres contra 15) no cabía
en el botón destacado, así que los rótulos pasaron de `Text` con tamaño FIJO + `Ellipsis` a
`BasicText` con `autoSize` (encoge la letra en vez de cortar la palabra):

| Composable | Antes | Ahora |
|---|---|---|
| `MenuButton` (todos los botones del menú, incl. los modos vía `BotonDeModo`) | 16.sp fijo, `Ellipsis` | autoSize 16 → 10.sp |
| `FeaturedStreetFighterButton` (el botón grande) | 20.sp fijo, `Ellipsis` | autoSize 20 → 11.sp |
| `FeaturedArcadeButton` (submenú de pelea) | 18.sp + descripción 9.sp | autoSize 18→11 y 9→7.sp |

⚠️ **Los altos siguen FIJOS** (56.dp y 76.dp) a propósito: de eso depende el fix del Galaxy S24
(2026-07-21), que evita que un rótulo salte a dos líneas y se recorte. Si algo se ve mal, la
solución **no** es quitar el alto fijo.

También se quitó la etiqueta "◆ MODO COMBATE ◆" que iba debajo del botón grande (decía dos veces lo
mismo con el nombre nuevo) y su string `menu_featured_tag` se borró de los 4 archivos.

### C · Los peleadores se re-empaquetaron (los 18)

Dos movimientos nuevos (**Contraataque** = L3, **Derribo con Poder** = R3) y arreglos en los
sprites de **La Llorona**. Esto reescribió `IMAGES/*.webp` + `DATA/*.json` de los 18.

⚠️ **La Llorona es justamente uno de los tres atlas ALPHA que causaron el OOM P0 en iOS** (ver
`ccac1bbd`). Su atlas cambió en esta rama, así que **hay que volver a medir la memoria con ella**.

---

## 2. Orden de pruebas (de lo que más rompe a lo que menos)

### Paso 1 — Que arranque y el menú se lea

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
# y abrir iosApp/POW.xcodeproj en Xcode → Run en el simulador
```

En el menú principal de iOS (que solo pinta AJUSTES, COLECCIONABLES y el modo pelea, más los "EN
OBRAS" si `MODOS_EN_OBRAS_VISIBLES` sigue en `true`):

1. **El botón grande dice "★ TITULACIÓN POR COMBATE ★" ENTERO**, sin "…" y sin salirse.
2. **Debajo ya NO hay ninguna etiqueta** ("MODO COMBATE" se quitó a propósito).
3. Los demás rótulos (AJUSTES, COLECCIONABLES) se leen completos.
4. **Repite con la fuente del sistema en grande** (Ajustes → Pantalla y brillo → Tamaño del texto,
   al máximo) y **en el iPhone más chico que tengas** (SE si hay). Ahí es donde el `autoSize` tiene
   que trabajar: la letra debe ENCOGER, nunca cortarse.
5. Cambia el idioma del simulador a inglés y repite: el nombre es propio, así que **debe seguir
   diciendo "TITULACIÓN POR COMBATE"** en inglés, no traducirse.

**Si el texto sale cortado o se sale:** el `autoSize` no está midiendo bien contra el ancho.
Empieza mirando el `Modifier.fillMaxWidth()` que lleva cada `BasicText` — sin ancho acotado,
`autoSize` no sabe cuándo encoger.

### Paso 2 — Entrar al modo pelea

El submenú debe titularse "TITULACIÓN POR COMBATE" (a 24.sp, puede partirse en DOS líneas: ahí sí
se permite, la columna hace scroll). El botón ARCADE y su descripción deben leerse completos.

### Paso 3 — Los dos movimientos nuevos

En una pelea, con un peleador que tenga la hoja 30 (los 18 dedicados la tienen):

- **L3 = Contraataque**: ventana de 200 ms. Si te pegan dentro, anula el golpe y haces un
  lanzamiento gratis. Si fallas, la recuperación es larga.
- **R3 = Derribo con Poder**: exige medidor ≥ 40/100 y estar pegado al rival.

Comprueba que **los botones L3/R3 aparecen** (se pintan solo si el peleador tiene esa hoja) y que
las animaciones no parpadean ni dejan al personaje congelado.

### Paso 4 — La Llorona y la memoria (lo que más nos ha dolido)

Pelea COMPLETA con La Llorona y vigila la RSS en Xcode:

- La referencia de `ccac1bbd` es **~612 MB en pelea → ~494 MB de vuelta al menú**. Si ahora sube
  bastante más, el atlas re-empaquetado engordó y hay que mirarlo.
- Mira su **Especial Pesado** (joystick + B) y su **Especial Medio** (joystick + Y): en Android se
  arreglaron dos cuadros donde el personaje DESAPARECÍA y solo quedaba una mancha oscura. Debe
  verse ella en todos los cuadros.

---

## 3. Lo que sigue pendiente de antes (no lo abras si no toca)

- **Firma de iOS**: el target de dispositivo no tiene `DEVELOPMENT_TEAM`. Con un Apple ID gratis
  basta para tu propio iPhone (perfil de 7 días).
- **Dos costuras que nunca han corrido** (orientación y puente JS): tienen su propio traspaso en
  `PROMPT_MAC_orientacion_y_puente_js.md`. **No** son parte de esta rama.
- **`MODOS_EN_OBRAS_VISIBLES = true`**: hay que ponerlo en `false` ANTES de firmar para la App
  Store (Apple rechaza funciones anunciadas que no funcionan). Ver `PowModos.kt`.

---

## 4. Si algo falla

Repórtalo con: **qué paso**, **qué esperabas**, **qué viste** y una captura. Si es de texto que no
cabe, di también **qué iPhone** y **qué tamaño de fuente** tenías puestos — sin esos dos datos el
fallo no se puede reproducir.

Al terminar, actualiza `_SESION_ACTUAL.md` con lo MEDIDO (no con lo que "debería"): es el único
traspaso que lee la siguiente sesión.
