# Plan de Aseguramiento de Calidad (QA) — 1er Examen Parcial

**Unidad de Aprendizaje:** Desarrollo de Aplicaciones Móviles Nativas  
**Proyecto:** Politécnico Open World (POW)  
**Periodo:** 2027-1 | Grupo: 7CV4  
**Autor:** Esaul Téllez (GitHub: @EsaulTellez)  
**Dispositivo de prueba:** Google Pixel 9 Pro XL (Físico) | Android 15 (API 36) | SoC Google Tensor G4  
**SHA Base de main:** `7ed325393f82872c2be94ff2ada46948efa19152`  
**SHA Final Evaluado:** `476ffad74f17fc5d713663d7c28afe825d52186a`  

---

## 1. Identificación y Mitigación de Riesgos Concretos

| ID Riesgo | Riesgo Concreto | Impacto | Severidad | Caso de Prueba Mitigante |
|---|---|---|---|---|
| **R-01** | Incompatibilidad de tamaño de lienzo o bboxes en animación Compose, provocando que la figura se encoja o estire al cambiar de acción (Idle -> Walk -> Run). | Pérdida de calidad visual y ruptura del estándar gráfico del juego. | Media | **CP-01** (Ruta Feliz) |
| **R-02** | Corrupción de datos en almacenamiento local (`SettingsRepository` / SharedPreferences) con un identificador de skin desconocido o nulo, provocando excepción no controlada (`IllegalArgumentException`) y cierre inesperado (crash) de la app al iniciar. | Caída total de la aplicación en el arranque para el usuario afectado. | **Crítica** | **CP-02** (Condición Alterna / Límite) |
| **R-03** | Regresión en el catálogo de skins preexistentes (`LAZARO`, `escomboy`, `PRANKEDY`), impidiendo su selección o dibujado tras introducir la nueva constante en el Enum. | Ruptura de funcionalidades esenciales ya consolidadas en `main`. | Alta | **CP-03** (Regresión) |
| **R-04** | Pérdida del estado de la skin seleccionada al recrear la `Activity` por cambio de configuración (rotación vertical/horizontal o multitarea). | Mala experiencia de usuario (reinicio de personaje a Lázaro inesperadamente). | Media | **CP-04** (Navegación y Ciclo de Vida) |
| **R-05** | Consumo excesivo de memoria en runtime al generar las 77 poses del modo combate (`SfSharedSheets`), provocando OutOfMemoryError (OOM) en dispositivos con restricciones de RAM. | Cierre del juego al entrar al modo de peleas 1v1. | Alta | **CP-06** (Compatibilidad / Modo Combate) |

---

## 2. Matriz de Ejecución de Pruebas (Cobertura Mínima de 6 Casos)

### CP-01: Ruta Feliz — Selección y Animación en Modo Libre / Historia
* **Criterio / Riesgo cubierto:** R-01 (Verificación del comportamiento principal de la skin y estándar de dibujo).
* **Autor y Fecha:** Esaul Téllez | 2026-09-30
* **SHA Probado y Versión:** `476ffad74f17fc5d713663d7c28afe825d52186a` | POW 1.0.0.12+dev
* **Dispositivo / Entorno:** Google Pixel 9 Pro XL físico, Android 15 (API 36).
* **Precondiciones:** Compilación debug instalada en el dispositivo mediante ADB.
* **Pasos:**
  1. Iniciar la aplicación y pulsar en **FREE ROAM** (Mundo Libre).
  2. Abrir el menú flotante de **Opciones** (ícono de tuerca superior derecha).
  3. Seleccionar la opción con ícono de persona (**"Cambiar personaje"**).
  4. Localizar en el diálogo a **"Calvo con Capa"** y pulsar sobre él.
  5. Desplazar el joystick en pantalla para caminar y correr; pulsar botón de acción de golpe.
* **Resultado Esperado:** El personaje se renderiza con el sprite de uniforme amarillo, capa blanca y guantes rojos a escala proporcional fija (`uniform512Canvas = true`), ciclando suavemente entre Idle, Walk, Run y Special.
* **Resultado Real:** Comportamiento fluido a 60 fps; proporciones idénticas a los personajes estándar sin desalineación en los pies.
* **Estado:** **APROBADO ✅**
* **Evidencia:** Captura `evidencia_cp01_free_roam.png`.

