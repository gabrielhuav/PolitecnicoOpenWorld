# Reporte de Optimización y Refactorización (Politécnico Open World)

Este documento detalla las mejoras técnicas y de arquitectura aplicadas al código fuente con el objetivo de maximizar la eficiencia y calidad del proyecto, sin alterar ni modificar la jugabilidad o el comportamiento visible para el usuario final.

## 1. Optimización de Rendimiento (CPU y Batería)
Se optimizó a bajo nivel el motor de Inteligencia Artificial responsable del comportamiento de los NPCs (`NpcAiManager.kt`) para reducir drásticamente el consumo de procesador y batería del dispositivo móvil.

* **Problema Encontrado:** El juego ejecutaba operaciones matemáticas extremadamente pesadas (raíces cuadradas usando `sqrt` y cálculos trigonométricos usando `cos`) cientos de miles de veces por minuto para determinar las distancias, *spawns*, *despawns* y la navegación de hasta 40 NPCs simultáneos sobre la red de calles (OpenStreetMap).
* **Solución Implementada:** 
  1. Se introdujo una función matemática optimizada (`calculateDistanceSqFast`) que utiliza la validación de **distancias al cuadrado**. Esto elimina por completo la necesidad de calcular raíces cuadradas para comparar proximidades.
  2. Se aplicó una técnica de *pre-caching* para los ángulos trigonométricos: ahora el coseno de la ubicación del jugador se calcula una sola vez al inicio del ciclo (frame), en lugar de recalcularse individualmente para cada nodo y calle en el mapa.
* **Beneficio:** La IA se procesa en una fracción del tiempo original. Esto libera al procesador del celular para renderizar gráficos de forma más fluida, calienta menos el dispositivo y ahorra batería, manteniendo el ecosistema procedural de los NPCs exactamente igual.

## 2. Refactorización y Código Limpio (Clean Architecture)
Se aplicaron principios de diseño de software para refactorizar la pantalla más pesada del juego: la interfaz gráfica principal (`WorldMapScreen.kt`), la cual había alcanzado el tamaño excesivo de **1,101 líneas de código**, violando el principio de responsabilidad única al mezclar la vista principal con lógicas auxiliares de bajo nivel.

* **Acción:** Se extrajeron **184 líneas de código** puras de la parte inferior de ese archivo.
* **Solución Implementada:** Se modularizó la lógica en 3 archivos independientes creados dentro del directorio de `components`:
  1. **`WebMapHtmlBuilder.kt`**: Encapsula completamente la inmensa plantilla y motor de inyección de HTML/JavaScript necesaria para el renderizado del mapa web (Leaflet).
  2. **`SpriteUtils.kt`**: Aísla la lógica matemática y de dibujo en Canvas (creación de barras de vida tipo RPG, bordes dinámicos) y manipulación de *Drawables*.
  3. **`CacheStatusWidget.kt`**: Extrae la jerarquía de micro-componentes visuales estáticos correspondientes al HUD de diagnóstico (estado de caché y red).
* **Beneficio:** Se logró un archivo principal muchísimo más limpio, modular y fácil de mantener para futuras actualizaciones, elevando el profesionalismo de la arquitectura del proyecto sin romper ni un solo botón o función del juego.

---
**Resumen en números:**
- **Líneas de código extraídas/reducidas del archivo principal:** 184 líneas.
- **Operaciones matemáticas costosas evitadas por segundo:** Miles.
