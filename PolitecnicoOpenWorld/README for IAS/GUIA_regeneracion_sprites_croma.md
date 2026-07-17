# GUÍA · Regeneración de sprites por HOJAS CROMA (SF + mundo, un solo arte) — 2026-07-16

> **Qué es:** el procedimiento VIGENTE para regenerar el arte de un peleador de
> "HUELUM VS. GOYA" **y** su set del mundo abierto desde CERO con ChatGPT Images,
> usando hojas con **fondo croma verde #00FF00** y 2 grupos de animación por hoja.
> Sustituye al "modo simple" de `GUIA_generacion_assets_SF.md` (fondo negro) para
> personajes nuevos. **Prankedy, Señor de la Tienda, Rey Grupero, ambos Paparazzi, las policías CDMX,
> Paramédico Cruz Roja, ambos Policías Granadero y los tres estudiantes ESCOM ya se
> regeneraron así (2026-07-16)**.
> Canon Prankedy: cabello RIZADO (no rastas), idle del mundo SIN guardia.
> Esta guía es AUTOSUFICIENTE: contiene el prompt maestro completo.

## 0. Resumen del flujo (por personaje)

1. Nueva conversación en ChatGPT: subir las 9 referencias + PROMPT MAESTRO (§4).
2. GPT genera normalmente 19 hojas en tandas (5 → 10 → 4); revisar cada tanda y corregir puntuales.
   Solo las hojas 14/15 `REFINED` son reemplazos opcionales: puede haber 18 si se omitió una de
   ellas, siempre que las versiones base 07–09 existan. Cualquier otra ausencia es bloqueante.
3. Descargar TODO a `newSFAssets/<Personaje>/` (raíz del REPO, fuera del proyecto Android).
4. Identificar/renombrar las hojas (los nombres de descarga de GPT no sirven): por los
   títulos amarillos — a mano o con OCR (`tesseract` sobre la máscara amarilla).
   Nombres canónicos: `<Personaje>_01_Idle_Turn.png` … `_19_WalkBackward_HandgunWalk.png`
   (catálogo exacto en §5).
5. Recortar en orden ESTRICTO 01→19 (la hoja 01 SIEMPRE primero, fija la referencia;
   cada secuencia corrige después el zoom desigual de su propia hoja;
   las hojas 14/15 `REFINED` sobrescriben intencionalmente puños/patadas de 07–09):
   `python3 tools/slice_sf_chroma_sheets.py "newSFAssets/<P>/<P>_01_Idle_Turn.png" <char>`
   … y así con las 19 (basta un for; `--list` = dry-run con conteos).
6. Empaquetar SF: `python3 tools/pack_sf_character.py <char> <Titulo>` →
   `STREETFIGHTER/IMAGES/<Titulo>.png` + `DATA/<char>.json`. Cero código si el
   personaje YA tenía sheet dedicado; si era COMPARTIDO (sharedSet), además quitar
   su `sharedSet` en `SfFighterId` y apuntar jsonAsset/spriteAsset a los nuevos.
7. Mundo: copiar `GEN/WORLD_<char>/{Idle,Walk,Run,Special,Talk}` sobre su set en
   `SPRITES/` (borrar frames viejos SOBRANTES si el conteo bajó) y actualizar en
   `PlayerSkin.kt`: `idleFrames/walkFrames/runFrames/specialFrames`. Para sets croma
   512² normalizados, activar `uniform512Canvas=true` y usar `360/512 = 0.703125`
   en todas las `*BodyFraction`; la UI no debe reescalar cada acción por separado.
8. **⚠️ SACAR `STREETFIGHTER/GEN/` de assets al terminar** (viaja al APK si se queda):
   `mv app/src/main/assets/STREETFIGHTER/GEN newSFAssets/GEN_<char>_intermedio`.
   Ahí quedan también `_extra/` (armas, talk, jump land, specials L/M) y `WORLD_*`.
9. Rebuild + probar: selector SF (preview idle-1), pelea completa, especial/proyectil,
   y en el mundo idle/caminar/correr/especial del skin.

### 0b. Traspaso exacto para otro modelo/agente (Claude, Gemini o Codex)

No improvisar rutas ni nombres. Desde la raíz del proyecto Android (`PolitecnicoOpenWorld/`):

1. **Inspeccionar visualmente las 19 hojas antes de renombrar.** El nombre/fecha de descarga NO
   determina el orden. Leer los títulos amarillos y asignar el catálogo §5. La hoja que contiene
   `IDLE + IDLE TURN` siempre será `_01_`, aunque haya sido la cuarta descarga.
2. Elegir cuatro identificadores que no colisionen con personajes existentes:
   - `<char>`: minúsculas sin espacios, usado por JSON y `GEN` (ej. `policiagranaderohombre`).
   - `<Titulo>`: PascalCase del sheet (ej. `PoliciaGranaderoHombre`).
   - `<CarpetaMundo>`: carpeta bajo `SPRITES/NPC/` (ej. `PoliciaGranaderoMasculinoCDMX`).
   - `<prefijo>`: tres letras + `_`, único (ej. `pgm_`).
