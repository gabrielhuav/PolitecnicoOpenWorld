# QA de La Planchada — PR #143

Actualización: 2026-11-06. Sustituye el material de prueba del personaje anterior.
Las capturas y los resultados de septiembre no constituyen evidencia de esta versión.

## Contrato e integración

- Identidad visual: La Planchada es una enfermera espectral del IMSS, con uniforme blanco, detalles verdes y apariencia fantasmal coherente en todas las poses.
- Peleador `LA_PLANCHADA`, atlas único `STREETFIGHTER/IMAGES/LaPlanchada.webp` y datos `STREETFIGHTER/DATA/laplanchada.json`.
- Cada celda SF es RGBA 256×256, centro x=128 y pies y=224; cuerpo erguido de 100 px. Agachados, poses aéreas y KO conservan su escala anatómica. Los proyectiles tienen origen `[128,128]`.
- Mundo libre: `PlayerSkin.LA_PLANCHADA`, 25 recortes derivados del mismo arte bajo `SPRITES/NPC/LaPlanchada/`, prefijo `lpl_`: 6 Idle, 6 Walk, 8 Run y 5 Special. Lienzos 512×512, cuerpo erguido de 360 px y pies y=456.
- Arcade: rival del grupo de leyendas, escalones 11–13 en orden aleatorio, antes de Tzitzimime, Presidenta y Yoalli. La campaña tiene 16 combates. La Planchada no pertenece a los peleadores iniciales: la victoria en Medio o Difícil aplica el desbloqueo existente.
- Escenario propio: Escuela Superior de Medicina IPN, `fondo_esm_anim.webp` y sus variantes `_noche_1_anim.webp` y `_noche_2_anim.webp`.
- Audio: comparte el pack de La Llorona (`special_llorona_attack`, `hurt`, `power`, `win`) y su archivo de poder como fallback. No se copian archivos de audio.
- La selección guardada inválida mantiene el fallback a Lázaro; Alexis Syntaxis permanece disponible.

## Verificación automatizada