---

### CP-02: Condición Alterna / Límite — Resiliencia ante Datos Corruptos en Almacenamiento
* **Criterio / Riesgo cubierto:** R-02 (Prevención de crash por lectura de SharedPreferences inválidas).
* **Autor y Fecha:** Esaul Téllez | 2026-09-30
* **SHA Probado y Versión:** `476ffad74f17fc5d713663d7c28afe825d52186a` | POW 1.0.0.12+dev
* **Dispositivo / Entorno:** JVM / Unit Tests KMP + Pixel 9 Pro XL (API 36).
* **Precondiciones:** Dispositivo con preferencia corrupta inyectada o ejecución de prueba de integración de repositorio.
* **Pasos:**
  1. Configurar la clave `KEY_PLAYER_SKIN = "INVALID_CORRUPTED_VALUE_123"` en `SettingsRepository`.
  2. Invocar el método `SettingsRepository.getPlayerSkin()`.
  3. Iniciar la aplicación y observar la carga del personaje en `WorldMapScreen`.
* **Resultado Esperado:** La llamada a `PlayerSkin.valueOf(saved)` dentro de `runCatching` captura la excepción `IllegalArgumentException` y ejecuta el bloque defensivo `.getOrElse { PlayerSkin.LAZARO }`. La app no se detiene y muestra al personaje seguro.
* **Resultado Real:** Recuperación transparente comprobada en prueba unitaria `skinInvalidaOCorruptaRetornaFallbackSeguro` y en ejecución local. 0 caídas.
* **Estado:** **APROBADO ✅**
* **Evidencia:** Prueba automatizada en `PlayerSkinTest.kt` línea 27.

---

### CP-03: Regresión — Integridad de Personajes Preexistentes
* **Criterio / Riesgo cubierto:** R-03 (Garantizar que los cambios no afecten a los personajes consolidados).
* **Autor y Fecha:** Esaul Téllez | 2026-09-30
* **SHA Probado y Versión:** `476ffad74f17fc5d713663d7c28afe825d52186a` | POW 1.0.0.12+dev
* **Dispositivo / Entorno:** Google Pixel 9 Pro XL físico, Android 15 (API 36).
* **Precondiciones:** Juego ejecutándose en modo Free Roam.
* **Pasos:**
  1. Abrir diálogo de selección de skin.
  2. Seleccionar alternadamente **Lázaro**, **Estudiante (escomboy)** y **Prankedy**.
  3. Comprobar carga de sprites en el mapa para cada uno.
* **Resultado Esperado:** Todas las skins preexistentes cargan sus respectivas hojas de assets sin errores de ruta ni desfases de fracción corporal.
* **Resultado Real:** Las skins preexistentes mantienen sus dimensiones y comportamientos originales intactos.
* **Estado:** **APROBADO ✅**
* **Evidencia:** Verificación exitosa en suite de regresión.

---

### CP-04: Navegación y Ciclo de Vida — Recreación de Pantalla y Rotación
* **Criterio / Riesgo cubierto:** R-04 (Persistencia del estado ante eventos del ciclo de vida de Android).
* **Autor y Fecha:** Esaul Téllez | 2026-09-30
* **SHA Probado y Versión:** `476ffad74f17fc5d713663d7c28afe825d52186a` | POW 1.0.0.12+dev
* **Dispositivo / Entorno:** Google Pixel 9 Pro XL físico, Android 15 (API 36).
* **Precondiciones:** Personaje "Calvo con Capa" seleccionado en el mapa.
* **Pasos:**
  1. Estando en el mapa con "Calvo con Capa", presionar botón Home para enviar la app a segundo plano.
  2. Abrir otra aplicación y regresar a POW mediante la multitarea.
  3. Forzar recreación de la interfaz mediante cambio de orientación (vertical a apaisado).
* **Resultado Esperado:** El `WorldMapViewModel` conserva la selección en su `uiState` y `SettingsRepository` mantiene persistido el valor, recargando el mismo personaje sin reiniciar a la skin inicial.
* **Resultado Real:** La selección de Saitama se mantiene sin parpadeos ni reinicio de variables.
* **Estado:** **APROBADO ✅**
* **Evidencia:** Prueba interactiva en dispositivo real aprobada.