3. Procesar **01 primero** y luego 02→19. Usar un `GEN` externo; nunca dejarlo en assets:

```powershell
$Source = "..\newSFAssets\<Personaje>"
$Gen = "..\newSFAssets\GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio"
$Char = "<char>"
$Prefix = "<prefijo>"

# Conteo previo, sin escribir resultados.
1..19 | ForEach-Object {
    $File = Get-ChildItem -LiteralPath $Source -Filter ("<Personaje>_{0:D2}_*.png" -f $_)
    python tools\slice_sf_chroma_sheets.py $File.FullName $Char --sheet-num $_ `
        --list --gen $Gen --world-prefix $Prefix
}

# Recorte real, siempre en orden.
1..19 | ForEach-Object {
    $File = Get-ChildItem -LiteralPath $Source -Filter ("<Personaje>_{0:D2}_*.png" -f $_)
    python tools\slice_sf_chroma_sheets.py $File.FullName $Char --sheet-num $_ `
        --gen $Gen --world-prefix $Prefix
    if ($LASTEXITCODE -ne 0) { throw "Falló hoja $_" }
}
```

4. Leer los 19 resultados. `OK` es ideal; un sobrante se muestrea uniformemente y un faltante de
   exactamente un cuadro se completa con el vecino central. Un faltante mayor DEBE abortar. La
   herramienta ya contempla ambos grupos en una sola fila, proyectiles formados por chispas y KO
   con cambio de orientación; no parchear cuadros manualmente antes de entender el aviso.
   Si hay 18 hojas, identificar la ausente por título. **Únicamente 14 o 15 pueden omitirse**:
   conservar el hueco numérico y procesar los archivos presentes, nunca renombrar 16→15. Ejemplo:

```powershell
Get-ChildItem -LiteralPath $Source -Filter "<Personaje>_*.png" | Sort-Object Name | ForEach-Object {
    if ($_.Name -notmatch '^<Personaje>_(\d{2})_') { throw "Nombre no canónico" }
    $SheetNumber = [int]$Matches[1]
    python tools\slice_sf_chroma_sheets.py $_.FullName $Char --sheet-num $SheetNumber `
        --gen $Gen --world-prefix $Prefix
    if ($LASTEXITCODE -ne 0) { throw "Falló hoja $SheetNumber" }
}
```

   Antes de empaquetar debe haber 123 PNG en `GEN/<char>/`; los golpes de 07–09 permanecen
   válidos cuando falta su refinamiento 14/15.
5. Añadir `<char>` a `PROJECTILE_PROFILES` en `tools/pack_sf_character.py`. Comparar el cuadro
   `special-3` para que `offset` nazca en manos/objeto; declarar `light`, `medium`, `heavy`.
6. Empaquetar y registrar:

```powershell
python tools\pack_sf_character.py <char> <Titulo> --gen $Gen
```

   - `SfFighterId`: identidad, display name, JSON y PNG. Si reemplaza un `sharedSet` existente,
     quitar `isAlpha/sharedSet`; si es otra persona, crear un enum NUEVO y conservar el genérico.
   - `PlayerSkin`: carpeta/prefijo, conteos 6/6/8/5, las cuatro fracciones `0.703125f` y
     `uniform512Canvas=true`.
   - `SkinSelectorDialog.devOnlySkins`: agregar la skin del mundo. SF la toma automáticamente de
     `SfFighterId.entries`.
   - Copiar solo `Idle/Walk/Run/Special/Talk` desde `WORLD_<char>` a la carpeta mundial. Si se
     reemplaza una carpeta existente, retirar primero únicamente sus cuadros viejos sobrantes.
7. Ejecutar el validador obligatorio y después compilar:

```powershell
python tools\validate_sf_chroma_character.py <char> <Titulo> <CarpetaMundo> <prefijo>
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

   No entregar si el validador no termina en `VALIDACION OK` o si Gradle no termina en
   `BUILD SUCCESSFUL`. Finalmente actualizar esta guía y `07_OTHER_FEATURES.md` con conteos,
   identidad del especial, excepciones de hojas y resultado de KO.

## 1. Qué produce el recorte

`tools/slice_sf_chroma_sheets.py` (fondo croma → transparente, despill de borde,
alfa desde la máscara CRUDA — sin verde interior):
- **SF:** `GEN/<char>/*.png` — 118 cuadros dedicados + 5 `proj-*` (123 en total)
  (256², pies en (128,224); cada secuencia erguida calibra su mediana a 100 px y
  el packer impone **100 px por cuadro** en Idle, caminatas, golpes, patadas y reacciones,
  no solo en la mediana. Especiales, saltos, crouch, victoria y KO conservan su escala física;
  `proj-*` centrados en (128,128)).
  `crouch-1..3` impone alturas de silueta **90→80→68 px** y `crouch-turn-*` mantiene
  68 px: cambia la postura, no la escala anatómica, y evita que cada personaje parezca
  encogerse o crecer al agacharse.
  `hit-face/hit-stomach` se toman de la PRIMERA mitad de la secuencia (de pie);
  `special-1..4` salen de SPECIAL HEAVY; `backwards-1..6` de WALK BACKWARD (hoja 19).
