<<<<<<< HEAD
# Pruebas de QA — Selección visual de slots del inventario

## 1. Información general

| Campo             | Valor                                    |
| ----------------- | ---------------------------------------- |
| Funcionalidad     | Selección visual de slots del inventario |
| Rama              | `feature/inventory-slot-selection`       |
| Base SHA          | `7ed32539`                               |
| SHA final probado | `caae27c3`                               |
| Autor             | Diego Mendieta González                  |
| Fecha             | 1 de octubre de 2026                     |
| Dispositivo       | Samsung Galaxy S24+                      |
| Modelo            | SM-S926B                                 |
| Sistema operativo | Android 16                               |
| API               | 36.1                                     |

La funcionalidad agrega retroalimentación visual al seleccionar un slot desbloqueado del inventario durante el modo historia.

## 2. Criterios de aceptación

* **AC-01:** Al seleccionar un slot desbloqueado del inventario se muestra un indicador visual verde.
* **AC-02:** Al seleccionar otro slot desbloqueado, el indicador visual se mueve al nuevo slot seleccionado.
* **AC-03:** El estado de selección se restablece al cerrar el inventario y los slots bloqueados no pueden convertirse en el slot seleccionado.

## 3. Matriz de pruebas

| ID     | Tipo                     | Criterio / riesgo                                                                                         | Resultado                                                                                                                                                                              | Estado | Evidencia                            |
| ------ | ------------------------ | --------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ------------------------------------ |
| INV-01 | Happy path               | AC-01 — El slot seleccionado debe mostrar retroalimentación visual.                                       | Se abrió el inventario en modo historia y se seleccionó un slot desbloqueado. El slot seleccionado mostró el indicador verde.                                                          | PASS   | [arreglo.mp4](evidencia/arreglo.mp4) |
| INV-02 | Alterno / límite         | AC-02 — La selección debe cambiar al seleccionar otro slot desbloqueado.                                  | Se seleccionó un segundo slot desbloqueado. El indicador verde se desplazó al nuevo slot.                                                                                              | PASS   | [arreglo.mp4](evidencia/arreglo.mp4) |
| INV-03 | Regresión                | Verificar que el funcionamiento general del inventario no se vea afectado.                                | Se abrió el inventario, se seleccionó un slot, se cerró y se volvió a abrir. El inventario continuó funcionando correctamente.                                                         | PASS   | [arreglo.mp4](evidencia/arreglo.mp4) |
| INV-04 | Navegación / estado      | AC-03 — No debe conservarse una selección anterior después de cerrar el inventario.                       | Después de seleccionar un slot y cerrar el inventario, se volvió a abrir sin seleccionar ningún slot. No apareció automáticamente la selección anterior.                               | PASS   | [arreglo.mp4](evidencia/arreglo.mp4) |
| INV-05 | Accesibilidad            | Comprobar que la retroalimentación visual continúe siendo distinguible con un tamaño de fuente aumentado. | Se aumentó temporalmente el tamaño de fuente del dispositivo y se verificó la selección del slot. El indicador verde continuó siendo distinguible y la interfaz permaneció utilizable. | PASS   | [arreglo.mp4](evidencia/arreglo.mp4) |
| INV-06 | Compatibilidad / entorno | Verificar la funcionalidad en el entorno objetivo y ausencia de errores visibles.                         | Se ejecutó la aplicación en un Samsung Galaxy S24+ con Android 16 / API 36.1. La selección visual funcionó sin cierre, congelamiento ni error visible.                                 | PASS   | [arreglo.mp4](evidencia/arreglo.mp4) |

## 4. Detalle de ejecución

### INV-01 — Selección de slot desbloqueado

**Autor:** Diego Mendieta González
**Fecha:** 1 de octubre de 2026
**SHA probado:** `caae27c3`
**Dispositivo:** Samsung Galaxy S24+ (SM-S926B), Android 16, API 36.1

**Precondiciones:**

* Aplicación instalada y ejecutándose.
* Jugador ubicado en el modo historia.
* Inventario disponible.

**Pasos:**

1. Entrar al modo historia.
2. Mantener presionado el botón Y para abrir el inventario.
3. Seleccionar un slot desbloqueado.

**Resultado esperado:**
El slot seleccionado muestra un indicador visual verde.

