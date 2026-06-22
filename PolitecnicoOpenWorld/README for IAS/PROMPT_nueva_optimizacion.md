# PROMPT para nueva conversación con una IA — Politécnico Open World (POW)

> Copia/pega esto al iniciar una sesión con un asistente (Claude, Gemini, etc.) y adjunta la carpeta
> `README for IAS`. Última actualización: **2026-06-21**.

---

## Contexto

Estás ayudándome con **"Politécnico Open World" (POW)**: un juego Android 2D top-down sobre mapas reales
(Kotlin + Jetpack Compose + MVVM estricto por feature). El repo está en
`C:\Users\<usuario>\AndroidStudioProjects\PolitecnicoOpenWorld\` y el módulo Android cuelga de
`…\PolitecnicoOpenWorld\PolitecnicoOpenWorld\` (de ahí cuelgan `app/` y `README for IAS/`).

**LÉELO PRIMERO:** la carpeta `README for IAS` (archivos **00–09** + los docs de trabajo) es el
**contexto COMPLETO** del proyecto y reemplaza al código. Úsala para saber qué `.kt` tocar sin leer todo
el repo (tablas "Key files" en 04/05/06/07). Si necesitas un `.kt` no documentado, pídemelo;
**no inventes su contenido.** El README **público** (visión general) vive en la RAÍZ del repo.

Docs de trabajo (además de 00–09):
- `ANALISIS_codigo.md` — informe de tamaños, duplicación, MVVM, perf, mejoras priorizadas.
- `REVISION_repo.md` — revisión del repo + seguridad (keystore NO comprometido, servidores Node).
- *(La de-dup de gemelos del VM ya terminó; su registro quedó consolidado en 09 §12.)*

---

## Estado actual (2026-06-21)

- **✅ i18n player-facing COMPLETA.** Todo el texto de UI visible al jugador está en
  `res/values/strings.xml` (ES) + `res/values-en/strings.xml` (EN), paridad 1:1. Migrados: main_menu,
  settings, map_exterior (HUD/misión/objetivo/Prankedy), campaña, interiores (zombi + ESCOM + transit +
  diseñadores), MainActivity. **Pendiente menor:** `CampaignObjective.title/description` → `@StringRes`.
- **✅ De-dup de los 8 gemelos miembro-vs-extensión del VM COMPLETA.** Ya no hay copias divergentes vivas.
  `WorldMapGameLoop.kt` es un tombstone (el game loop es solo el miembro `startGameLoop`, con audio).
  La cadena de routing (`updateDestinationRoute`+`calculateRouteOnNetwork`) se dejó SIN de-dup a propósito
  (es interdependiente y rompía la navegación). Ver `09 §12`.
- **✅ Audio del game loop activo** (pasos/correr/coche/zombi) + fix de carga async de `SoundPool`.
- **Tamaños:** solo 5 archivos >1000 líneas — `WorldMapViewModel`(2114), `NativeOsmMap`(1460),
  `WorldMapScreen`(1326), `MainActivity`(1064), `ZombieGameScreen`(1035). Objetivo "<1500" sin cumplir
  solo en el VM; bajarlo más requiere extracción estructural extra (NO urgente).

### Pendiente / opcional (el dueño decide)
1. `CampaignObjective.title/description` → `@StringRes` (i18n total del objetivo de campaña).
2. Base común **Metro ⇄ Metrobús** (`TransitInteriorViewModel`/`TransitStationScreen`): la mayor
   duplicación estructural restante. Esfuerzo medio/alto.
3. Extraer el `NavHost` de `MainActivity` a `AppNavGraph.kt` (opcional, bajo valor).
4. Más extracción del VM a parciales nuevos (sin crear gemelos), si se quiere bajar de 1500.

---

## Reglas al escribir código (resumen de 09)

- **MVVM estricto.** Estado inmutable: `_state.update { it.copy(...) }`. Las Views solo observan con
  `collectAsState()` y emiten intenciones; **nunca** tocan repos/DAOs (excepción sancionada: leer
  `SettingsRepository` una vez al entrar para developerMode/skin/name).
- Comentarios y strings de UI **en español**. Strings de UI nuevos → `strings.xml` (ES) **y** `values-en/`
  (EN), paridad 1:1. En Composables `stringResource(R.string.x[, args])`; en Activity `getString(...)`;
  en lambdas `onClick` **hoistea** el `stringResource` a un `val` antes. **Apóstrofes en XML van como `\'`**
  y `<` como `&lt;` (si no, AAPT2 falla: "Can not extract resource from ParsedResource").
- **Gotcha miembro vs extensión:** los parciales `WorldMap*.kt` son extensiones del VM. Si una función
  existe como miembro privado del VM Y como extensión homónima, **gana el miembro**. (Los 8 gemelos que
  había YA se de-duplicaron; no crees nuevos.) Las extensiones solo ven miembros `internal`/`public`.
- **Verifica balance de llaves/paréntesis por archivo CON la herramienta de lectura del editor**, no con
  la terminal: el shell del sandbox a veces sirve **copias truncadas/stale** de archivos grandes
  (`.kt`/`strings.xml`) → da conteos falsos. Pasos pequeños, UNO POR ARCHIVO.
- **CRLF:** los archivos fuente usan CRLF; al editar consérvalo (no uses `sed`/escrituras de shell sobre
  `.kt` — corrompen). Archivos nuevos quedan en LF; normaliza en Android Studio o déjalos (compila igual).
- **NO PUEDES COMPILAR tú:** entrega cambios listos para "Rebuild Project". Para cambios riesgosos
  (de-dup destructivo, refactors estructurales): **UNO POR CICLO**, yo compilo y pruebo entre cada uno.
  Antes de borrar un miembro gemelo, **revisa TODA su cascada** (qué llama y si esos callees son gemelos
  divergentes — eso rompió la cadena de routing).
- Mantén separadas **CAMPAÑA ⇄ MUNDO ABIERTO** y **ZOMBIS ⇄ INTERIORES**. `package` == carpeta; agrupa por
  feature/dominio (`features/<f>/{ui,viewmodel,models}`, `domain/models/{map,ai,campaign,zombie}`,
  `data/{repository,cache,auth}`). Package-moves SIEMPRE con el refactor **Move de Android Studio**, nunca a mano.

## Protocolo de docs (archivo 09)
Al terminar cualquier cambio de comportamiento/estructura: dime qué líneas de los **docs 00–09** hay que
actualizar (o actualízalas) y, si el cambio es user-facing, del **README público** (bilingüe EN+ES) de la
RAÍZ del repo.

## Estilo
Respuestas concisas y directas, sin relleno.