- **Ataques ligeros legibles:** LIGHT PUNCH (hoja 06) y LIGHT KICK (hoja 08) eligen
  **guardia + cuadro intermedio de contacto**, nunca los dos extremos de la secuencia
  (a menudo ambos son guardia). Como defensa para assets futuros, el packer mide la diferencia
  visual del puño ligero; si queda casi estático, reutiliza los dos primeros cuadros de MEDIUM
  PUNCH antes de producir el sheet.
- **PROJECTILE tolerante:** lo ideal son 5 efectos (2 vuelo + 3 impacto). Los efectos dispersos
  de la fila inferior se recuperan por intervalos horizontales, conservando incluso chispas que
  serían demasiado pequeñas para el detector de cuerpos. Si GPT entrega realmente solo 4,
  se aceptan como `[0,1,1,2,3]`, duplicando únicamente el cuadro central para conservar el
  contrato de cinco slots sin inventar ni estirar otro asset.
- **Mundo:** `GEN/WORLD_<char>/{Idle,Walk,Run,Special,Talk}/<prefix><k>_<n>.webp`
  (lienzo 512²; TODAS las secuencias calibran su mediana a 360 px con una sola escala
  interna por secuencia, pies Y=456; `--world-ref`
  queda aceptado solo por compatibilidad y ya NO hereda tamaños viejos desiguales).
  Idle del mundo = IDLE RELAXED (hoja 18), NO el idle de guardia.
- **Alfa/pies exactos:** después de reescalar, el mundo recorta filas transparentes generadas por
  LANCZOS y compone RGBA con `alpha_composite` (una sola aplicación del alfa). Usar
  `paste(..., img)` multiplicaba el alfa como fuente+máscara y podía borrar 1–3 píxeles suaves
  del calzado; el validador exige que todos los cuadros terminen exactamente en Y=456.
- **Orientación KO automática:** compara continuidad de silueta+color entre cuadros tal cual
  y espejados. Si detecta una inversión fuerte al tocar el piso, escribe `_frame_meta.json`
  (`flipX` por cuadro); el packer lo pasa al JSON y la UI lo aplica de forma genérica.
- **Robustez:** grupos por FILAS con partición flexible (tolera que GPT dibuje ±1
  cuadro: aviso ⚠ y sigue); efectos DISPERSOS (confeti) → reintento con cierre 25 y
  partición por valle de densidad si dos cuadros se fusionan. El aviso se imprime como
  `AVISO` ASCII para no abortar en consolas Windows `cp1252`. Si falta exactamente un cuadro
  requerido, repite el vecino central más cercano tanto en SF como en mundo; faltantes mayores
  abortan. PROJECTILE 4→5 conserva su mapeo especializado.

## 2. Registro Prankedy (2026-07-16, hecho)

- 19/19 hojas OK (GPT dibujó 13/14 en HURT HEAD y 12/13 en HURT BODY — irrelevante,
  solo se usan 4). Sheet 2560×3328, 123 frames, 30 anims; mismas rutas → sin código SF.
- Mundo: `PrankedyPlayable` reemplazado (Idle 3→6 relajado, Walk 9→6, Run 8, Special 5
  + carpeta `Talk/` NUEVA aún sin cablear). Idle/Walk/Run/Talk median 360 px;
  `PlayerSkin.PRANKEDY` usa caja UI fija 512² y fracción común `0.703125`.
- KO: detector marcó `fall-4/fall-5` con `flipX`; ya no cambia de lado al quedar tendido.
- Intermedios y hojas fuente: `newSFAssets/Prankedy/` + `newSFAssets/GEN_prankedy_intermedio/`
  (incluye `_extra/`: HANDGUN/RIFLE ready-aim-walk, IDLE RELAXED, RUN y TALK; salto atrás,
  aterrizaje y SPECIAL L/M ya se integran directamente en SF).

## 2b. Registro Señor de la Tienda (2026-07-16, hecho)

- 19/19 hojas utilizables (CROUCH 8/9, HURT HEAD 15/14, HURT BODY 12/13; sobran los slots
  necesarios). Sheet dedicado `SenorTienda.png` 2560×3328 + `senortienda.json`: 123 frames,
  30 animaciones, proyectil propio de barrido/polvo; deja de usar `sharedSet` y pierde badge ALPHA.
- Mundo `SenorTienda`: Idle 3→6, Walk 4→6, Run 8, Special 6→5, Talk NUEVO; lienzos 512²,
  secuencias median 360 px; caja UI fija y fracción común `0.703125`.
- KO: el mismo detector genérico marcó `fall-4/fall-5` con `flipX`.
- Fuentes/intermedios nuevos: `newSFAssets/Prankedy/`, `newSFAssets/SenorTienda/` y
  `GEN_prankedy_senortienda_uniform_intermedio/`.

## 2c. Registro Rey Grupero (2026-07-16, hecho)

- Las 19 hojas de `newSFAssets/ReyGrupero/` se identificaron por título y renombraron
  `ReyGrupero_01..19`; 19/19 son utilizables (HURT HEAD/BODY pueden traer menos cuadros que
  el rótulo, pero superan los slots consumidos).
