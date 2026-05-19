# Reporte de Optimización: Gestión de NPCs (`NpcAIManager.kt`)
**Desarrollador:** Blanco López Juan Antonio

---

### **Q: ¿Qué problema se identificó en el código original?**
**A:** Se detectaron tres fuentes principales de degradación de rendimiento (*overhead*) dentro del método `updateNpcs` al procesar flujos constantes de NPCs en tiempo real:
1. **Uso de operaciones matemáticas costosas:** Se calculaba la distancia real usando `calculatedDistance`, la cual ejecuta internamente una raíz cuadrada (`sqrt`) en cada frame por cada NPC para evaluar umbrales de proximidad.
2. **Desperdicio de memoria (Garbage Collector):** Se encadenaban funciones como `.filter` y `.sortedByDescending` antes de un `.removeAll`. Esto generaba múltiples listas intermedias e innecesarias en memoria en cada ciclo de ejecución.
3. **Copiado masivo en listas concurrentes:** La colección `serverNpcs` es de tipo `CopyOnWriteArrayList`. Cada operación directa de escritura (`.clear()` y `.addAll()`) duplicaba todo el arreglo internamente, saturando el hilo de cómputo.

---

### **Q: ¿Por qué se realizaron estos cambios?**
**A:** Para garantizar la estabilidad del juego en escenarios de alta densidad de elementos (mapas muy poblados). Al optimizar el uso de CPU y memoria en el hilo de cómputo secundario (`Dispatchers.Default`), logramos:
* **Mayor estabilidad de FPS:** Se eliminan los micro-tirones o *stuttering* durante la exploración del mapa.
* **Eficiencia energética:** Menos ciclos de reloj desperdiciados en matemáticas redundantes se traducen directamente en un menor consumo de batería en el dispositivo móvil del usuario.
* **Reducción de latencia:** El recolector de basura (Garbage Collector) trabaja menos, evitando pausas en el ciclo de vida de la aplicación.

---

### **Q: ¿Qué cambios específicos se hicieron en el código y cómo se implementaron?**
**A:** Los cambios se dividieron en tres optimizaciones clave y puramente internas en `NpcAIManager.kt`:

#### **1. Evitar la raíz cuadrada en las comparaciones**
* **Implementación:** Se añadió la función matemática `calculateDistanceSq` que calcula la distancia euclidiana al cuadrado (evitando el `sqrt`).
* **Código de referencia:**
  ```kotlin
  private val despawnDistanceSq = despawnDistance * despawnDistance
  private val spawnDistanceSq   = spawnDistance * spawnDistance

  private fun calculateDistanceSq(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
      val dLat = lat1 - lat2
      val dLon = (lon1 - lon2) * Math.cos(lat1 * Math.PI / 180)
      return dLat * dLat + dLon * dLon
  }

### **2. Optimizar el filtrado de "Despawn" y "Población"**
* **Implementación:** Se eliminó la creación de colecciones intermedias sustituyendo la combinación de `.filter` + `.removeAll` por un filtrado directo mediante un predicado en línea.


#### **3. Reducir operaciones de copiado en `CopyOnWriteArrayList`**
* **Implementación:** En lugar de limpiar y reescribir `serverNpcs` múltiples veces durante el proceso, ahora se extrae una lista mutable normal al inicio (`workingList`), se opera todo el flujo de forma ligera sobre ella, y se actualiza la lista global compartida una única vez al final.


### **Q: ¿Cómo se puede validar que el cambio funciona correctamente?**
**A:** Al ser un cambio puramente interno en la clase, no afecta a otros módulos. Se valida mediante los siguientes pasos:
1. Cambiar a la rama `OptimizacionBlancoLopez` y realizar un *Sync* de Gradle.
2. Ejecutar la aplicación en modo *Profile* para inspeccionar que la curva de asignación de memoria se mantenga plana (sin picos de Garbage Collection).
3. Confirmar visualmente en el juego que el spawn y despawn de las entidades responda de forma fluida conforme el jugador se desplaza por las coordenadas del mapa (utilizando el sistema de coordenadas `[y, x]` del motor).