**Resultado actual:**
El slot seleccionado mostró el indicador visual verde.

**Estado:** PASS.

**Evidencia:** [arreglo.mp4](evidencia/arreglo.mp4)

---

### INV-02 — Cambio de slot seleccionado

**Autor:** Diego Mendieta González
**Fecha:** 1 de octubre de 2026
**SHA probado:** `caae27c3`
**Dispositivo:** Samsung Galaxy S24+ (SM-S926B), Android 16, API 36.1

**Precondiciones:**

* Inventario abierto.
* Al menos dos slots desbloqueados disponibles.

**Pasos:**

1. Seleccionar un slot desbloqueado.
2. Seleccionar un segundo slot desbloqueado.

**Resultado esperado:**
El indicador visual se mueve al segundo slot y deja de mostrarse en el anterior.

**Resultado actual:**
El indicador visual se desplazó al segundo slot seleccionado.

**Estado:** PASS.

**Evidencia:** [arreglo.mp4](evidencia/arreglo.mp4)

---

### INV-03 — Regresión del inventario

**Autor:** Diego Mendieta González
**Fecha:** 1 de octubre de 2026
**SHA probado:** `caae27c3`
**Dispositivo:** Samsung Galaxy S24+ (SM-S926B), Android 16, API 36.1

**Precondiciones:**

* Aplicación funcionando normalmente.

**Pasos:**

1. Abrir el inventario.
2. Seleccionar un slot.
3. Cerrar el inventario.
4. Volver a abrirlo.
5. Seleccionar nuevamente un slot.

**Resultado esperado:**
El inventario continúa funcionando y permite seleccionar slots después de cerrarlo y volverlo a abrir.

**Resultado actual:**
El inventario continuó funcionando correctamente.

**Estado:** PASS.

**Evidencia:** [arreglo.mp4](evidencia/arreglo.mp4)

---

### INV-04 — Restablecimiento del estado

**Autor:** Diego Mendieta González
**Fecha:** 1 de octubre de 2026
**SHA probado:** `caae27c3`
**Dispositivo:** Samsung Galaxy S24+ (SM-S926B), Android 16, API 36.1

**Precondiciones:**

* Inventario disponible.

**Pasos:**

1. Abrir el inventario.
2. Seleccionar un slot.
3. Cerrar el inventario.
4. Volver a abrirlo.
5. No seleccionar ningún slot.

**Resultado esperado:**
No debe conservarse visualmente la selección anterior.

**Resultado actual:**
El indicador de selección anterior no apareció automáticamente al volver a abrir el inventario.

**Estado:** PASS.

**Evidencia:** [arreglo.mp4](evidencia/arreglo.mp4)

---

### INV-05 — Accesibilidad visual

**Autor:** Diego Mendieta González
**Fecha:** 1 de octubre de 2026
**SHA probado:** `caae27c3`
**Dispositivo:** Samsung Galaxy S24+ (SM-S926B), Android 16, API 36.1

**Precondiciones:**

* Aplicación instalada.
* Inventario disponible.

**Pasos:**

1. Aumentar temporalmente el tamaño de fuente del dispositivo.
2. Abrir la aplicación.
3. Abrir el inventario.
4. Seleccionar un slot desbloqueado.
5. Comprobar la visibilidad del indicador.

**Resultado esperado:**
El indicador de selección debe continuar siendo distinguible y la interfaz debe permanecer utilizable.

**Resultado actual:**
El indicador verde continuó siendo distinguible y la interfaz permaneció utilizable.

**Estado:** PASS.

**Evidencia:** [arreglo.mp4](evidencia/arreglo.mp4)

---

### INV-06 — Compatibilidad / entorno

**Autor:** Diego Mendieta González
**Fecha:** 1 de octubre de 2026
**SHA probado:** `caae27c3`
**Dispositivo:** Samsung Galaxy S24+ (SM-S926B), Android 16, API 36.1

**Precondiciones:**

* Samsung Galaxy S24+ (SM-S926B).
* Android 16.
* API 36.1.
* Aplicación instalada y ejecutándose.

**Pasos:**

1. Ejecutar la aplicación.
2. Entrar al modo historia.
3. Abrir el inventario.
4. Seleccionar un slot desbloqueado.
5. Observar el comportamiento de la aplicación.