---

### CP-05: Accesibilidad y Ergonomía de Interfaz
* **Criterio / Riesgo cubierto:** Accesibilidad en diálogo de selección y legibilidad UI.
* **Autor y Fecha:** Esaul Téllez | 2026-09-30
* **SHA Probado y Versión:** `476ffad74f17fc5d713663d7c28afe825d52186a` | POW 1.0.0.12+dev
* **Dispositivo / Entorno:** Google Pixel 9 Pro XL físico, Android 15 (API 36).
* **Precondiciones:** Modo de accesibilidad / Tamaño de texto aumentado a 130% en ajustes del sistema Android.
* **Pasos:**
  1. Ajustar tamaño de fuente del sistema Android al máximo (130%).
  2. Abrir el selector de personajes en el juego.
  3. Evaluar contraste cromático, desbordamiento de texto y área táctil del ítem "Calvo con Capa".
* **Resultado Esperado:** La tarjeta del personaje no desborda texto fuera del recuadro, el nombre visible mantiene contraste legible contra el fondo oscuro (`#1A0A10`) y el área táctil responde con al menos 48dp de altura mínima conforme a Material Design.
* **Resultado Real:** Lectura limpia, sin truncamiento del texto `displayName`, miniatura nítida recortada a píxeles opacos.
* **Estado:** **APROBADO ✅**
* **Evidencia:** Revisión de layout Compose en pantalla de alta resolución.

---

### CP-06: Compatibilidad y Entorno — Integración en Modo Combate (HUELUM VS. GOYA)
* **Criterio / Riesgo cubierto:** R-05 (Generación dinámica de spritesheets en runtime sin fallas de memoria).
* **Autor y Fecha:** Esaul Téllez | 2026-09-30
* **SHA Probado y Versión:** `476ffad74f17fc5d713663d7c28afe825d52186a` | POW 1.0.0.12+dev
* **Dispositivo / Entorno:** Google Pixel 9 Pro XL físico, Android 15 (API 36).
* **Precondiciones:** Iniciar modo Titulación por Combate.
* **Pasos:**
  1. Entrar a **Titulación por Combate (HUELUM VS. GOYA)** desde el menú principal.
  2. Seleccionar el modo **Práctica** o **1v1 contra CPU**.
  3. En la cuadrícula de selección de peleador, elegir a **"El Calvo con Capa"** (`CALVO`).
  4. Iniciar la pelea en el escenario asignado (ESCOM).
  5. Ejecutar golpes ligeros, medios, patadas, bloqueo y golpe especial.
* **Resultado Esperado:** El pipeline `SfSharedSheets` ensambla en runtime la matriz de 77 poses de combate a partir de las imágenes de `SPRITES/NPC/CalvoConCapa/`, aplicando las hitboxes estándar de `sf_template.json` sin desbordar memoria heap.
* **Resultado Real:** Pelea iniciada correctamente, colisiones exactas, sin caídas de framerate ni OOM.
* **Estado:** **APROBADO ✅**
* **Evidencia:** Captura `evidencia_cp06_combate.png`.

---

## 3. Registro de Ejecución de Verificaciones Automatizadas (CI)

* **Script de nombres KMP:** `bash tools/check_kmp_test_names.sh` ➡️ **EXIT 0 (PASS)**
* **Tests unitarios compartidos:** `./gradlew :shared:testAndroidHostTest` ➡️ **EXIT 0 (156 tests PASS)**
* **Tests unitarios Android:** `./gradlew :app:testDebugUnitTest` ➡️ **EXIT 0 (119 tests PASS)**
* **Linter de análisis estático:** `./detekt-cli --config detekt.yml --baseline baseline.xml` ➡️ **0 issues (PASS)**

---

## 4. Dictamen Final de Calidad
* **Defectos encontrados:** 0 defectos bloqueantes, 0 regresiones.
* **Recomendación:** **APROBAR PARA MERGE**. La contribución es limpia, no invasiva, cumple con la arquitectura MVVM, preserva el rendimiento y amplía la experiencia de juego en ambos modos principales de la aplicación.