Desde `PolitecnicoOpenWorld/`:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```

Las pruebas de integración cubren registro, rutas, densidad de mundo libre, reutilización del pack y fallback de audio, aparición de La Planchada antes de los tres jefes, mapas ESM según dificultad y ausencia de desbloqueo gratuito. La auditoría de campaña verifica que existen atlas, datos, escenarios y audios para todos los rivales. El gate `SF Fighter Assets` reconstruye el atlas con la receta versionada y comprueba geometría y reproducibilidad.

## Resultados ejecutados

Fecha: **2026-11-06**. Código y assets evaluados: **`ef3f6372`**, después de incorporar `main` en `4956f286`. APK debug **1.0.0.22**. Entorno: AVD **Medium_Phone_API_35**, Android **15 / API 35**, emulador headless.

- `:app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest`: **PASS**. **441 pruebas** aprobadas: **155 de app + 286 de shared**, sin fallos, errores ni omitidas. Incluye la auditoría completa de campaña y las pruebas nuevas de audio de `main`.
- Detekt sobre `app/src/main/java` y `shared/src/commonMain/kotlin`, con configuración y baseline del repositorio: **PASS**.
- `tools/check_kmp_test_names.sh`: **PASS**, nombres compatibles con Kotlin/Native. No se ejecutó una compilación de iOS.
- Se instaló este APK en el emulador y se abrió el selector, el showcase y una práctica con **La Planchada contra Escomboy en ESM**. Se observaron las dos orientaciones en showcase, guardia, patada y daño corporal; en práctica se verificaron carga del escenario, combate con CPU, agachado y una pose aérea, sin crecimiento corporal visible.
- El proceso seguía activo al terminar. El registro filtrado del proceso no presentó errores `AndroidRuntime` ni `MediaPlayer`: [registro de ejecución](../pr-screenshots/la-planchada/runtime-check.txt).
- El showcase auditó los datos y los cuatro archivos de voz de La Planchada sin avisos de ese personaje. El [diagnóstico completo](../pr-screenshots/la-planchada/showcase-audit.txt) incluye un aviso de `bonusPower3` de **La Tzitzimime**, cuyo poder está retirado en el catálogo existente; no pertenece a esta integración.
- **No se realizó escucha del audio**: el emulador se inició con `-no-audio`. La evidencia comprueba registro, archivos, rutas y ausencia de errores de carga; no certifica calidad sonora ni balance de volumen.

### QA estático del arte

Se revisaron visualmente las secuencias de **258 cuadros / 58 animaciones** del atlas y los **25 recortes derivados** de mundo libre. El pipeline SF y el validador de mundo libre reportaron **0 errores**; las **21 pruebas SF** pasaron. La [receta reproducible](../PolitecnicoOpenWorld/tools/sf_fighters/laplanchada.json) y la [fuente documentada](../newSFAssets/LaPlanchada/README.md) permiten reconstruirlos. Esta inspección de imágenes se distingue de la prueba de ejecución Android.

### Evidencia final de Android

| Evidencia | Captura |
|---|---|
| Selector con La Planchada | [Selector](../pr-screenshots/la-planchada/05-final-selector.png) |
| Carga de práctica en ESM | [Inicio de práctica](../pr-screenshots/la-planchada/06-final-practice-esm.png) |
| Combate sin overlay de showcase | [Combate](../pr-screenshots/la-planchada/07-final-practice-esm-combat.png) |
| Agachado con anatomía a la misma escala | [Agachado](../pr-screenshots/la-planchada/08-final-practice-crouch.png) |
| Pose aérea de práctica | [Aéreo parcial](../pr-screenshots/la-planchada/10-final-practice-airborne.png) |
| Orientaciones izquierda y derecha | [Guardia espejo](../pr-screenshots/la-planchada/02-final-showcase-idle.png) |
| Patada y daño corporal | [Patada](../pr-screenshots/la-planchada/03-final-showcase-kick.png), [Daño corporal](../pr-screenshots/la-planchada/04-final-showcase-body-hurt.png) |

Las capturas de showcase conservan sus controles de QA. La captura aérea demuestra una pose en ejecución; no prueba por sí sola toda la secuencia de salto ni todos los ataques aéreos.

## Cobertura manual verificada y pendiente

| Caso | Estado y alcance |
|---|---|
| Selector de combate | **Verificado** en Android: La Planchada se selecciona en práctica y su atlas carga. La prueba usó modo desarrollador para acceder al roster completo. |
| Práctica diurna ESM | **Verificado**: combate contra Escomboy, agachado, pose aérea parcial, daños recibidos y tamaño corporal coherente. |
| Showcase | **Parcial**: se ejecutó el personaje y se capturaron guardia, patada, daño corporal y ambas orientaciones. Falta una revisión manual aislada de cada variante de daño, KO, recuperación, agarres, contraataques y súper. |
| Audios reutilizados | **Registro y carga verificados**, sin escucha ni revisión de volumen. |
| Mundo libre e interiores | **QA estático verificado** para los 25 derivados. Pendiente elegir la skin en el mapa, caminar/correr/especial, entrar a un interior y comprobar persistencia tras reiniciar. |
| Progresión y desbloqueo | **Pruebas automatizadas aprobadas**. Pendiente vencer a La Planchada en una campaña real en Medio/Difícil y comprobar la recompensa. |
| ESM nocturno y apocalipsis | **QA estático y catálogo verificados**. Pendiente jugar ambas variantes en Android. |
| Regresión del roster y guardados | **Pruebas automatizadas aprobadas**. Pendiente recorrido manual con Alexis, estudiantes y una campaña antigua guardada. |
| Accesibilidad, ciclo de vida e iOS | **Pendiente**: fuentes grandes, pausa/reanudación prolongada, cambios de configuración y prueba en dispositivo iOS. |