- Sheet dedicado `ReyGrupero.png` 2560×3328 + `reygrupero.json`: 123 frames, 30 animaciones;
  deja `sharedSet`, pierde ALPHA y usa escala SF fija (idle/caminatas ≈100 px; crouch 90→80→68).
- Mundo `ReyGrupero`: Idle 6, Walk 6, Run 8, Special 5, Talk 4; lienzos 512², cuerpo mediano
  360 px, `uniform512Canvas=true` y fracción común `0.703125`. Se eliminaron los antiguos
  `Walk 7/8` y `Special 6`, que ya no pertenecen al ciclo nuevo.
- KO: el detector no pidió `flipX`; esta decisión queda por cuadro en metadata y se repetirá
  automáticamente para futuros personajes.

## 2d. Registro Paparazzi 1 (2026-07-16, hecho)

- Las 19 hojas de `newSFAssets/Paparazzi1/` se renombraron **por el título dibujado, no por fecha
  de descarga** (la primera tanda llegó Crouch, Idle, Jump Up, Jump Start, Walk). 19/19 utilizables.
- Sheet dedicado `Paparazzi1.png` 2560×3328 + `paparazzi1.json`: 123 frames, 30 animaciones;
  deja `sharedSet`, pierde ALPHA y comparte las mismas alturas fijas. Su PROJECTILE llegó con
  4 efectos y se normalizó a 5 mediante el mapeo seguro descrito arriba.
- Mundo `PaparazziN1`: Idle 6, Walk 6, Run 8, Special 5, Talk 4; lienzos 512², cuerpo mediano
  360 px, `uniform512Canvas=true` y fracción común `0.703125`. Se eliminaron los antiguos
  `Walk 7/8` y `Run 9/10`.
- KO: el detector no pidió `flipX`; no se fuerza orientación cuando la continuidad ya es correcta.
- Intermedios de los cuatro personajes: `newSFAssets/GEN_prankedy_senortienda_rey_paparazzi_uniform_intermedio/`.

## 2e. Registro Paparazzi 5 (2026-07-16, hecho)

- Las 19 hojas de `newSFAssets/Paparazzi5/` se identificaron por título y renombraron
  `Paparazzi5_01..19`; todos los grupos llegaron con el conteo exacto salvo PROJECTILE 4/5,
  normalizado mediante el mapeo tolerante previsto.
- Sheet dedicado `Paparazzi5.png` 2560×3328 + `paparazzi5.json`: 123 cuadros, 30 animaciones
  y eventos de destello por fuerza. Deja `sharedSet`, pierde ALPHA y usa tamaño SF fijo.
- Mundo `PaparazziN5`: Idle 3→6, Walk 7→6, Run 10→8, Special 4→5 y Talk 4 nuevo; todo en
  lienzos 512², mediana corporal 360 px, `uniform512Canvas=true` y fracción común `0.703125`.
- KO no necesitó `flipX`. Fuentes en `newSFAssets/Paparazzi5/`; intermedios dentro del directorio
  histórico `GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio/`.

## 2f. Registro Policía Femenina CDMX (2026-07-16, hecho)

- Las 19 hojas de `newSFAssets/PoliciaFemeninoCDMX/` se identificaron por título y renombraron
  `PoliciaFemeninoCDMX_01..19`. WALK llegó 5/6 y se completó repitiendo el vecino central;
  CROUCH 8/9, HURT HEAD 12/14 y HURT BODY 12/13 conservan más cuadros que los slots consumidos.
- Sheet dedicado `PoliciaCDMX.png` 2560×3328 + `policiacdmx.json`: 123 cuadros, 30 animaciones
  y proyectil luminoso por fuerza. Deja `sharedSet`, pierde ALPHA y usa tamaño SF fijo.
- El anclaje genérico de SPECIAL separa visualmente cuerpo y efecto por densidad vertical: la
  policía queda centrada en sus pies mientras el haz ancho se extiende y recorta hacia adelante.
- Mundo `PoliciaCDMX`: Idle 4→6, Walk 7→6, Run 11→8, Special 5 y Talk 4 nuevo; lienzos 512²,
  cuerpo mediano ≈360 px, pies Y=456, `uniform512Canvas=true` y fracción común `0.703125`.
- KO no necesitó `flipX`. Intermedios dentro del directorio histórico
  `GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio/`.

## 2g. Registro Policía Masculino CDMX (2026-07-16, hecho)

- Las 19 hojas de `newSFAssets/PoliciaMasculinoCDMX/` se identificaron por título y renombraron
  `PoliciaMasculinoCDMX_01..19`; HURT HEAD llegó 13/14, pero conserva de sobra los cuatro cuadros
  utilizados por el juego.
- Sheet dedicado `PoliciaCDMXHombre.png` 2560×3328 + `policiacdmxhombre.json`: 123 cuadros,
  30 animaciones y eventos de proyectil por fuerza. Es una identidad separada de la policía mujer.
- PROJECTILE contiene cinco estados reales (dos de avance y tres de impacto). El recuperador de
  efectos dispersos evita perder el último fade de partículas; SPECIAL conserva el cuerpo centrado
  aunque el haz se extienda ampliamente hacia adelante.
