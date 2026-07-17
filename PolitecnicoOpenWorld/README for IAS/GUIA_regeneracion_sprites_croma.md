# GUÍA · Regeneración de sprites por HOJAS CROMA (SF + mundo, un solo arte) — 2026-07-16

> **Qué es:** el procedimiento VIGENTE para regenerar el arte de un peleador de
> "HUELUM VS. GOYA" **y** su set del mundo abierto desde CERO con ChatGPT Images,
> usando hojas con **fondo croma verde #00FF00** y 2 grupos de animación por hoja.
> Sustituye al "modo simple" de `GUIA_generacion_assets_SF.md` (fondo negro) para
> personajes nuevos. **Prankedy y Señor de la Tienda ya se regeneraron así (2026-07-16)**.
> Canon Prankedy: cabello RIZADO (no rastas), idle del mundo SIN guardia.
> Esta guía es AUTOSUFICIENTE: contiene el prompt maestro completo.

## 0. Resumen del flujo (por personaje)

1. Nueva conversación en ChatGPT: subir las 9 referencias + PROMPT MAESTRO (§4).
2. GPT genera 19 hojas en tandas (5 → 10 → 4); revisar cada tanda y corregir puntuales.
3. Descargar TODO a `newSFAssets/<Personaje>/` (raíz del REPO, fuera del proyecto Android).
4. Identificar/renombrar las hojas (los nombres de descarga de GPT no sirven): por los
   títulos amarillos — a mano o con OCR (`tesseract` sobre la máscara amarilla).
   Nombres canónicos: `<Personaje>_01_Idle_Turn.png` … `_19_WalkBackward_HandgunWalk.png`
   (catálogo exacto en §5).
5. Recortar en orden ESTRICTO 01→19 (la hoja 01 SIEMPRE primero, fija la escala;
   las hojas 14/15 `REFINED` sobrescriben intencionalmente puños/patadas de 07–09):
   `python3 tools/slice_sf_chroma_sheets.py "newSFAssets/<P>/<P>_01_Idle_Turn.png" <char>`
   … y así con las 19 (basta un for; `--list` = dry-run con conteos).
6. Empaquetar SF: `python3 tools/pack_sf_character.py <char> <Titulo>` →
   `STREETFIGHTER/IMAGES/<Titulo>.png` + `DATA/<char>.json`. Cero código si el
   personaje YA tenía sheet dedicado; si era COMPARTIDO (sharedSet), además quitar
   su `sharedSet` en `SfFighterId` y apuntar jsonAsset/spriteAsset a los nuevos.
7. Mundo: copiar `GEN/WORLD_<char>/{Idle,Walk,Run,Special,Talk}` sobre su set en
   `SPRITES/` (borrar frames viejos SOBRANTES si el conteo bajó) y actualizar en
   `PlayerSkin.kt`: `idleFrames/walkFrames/runFrames/specialFrames` + las
   `*BodyFraction` re-MEDIDAS (mediana de alto_opaco/512 por animación).
8. **⚠️ SACAR `STREETFIGHTER/GEN/` de assets al terminar** (viaja al APK si se queda):
   `mv app/src/main/assets/STREETFIGHTER/GEN newSFAssets/GEN_<char>_intermedio`.
   Ahí quedan también `_extra/` (armas, talk, jump land, specials L/M) y `WORLD_*`.
9. Rebuild + probar: selector SF (preview idle-1), pelea completa, especial/proyectil,
   y en el mundo idle/caminar/correr/especial del skin.

## 1. Qué produce el recorte

`tools/slice_sf_chroma_sheets.py` (fondo croma → transparente, despill de borde,
alfa desde la máscara CRUDA — sin verde interior):
- **SF:** `GEN/<char>/*.png` — los 77 nombres del template + `proj-*` si hay
  (256², pies en (128,224), cuerpo base FORZADO a 100 px también por el packer;
  `proj-*` centrados en (128,128)).
  `hit-face/hit-stomach` se toman de la PRIMERA mitad de la secuencia (de pie);
  `special-1..4` salen de SPECIAL HEAVY; `backwards-1..6` de WALK BACKWARD (hoja 19).
- **Mundo:** `GEN/WORLD_<char>/{Idle,Walk,Run,Special,Talk}/<prefix><k>_<n>.webp`
  (lienzo 512²; estándar ABSOLUTO común: cuerpo base 360 px, pies Y=456; `--world-ref`
  queda aceptado solo por compatibilidad y ya NO hereda tamaños viejos desiguales).
  Idle del mundo = IDLE RELAXED (hoja 18), NO el idle de guardia.
- **Orientación KO automática:** compara continuidad de silueta+color entre cuadros tal cual
  y espejados. Si detecta una inversión fuerte al tocar el piso, escribe `_frame_meta.json`
  (`flipX` por cuadro); el packer lo pasa al JSON y la UI lo aplica de forma genérica.
- **Robustez:** grupos por FILAS con partición flexible (tolera que GPT dibuje ±1
  cuadro: aviso ⚠ y sigue); efectos DISPERSOS (confeti) → reintento con cierre 25 y
  partición por valle de densidad si dos cuadros se fusionan. El aviso se imprime como
  `AVISO` ASCII para no abortar en consolas Windows `cp1252`. Aborta solo si un grupo trae
  MENOS cuadros que slots SF necesita.

## 2. Registro Prankedy (2026-07-16, hecho)

- 19/19 hojas OK (GPT dibujó 13/14 en HURT HEAD y 12/13 en HURT BODY — irrelevante,
  solo se usan 4). Sheet 2560×2304, 82 frames, 30 anims; mismas rutas → sin código SF.
- Mundo: `PrankedyPlayable` reemplazado (Idle 3→6 relajado, Walk 9→6, Run 8, Special 5
  + carpeta `Talk/` NUEVA aún sin cablear). `PlayerSkin.PRANKEDY`: conteos y fracciones
  actualizados con el estándar 360 px (idle .695, walk .603, run .494, special .475).
- KO: detector marcó `fall-4/fall-5` con `flipX`; ya no cambia de lado al quedar tendido.
- Intermedios y hojas fuente: `newSFAssets/Prankedy/` + `newSFAssets/GEN_prankedy_intermedio/`
  (incluye `_extra/`: HANDGUN/RIFLE ready-aim-walk, JUMP BACKWARD, JUMP LAND,
  SPECIAL LIGHT/MEDIUM, IDLE RELAXED, TALK — para futuras features: armas por capas, plática).

## 2b. Registro Señor de la Tienda (2026-07-16, hecho)

- 19/19 hojas utilizables (CROUCH 8/9, HURT HEAD 15/14, HURT BODY 12/13; sobran los slots
  necesarios). Sheet dedicado `SenorTienda.png` 2560×2304 + `senortienda.json`: 82 frames,
  30 animaciones, proyectil propio de barrido/polvo; deja de usar `sharedSet` y pierde badge ALPHA.
- Mundo `SenorTienda`: Idle 3→6, Walk 4→6, Run 8, Special 6→5, Talk NUEVO; lienzos 512²,
  cuerpo base 360 px. Fracciones: idle .683, walk .583, run .534, special .627.
- KO: el mismo detector genérico marcó `fall-4/fall-5` con `flipX`.
- Fuentes/intermedios: `newSFAssets/SenorTienda/` + `GEN_senortienda_intermedio/`.

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
