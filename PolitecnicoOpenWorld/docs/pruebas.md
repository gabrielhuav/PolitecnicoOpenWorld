# Matriz de Pruebas de Calidad (QA) - Claxon de Vehículo con Reacción de Peatones

**Proyecto:** Politécnico Open World (POW)  
**Módulo:** Mundo Abierto (`map_exterior`) / Conducción y NPCs  
**Dispositivo de prueba:** Samsung Galaxy S24 FE (`SM-S721B`), Android 14 / One UI 6.1 (API 34)  
**Versión / Commit SHA probado:** `7ed325393f82872c2be94ff2ada46948efa19152` (+ commits de la rama)  

---

## Resumen Ejecutivo de Casos de Prueba

| ID Caso | Tipo de Prueba | Descripción Resumida | Estado |
|---|---|---|---|
| **CP-01** | Happy Path | Tocar el claxon en auto civil y verificar huida de peatones `COWARD` a la banqueta | **APROBADO** |
| **CP-02** | Alternate / Boundary | Spam rápido del claxon y ramificación a sirena policial en patrulla robada | **APROBADO** |
| **CP-03** | Regresión | Verificar aceleración (A), frenado (B), freno de mano (X) y salir (Y) intactos | **APROBADO** |
| **CP-04** | Lifecycle / State | Persistencia del botón y estado al minimizar la app (Home) y volver del menú | **APROBADO** |
| **CP-05** | Accesibilidad | Cumplimiento de área táctil >=48 dp, contraste de color naranja y feedback háptico | **APROBADO** |
| **CP-06** | Compatibilidad / Audio | Ejecución en hardware real con volumen en 0 (silencio) sin excepciones de audio | **APROBADO** |

---

## Fichas Detalladas de Ejecución

### CP-01: Happy Path - Conducción Libre y Claxon Civil
* **Objetivo:** Comprobar que al tocar el claxon en un coche civil, se emite el sonido dual y los peatones cobardes huyen.
* **Precondiciones:** Partida en Modo Libre en Zacatenco. Peatones civiles visibles en la calle/banqueta a ~15 m.
* **Pasos:**
  1. Abordar un coche civil presionando **X**.
  2. Presionar el botón naranja central **📢** en el diamante de controles.
* **Resultado Esperado:** Se reproduce `claxon.wav` mediante `SoundPool`. Los peatones con rasgo `NpcTrait.COWARD` entran en pánico (`fearUntil`) y corren hacia la banqueta alejándose del auto. Los `AGGRESSIVE` muestran burbuja de queja (`talkingUntil`).
* **Resultado Obtenido:** El claxon suena con claridad; los peatones cobardes corren en dirección opuesta al vehículo y los agresivos despliegan el emoji de diálogo.
* **Estado:** **APROBADO**

---

### CP-02: Alternate / Boundary - Spam Rápido y Sirena de Patrulla
* **Objetivo:** Verificar la ramificación a sirena policial y la resistencia del motor de audio ante pulsaciones veloces repetidas.
* **Precondiciones:** Jugador a bordo de una patrulla policial (`isDrivingPoliceCar = true`).
* **Pasos:**
  1. Presionar el botón **📢** repetidamente más de 10 veces continuas (spam de claxon).
* **Resultado Esperado:** Se reproduce el sonido de sirena policial (`playPolice1()`). `SoundPool` maneja las pistas concurrentes sin saturarse, sin congelar el hilo de UI ni provocar fugas de memoria.
* **Resultado Obtenido:** La sirena policial suena sin distorsión ni tartamudeo. Los FPS se mantienen estables a 60 fps.
* **Estado:** **APROBADO**

---

### CP-03: Regresión - Controles de Conducción y Pie
* **Objetivo:** Garantizar que los controles existentes de vehículos no sufrieron desplazamientos ni alteraciones en su funcionamiento.
* **Precondiciones:** Jugador en vehículo.
* **Pasos:**
  1. Acelerar con **A**, frenar con **B**, activar freno de mano con **X**.
  2. Salir del vehículo con **Y**.
  3. Desplazarse a pie con el joystick.
* **Resultado Esperado:** Todas las acciones de conducción responden con normalidad. Al descender del coche, el HUD vuelve al diamante de a pie sin botón de claxon.
* **Resultado Obtenido:** Cero regresiones detectadas. El layout de los botones mantiene su proporción y responsividad original.
* **Estado:** **APROBADO**

---

### CP-04: Lifecycle / State - Pausa y Reanudación de la App
* **Objetivo:** Verificar que el estado del claxon y la conducción sobrevivan a cambios de ciclo de vida de la Activity.
* **Precondiciones:** Jugador conduciendo en el mapa.
* **Pasos:**
  1. Presionar botón Home del sistema Android.
  2. Reabrir la app desde la lista de aplicaciones recientes.
  3. Abrir el menú de Opciones / Mapa y cerrarlo.
* **Resultado Esperado:** La Activity se reanuda correctamente. El jugador sigue a bordo del vehículo y el botón 📢 continúa operativo.
* **Resultado Obtenido:** El estado se mantiene intacto sin bloqueos ni recreaciones de Activity fallidas.
* **Estado:** **APROBADO**

---

### CP-05: Accesibilidad - Target Táctil >=48 dp y Ergonomía
* **Objetivo:** Validar cumplimiento de los lineamientos de accesibilidad de Android Material Design.
* **Precondiciones:** Inspección de la UI táctil.
* **Pasos:**
  1. Medir el área táctil del botón 📢 en el composable (`tamano = TamanoBotonAccion = 48.dp`).
  2. Comprobar el contraste de color (`#E67E22`) contra el fondo oscuro del volante.
* **Resultado Esperado:** El botón cumple con el estándar de accesibilidad de mínimo 48x48 dp y ofrece respuesta háptica al pulsar.
* **Resultado Obtenido:** Pulsación cómoda con el pulgar; retroalimentación háptica detectada mediante `rememberInputFeedback().tap()`.
* **Estado:** **APROBADO**

---

### CP-06: Compatibilidad / Audio - Modo Silencio y Rendimiento
* **Objetivo:** Probar el comportamiento del juego en hardware real bajo condiciones de volumen en cero.
* **Precondiciones:** Samsung SM-S721B con volumen multimedia silenciado (`sfxVolume = 0`).
* **Pasos:**
  1. Presionar el botón 📢 en modo silencio.
* **Resultado Esperado:** La app no lanza excepciones de audio nulo; los peatones siguen reaccionando visualmente por la lógica desacoplada de `NpcAiManager`.
* **Resultado Obtenido:** Ejecución fluida, cero crasheos y animación visual de huida completamente operativa.
* **Estado:** **APROBADO**