- Mundo `PoliciaMasculinoCDMX`: Idle 6, Walk 6, Run 8, Special 5 y Talk 4; lienzos 512²,
  cuerpo mediano ≈360 px, pies Y=456, `uniform512Canvas=true` y fracción común `0.703125`.
- KO no necesitó `flipX`. Intermedios dentro del directorio histórico
  `GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio/`.

## 2h. Registro Paramédico Cruz Roja (2026-07-16, hecho)

- Las 19 hojas de `newSFAssets/ParamedicoCruzRoja/` se identificaron por título y renombraron
  `ParamedicoCruzRoja_01..19`. RUN llegó 9/8 y se muestreó uniformemente a ocho cuadros para el
  mundo; LIGHT/HEAVY KICK llegaron 5/6 y usan la repetición central segura. HURT BODY 12/13
  conserva de sobra los cuatro cuadros consumidos.
- Sheet dedicado `ParamedicoCruzRoja.png` 2560×3328 + `paramedicocruzroja.json`: 123 cuadros,
  30 animaciones y descarga/desfibrilador con cinco efectos eléctricos reales. Es una identidad
  separada del `PARAMEDICO` genérico compartido.
- Mundo `ParamedicoCruzRoja`: Idle 6, Walk 6, Run 8, Special 5 y Talk 4; lienzos 512²,
  cuerpo mediano ≈360 px, pies Y=456, `uniform512Canvas=true` y fracción común `0.703125`.
- El detector de orientación marcó únicamente `fall-5.flipX`, corrigiendo el lado del cuerpo al
  quedar tendido. Intermedios dentro del directorio histórico
  `GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio/`.

## 2i. Registro Policía Granadero Hombre CDMX (2026-07-17, hecho)

- Las 19 hojas de `newSFAssets/PoliciaGranaderoMasculinoCDMX/` se identificaron por título. La
  primera tanda llegó fuera de orden (Crouch, Jump Start/Land, Walk/Run, Idle/Turn, Jump) y se
  renombró correctamente como `PoliciaGranaderoMasculinoCDMX_01..19` por contenido.
- Sheet dedicado `PoliciaGranaderoHombre.png` 2560×3328 + `policiagranaderohombre.json`:
  123 cuadros, 30 animaciones y pulso sónico de megáfono con cinco efectos. Es una identidad
  separada del `GRANADERO` genérico compartido.
- JUMP FORWARD llegó 8/7 y se muestreó a siete; HURT BODY llegó 12/13, pero conserva de sobra
  los cuatro cuadros utilizados. La hoja 04 colocó ambos grupos en una sola fila; el recortador
  ahora aplica un corte A→B contractual cuando esto ocurre.
- Mundo `PoliciaGranaderoMasculinoCDMX`: Idle 6, Walk 6, Run 8, Special 5 y Talk 4; lienzos
  512², cuerpo mediano 360 px, pies Y=456, `uniform512Canvas=true` y fracción común `0.703125`.
- KO no necesitó `flipX`. El validador nuevo `tools/validate_sf_chroma_character.py` confirmó
  sheet/JSON/mundo y ausencia de `STREETFIGHTER/GEN` dentro del APK.

## 2j. Registro ESCOMBOY con 18 hojas (2026-07-17, hecho)

- `newSFAssets/ESCOMBOY/` contiene 18 hojas reales: están 01–14 y 16–19; falta únicamente
  `15 MEDIUM KICK REFINED + HEAVY KICK REFINED`. Se conservó el hueco 15 y NO se desplazaron
  las hojas de armas/Idle relajado. Las patadas base de 08/09 alimentan los mismos slots finales.
- Sheet dedicado `EscomBoy.png` 2560×3328 + `escomboy.json`: 123 cuadros, 30 animaciones y
  especial tecnológico de laptop/portal con proyectil USB de cinco efectos. `SfFighterId.ESCOMBOY`
  dejó `sf_template.json`, `RUNTIME/EscomBoy.png`, `isAlpha` y `sharedSet`.
- RUN llegó 7/8 y se completó con el vecino central; HURT BODY llegó 12/13 pero solo usa cuatro.
  KO no necesitó `flipX`. Todas las poses erguidas empaquetadas validan 100 px exactos.
- Mundo reemplazó los ciclos históricos grandes `escomboyIdle/Walk/Run/Special` (16/25/16/16)
  por 6/6/8/5 y añadió `escomboyTalk` 4. Se conserva la convención PLAYER sin subcarpeta:
  lienzos 512², cuerpo mediano 360 px, pies Y=456, `uniform512Canvas=true`, fracción `0.703125`.
- Validación especial para protagonistas:
  `python tools/validate_sf_chroma_character.py escomboy EscomBoy escomboy escomboy_`
  ` --world-base SPRITES/PLAYER --flat-world-folders`.

## 2k. Registro ESCOMGIRL (2026-07-17, hecho)

- Las 19 hojas de `newSFAssets/ESCOMGIRL/` están completas y se renombraron
  `ESCOMGIRL_01..19` por título. Las hojas refinadas 14/15 sí existen y sobrescriben
  intencionalmente golpes y patadas base.