**Resultado esperado:**
La selección visual funciona correctamente sin cierre inesperado, congelamiento o error visible.

**Resultado actual:**
La funcionalidad funcionó correctamente en el dispositivo indicado y no se observaron cierres, congelamientos ni errores visibles.

**Estado:** PASS.

**Evidencia:** [arreglo.mp4](evidencia/arreglo.mp4)

---

## 5. Evidencia general

* [Video — comportamiento antes de la modificación](evidencia/antes.mp4)
* [Video — comportamiento después de la modificación y ejecución de pruebas](evidencia/arreglo.mp4)

El video `antes.mp4` muestra el comportamiento original, en el que el inventario no proporcionaba retroalimentación visual al seleccionar un slot.

El video `arreglo.mp4` muestra el comportamiento posterior a la modificación, incluyendo el indicador visual verde y la ejecución de las pruebas funcionales realizadas sobre la versión final.

Un mismo video puede respaldar varios casos cuando contiene evidencia observable de los pasos y resultados correspondientes.

## 6. Defectos encontrados

Durante la ejecución de los seis casos de prueba no se identificaron defectos reproducibles relacionados con la funcionalidad modificada.

**Defectos abiertos:** 0.

## 7. Reejecución después de correcciones

Los casos de prueba se ejecutaron sobre la versión final de la funcionalidad, correspondiente al SHA:

`caae27c3`

Los casos relacionados directamente con la selección visual y el estado del inventario fueron comprobados nuevamente después de las modificaciones finales.

## 8. Relación entre versión probada y versión final

* **Base de trabajo:** `7ed32539`
* **Versión final probada:** `caae27c3`
* **Commits de la funcionalidad:** 5
* **Rama:** `feature/inventory-slot-selection`

El SHA `caae27c3` corresponde al estado final de la rama utilizado durante las pruebas descritas en este documento.

## 9. Cierre de QA

Los seis casos definidos fueron ejecutados en el dispositivo de prueba y finalizaron con estado **PASS**.

La evidencia obtenida permite verificar el comportamiento esperado de la selección visual, el cambio entre slots, el restablecimiento del estado y la ausencia de regresiones observables durante las pruebas realizadas.

La documentación queda asociada a la versión final `caae27c3`.
=======
# Exam Test Report

- **Date:** 01/10/2026
- **Student / Integrant:** Tellez Giron Castro Angel Ricardo
- **Tested SHA:** `8781c7cbcea0929c90bdc8b8285d3ef9ac4bc95f` (Base SHA: `7ed325393f82872c2be94ff2ada46948efa19152`)
- **Environment:** 
  - OS: Windows 10
  - Android Studio: Quail 3 | 2026.1.3 (Build AI-261.26222.65.2613.15948027)
  - Device: OPPO CPH2205 (Physical device via USB, Android 13, API 33)

## Test Case Matrix

| Case | Action Performed | Observed Result | Status |
| :--- | :--- | :--- | :--- |
| **C01** | Start the application, load the Huelum vs Goya fight scenario, and let Llorona win | The scenario loads correctly; initial observation showed the win audio matched the special attack audio. | **Passed** |
| **C02** | Validation of the "Ayyyy" sound on taking damage | Corresponds to the standard hurt sound (`special_llorona_hurt`) and the new clip did not play incorrectly. | **Passed** |
| **C03** | Use Llorona's special attack | The usual clip plays (`special_llorona_power`). | **Passed** |
| **C04** | Exit the fight using Back and re-enter | The audio stopped playing and the fight functionality resumed normally. | **Passed** |
| **C05** | Minimize the app and return | No crash occurred, and the app behaved normally upon returning. | **Passed** |
| **C06** | Accessibility test (maximum text size in Settings) | The menu and buttons look complete and can be tapped without issues. | **Passed** |

## Additional Evidence
- Executed host test output path: `C:\Users\LENOVO\Documents\evidencias-pow\tests-shared-rerun.txt`
- **Audio Origin and License:** Audio captured directly via screen recording on a mobile device (academic / personal use for exam testing purposes).

## Video Evidence
- **Before fix:** [antes.mp4](./evidencia/antes.mp4) *(Ajusta la ruta si tu carpeta se llama distinto)*
- **After fix:** [despues.mp4](./evidencia/despues.mp4)
>>>>>>> upstream/main