- Sheet dedicado `EscomGirl.png` 2560×3328 + `escomgirl.json`: 123 cuadros, 30 animaciones y
  especial tecnológico de dispositivo/portal con cinco efectos. `SfFighterId.ESCOMGIRL` dejó
  `sf_template.json`, `RUNTIME/EscomGirl.png`, `isAlpha` y `sharedSet`, conservando el mismo enum.
- CROUCH llegó 8/9, WALK 5/6 y RUN 7/8; cada faltante único usa el vecino central. HURT HEAD
  llegó 15/14 y HURT BODY 12/13, pero ambos conservan suficientes cuadros para los cuatro usados.
  KO no necesitó `flipX`; las poses erguidas empaquetadas validan 100 px exactos.
- Mundo reemplazó `escomgirlIdle/Walk/Run/Special` 6/5/4/6 por 6/6/8/5 y añadió
  `escomgirlTalk` 4. Mantiene la convención PLAYER plana; lienzos 512², cuerpo 360 px,
  pies Y=456, `uniform512Canvas=true` y fracción común `0.703125`.
- Validación: `python tools/validate_sf_chroma_character.py escomgirl EscomGirl escomgirl`
  ` escomgirl_ --world-base SPRITES/PLAYER --flat-world-folders` → `VALIDACION OK`.

## 2l. Registro Policía Granadero Mujer CDMX (2026-07-17, hecho)

- Las 19 hojas de `newSFAssets/PoliciaGranaderoFemeninoCDMX/` llegaron en tandas invertidas:
  las primeras diez eran 06–15, las siguientes cinco 01–05 y las últimas cuatro 16–19. Se
  renombraron `PoliciaGranaderoFemeninoCDMX_01..19` estrictamente por título.
- Sheet dedicado `PoliciaGranaderoMujer.png` 2560×3328 + `policiagranaderomujer.json`:
  123 cuadros, 30 animaciones y proyectil propio de cápsula/humo rosa con cinco efectos. Es una
  identidad separada de Policía Granadero Hombre y del `GRANADERO` genérico compartido.
- HURT HEAD llegó 13/14, HURT BODY 12/13 y KO 7/6; hay suficientes cuadros para los slots
  consumidos y KO se muestreó a cinco. No necesitó `flipX`; las poses erguidas validan 100 px.
- Mundo `PoliciaGranaderoFemeninoCDMX`: Idle 6, Walk 6, Run 8, Special 5 y Talk 4; lienzos
  512², cuerpo mediano 360 px, pies Y=456, `uniform512Canvas=true` y fracción `0.703125`.
- El validador detectó que `Special/pgf_s_5` terminaba inicialmente en Y=453 por alfa duplicado;
  el compositor mundial se corrigió de forma genérica y la secuencia completa volvió a Y=456.
- Validador: `python tools/validate_sf_chroma_character.py policiagranaderomujer`
  ` PoliciaGranaderoMujer PoliciaGranaderoFemeninoCDMX pgf_`.

## 2m. Registro ESCOMROBOT (2026-07-17, hecho)

- Las 19 hojas de `newSFAssets/ESCOMROBOT/` están completas y se renombraron
  `ESCOMROBOT_01..19` por título; las hojas refinadas 14/15 sobrescriben golpes/patadas base.
- Sheet dedicado `Robot.png` 2560×3328 + `robot.json`: 123 cuadros, 30 animaciones y pulso
  energético con cinco efectos. `SfFighterId.ROBOT` dejó `sf_template.json`, `RUNTIME/Robot.png`,
  `isAlpha` y `sharedSet`, conservando enum, desbloqueos y progreso arcade.
- CROUCH llegó 8/9 y HURT BODY 12/13; el faltante único usa el vecino central y HURT conserva
  suficientes cuadros. KO no necesitó `flipX`; todas las poses erguidas validan 100 px.
- Mundo reemplazó los cuatro ciclos históricos de 25 cuadros (`robotIdle/Walk/Run/Special`)
  por 6/6/8/5 y añadió `robotTalk` 4. Convención PLAYER plana, lienzos 512², cuerpo mediano
  360 px, pies Y=456, `uniform512Canvas=true` y fracción común `0.703125`.
- Validación: `python tools/validate_sf_chroma_character.py robot Robot robot robot_`
  ` --world-base SPRITES/PLAYER --flat-world-folders` → `VALIDACION OK`.

## 2n. Pase de combate completo (2026-07-16, hecho)

- El recortador ya no comprime los golpes al mínimo del template: LIGHT PUNCH 4, MEDIUM/HEAVY
  PUNCH 6, LIGHT/HEAVY KICK 6 y MEDIUM KICK 5. Las hitboxes solo existen en los cuadros de
  contacto; preparación y recuperación no producen golpes fantasma.
- JUMP START usa 2 cuadros, JUMP LAND 3 y JUMP BACKWARD sus 7 cuadros propios. STUN usa 1→2→3.
- SPECIAL LIGHT/MEDIUM/HEAVY usa 5 cuadros distintos por fuerza. `pack_sf_character.py` agrega
  `events.projectile.<strength>` con cuadro de salida, offset y escala; Kotlin lo carga de forma
  genérica. Las trece identidades quedan visibles: confeti, polvo de escoba, ondas de megáfono,
  humo rosa, destellos fotográficos, haces policiales, descarga médica y energía tecnológica ESCOM.
- El sheet dedicado pasa a 123 cuadros en rejilla 10×13 (`2560×3328`); los 123 quedan referenciados.
  `pack_sf_character.py --gen <ruta>` permite empaquetar desde intermedios externos sin devolver
  `STREETFIGHTER/GEN/` al APK.
- Intermedios vigentes: `newSFAssets/GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio/`.
  HANDGUN/RIFLE siguen reservados: las hojas contienen pose corporal sin arma y requieren una capa
  de arma antes de poder mostrarse correctamente.
- Corrección de tamaño final: HURT ahora calcula la escala usando solo los cuatro cuadros elegidos
  (no también los descartados) y el packer valida un rango exacto de 100–100 px en todas las poses
  erguidas. Si una futura hoja vuelve a introducir zoom variable, el empaquetado falla en vez de
  permitir que llegue al modo SF.
- Objetos y efectos no se usan para medir la anatomía: pueden sobresalir sin encoger al cuerpo.
  Para una irregularidad interna de una hoja, `_frame_meta.json` admite `{"<frame>":{"scale":0.95}}`;
  el packer aplica esa corrección alrededor de los pies. QA usa este mecanismo/perfil para los
  cuadros inicial/final de SPECIAL HEAVY de Rey Grupero, que venían con zoom corporal adicional.
- En el selector, cada tarjeta recorre el Idle completo cargando solo sus regiones del sheet
  (no la imagen completa). Durante `RONDA / PELEA` el input, el movimiento y el reloj siguen
  bloqueados, pero ambos peleadores también recorren el Idle; ya no quedan congelados antes
  de iniciar el combate.

## 3. Pendientes que NO cubre esta guía

Escenario/HUD/sonidos/proyectil genérico del tema (`POW_THEME`) siguen en
`ASSETS_STREETFIGHTER_MIGRACION.md` y el plan aprobado (prompts §3d/§3e de la sesión
2026-07-16). El proyectil genérico para COMPARTIDOS sigue FALTANDO (release: fireball
del tema apunta a Ken.png, debug-only — ver 07/09).

## 4. PROMPT MAESTRO (pegar como 1er mensaje, con las 9 referencias adjuntas)

Sustituir PRANKEDY por el personaje que toque (y su "diseño canónico" + objeto
característico). Para personajes SIN armas, omitir hojas 16/17/19B y ajustar el catálogo.

```text
Estas nueve imágenes son referencias permanentes durante toda esta conversación para
regenerar sprites originales de mi juego de pelea 2D "Politécnico Open World: HUELUM VS. GOYA".

ROLES DE LAS REFERENCIAS
- prankedy.png, senorTienda.png, paparazzi1.png, paparazzi5.png y grupero.png: referencias de
  identidad, rostro, ropa, proporciones, paleta y estilo de los cinco personajes jugables actuales.
- paramedico1.png, policia1.png, policia2.png y granaderos1.png: personajes reservados.
  No los dibujes salvo que yo lo solicite expresamente.

REGLA PRINCIPAL DE CONSISTENCIA
No generes variantes visuales alternativas de un personaje. Cuando apruebe la primera
representación, esa versión queda BLOQUEADA como referencia de identidad. Todas las hojas
posteriores conservan exactamente: el mismo rostro, anatomía, proporciones, escala, ropa,
paleta, peinado, sombreado, nivel de detalle, grosor de contorno y la misma interpretación
visual. Las tandas posteriores agregan animaciones nuevas del mismo diseño.

ESTILO PERMANENTE
- Arte original pixel art 16-bit para videojuego de pelea 2D, vista lateral.
- Contorno oscuro limpio de ~1 píxel. Formas legibles en pantalla de celular.
- Paleta limitada y consistente. Sombreado pixel art definido, sin apariencia de pintura digital.
- Nada identificable de Street Fighter u otra franquicia. No copies sprites comerciales.
- Cada spreadsheet: UN solo personaje y exactamente DOS grupos de animación.
- Cuadros de izquierda a derecha; cantidad EXACTA de cuadros por grupo; sin cuadros extra;
  sin collages; sin cuadros individuales salvo corrección puntual.

ORIENTACIÓN
Siempre mirando a la DERECHA. Excepciones: IDLE TURN y CROUCH TURN pueden mostrar tres
cuartos o espalda durante el giro (nunca terminan a la izquierda). JUMP BACKWARD se desplaza
hacia atrás sin dejar de mirar a la derecha (no voltees el sprite). Las poses con armas
también miran a la derecha.

FONDO Y COMPOSICIÓN
- Fondo verde croma puro, plano y uniforme: #00FF00. Nunca uses #00FF00 dentro del personaje,
  objetos ni efectos.
- Sin textura, degradado, iluminación de fondo, suelo, sombras, reflejos, escenario, marcos,
  HUD, logotipos, retratos, paletas exhibidas, accesorios sueltos, marcas de agua, personajes
  adicionales ni texto descriptivo.
- Solo dos títulos pequeños en amarillo por spreadsheet: uno por grupo.

REJILLA Y ESCALA
- Espacios amplios y uniformes entre cuadros (recorte automático); ningún cuadro toca otro
  ni los títulos.
- Escala idéntica en toda la producción. De pie ~190 píxeles de alto.
- Pies de los cuadros terrestres en la misma línea base. En saltos, misma escala del cuerpo.
- No agrandar para llenar espacio. No recortar sombrero/cabello/manos/pies/efectos.
- Mismo tamaño de cabeza, torso y extremidades en todas las acciones.

FLUJO DE GENERACIÓN
El catálogo son 19 spreadsheets. Genera por tandas (5, luego 10, luego 4). Tras cada tanda:
detente, espera revisión, corrige solo lo señalado, mantén la variante aprobada.

DISEÑO CANÓNICO DEL PERSONAJE  ← (editar por personaje)
[PRANKEDY: cabello largo RIZADO y esponjado (nunca rastas/trenzas), sombrero negro de ala
ancha con banda ajedrezada, cubrebocas negro, chamarra negra, playera amarilla, pantalón
negro, tenis negros. Objeto característico: tanque de gas de broma que dispara CONFETI —
solo en SPECIAL/PROJECTILE, cómico, sin fuego/explosión/gas tóxico.]

CATÁLOGO (2 grupos por hoja; cuadros exactos):
01 Idle_Turn: IDLE 6 (guardia, ciclo respiración) + IDLE TURN 4 (giro, termina a la derecha)
02 Crouch_CrouchTurn: CROUCH 9 (bajar→cuclillas→mantener→subir; cabeza/sombrero SIN cambiar
   de tamaño) + CROUCH TURN 4 (giro agachado)
03 Walk_Run: CAMINAR 6 (ciclo real, brazos contrarios) + CORRER 8 (alternancia real de
   piernas, fase de suspensión, torso inclinado)
04 JumpStart_Land: JUMP START 2 (preparación+despegue) + JUMP LAND 3 (contacto, compresión,
   recuperación; sin polvo/sombra/suelo)
05 JumpUp_Air: JUMP UP 6 (vertical completo) + JUMP FORWARD 7 (arco legible)
06 JumpBack_LightPunch: JUMP BACKWARD 7 (hacia atrás mirando a la derecha) + LIGHT PUNCH 4
   (jab: guardia, extensión, contacto, regreso)
07 Medium_HeavyPunch: MEDIUM PUNCH 6 + HEAVY PUNCH 6 (claramente distinto, más rotación/peso)
08 Light_MediumKick: LIGHT KICK 6 (baja y rápida) + MEDIUM KICK 5 (altura media)
09 HeavyKick_HurtHead: HEAVY KICK 6 (amplia, con peso) + HURT HEAD 14 (reacción progresiva
   en cabeza, de leve a fuerte; sin sangre ni atacante)
10 HurtBody_Stun: HURT BODY 13 (golpes al abdomen, progresivo) + STUN 3 (aturdido de pie;
   estrellitas amarillas opcionales, nunca verdes)
11 SpecialLight_Medium: SPECIAL LIGHT 5 + SPECIAL MEDIUM 5 (misma familia, más impulso)
12 SpecialHeavy_Projectile: SPECIAL HEAVY 5 (ejecución máxima del objeto) + PROJECTILE 5
   (2 de vuelo + 3 de impacto, SIN el personaje; efecto cómico, nunca #00FF00)
13 Victory_KO: VICTORY 6 (celebración propia, sin objeto/texto) + KO 6 (caída progresiva
   hasta quedar tendido; sin sangre)
14 Refuerzo_Punos: MEDIUM PUNCH REFINED 6 + HEAVY PUNCH REFINED 6 (repetición mejorada)
15 Refuerzo_Patadas: MEDIUM KICK REFINED 5 + HEAVY KICK REFINED 6
16 HandgunReady_Aim: HANDGUN READY 5 + HANDGUN AIM 5 (pistola INVISIBLE: manos colocadas,
   espacio limpio para añadir el arma como capa; sin disparo/retroceso/fogonazo)
17 RifleReady_Aim: RIFLE READY 6 + RIFLE AIM 6 (carabina invisible, culata al hombro)
18 IdleRelax_Talk: IDLE RELAXED 6 (SIN guardia: postura casual de calle, respiración) +
   TALK 4 (gesticulando, en bucle; sin objetos)
19 WalkBackward_HandgunWalk: WALK BACKWARD 6 (retroceder EN GUARDIA cubriéndose, no es la
   caminata invertida) + HANDGUN WALK 6 (avanzar apuntando pistola invisible)

Comienza ÚNICAMENTE con la TANDA 1 (hojas 01 a 05, cinco imágenes separadas). Al terminar,
detente y espera mi revisión.
```

Mensajes de continuación: "Apruebo la TANDA N (versión BLOQUEADA). Genera las hojas
06–15 (diez imágenes separadas); si no caben en una respuesta, continúa sin esperar
confirmación hasta completarlas." → luego 16–19. Corrección puntual: "Corrige ÚNICAMENTE
<hoja>: [problema]. Regenera solo ese spreadsheet manteniendo la variante aprobada."
