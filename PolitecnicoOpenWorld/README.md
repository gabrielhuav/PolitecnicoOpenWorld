# Politécnico Open World (POW)

**Politécnico Open World (POW)** es una aplicación interactiva de exploración 2D con vista *top-down* para Android. El proyecto integra mapas del mundo real mediante OpenStreetMap (OSM) con un motor propio de renderizado para interiores, permitiendo la transición fluida entre exteriores y el interior de edificios, plazas y salones de clase.

El desarrollo está basado nativamente en **Jetpack Compose** y utiliza una arquitectura **Data-Driven UI** para manejar la escalabilidad de miles de locaciones sin incrementar la cantidad de código fuente.

## ⚙️ Arquitectura y Enfoque Técnico

Para evitar el antipatrón de crear una vista o archivo por cada locación física (Ej. `Salon101.kt`, `Salon102.kt`), el proyecto utiliza un modelo de **Data-Driven UI**:

1.  **Jerarquía basada en Base de Datos:** Las locaciones (Campus -> Edificio -> Piso -> Salón) se almacenan como nodos en una base de datos local (Room).
2.  **Renderizado Dinámico:** Existe un único componente `LocationScreen` genérico. El *ViewModel* consulta la base de datos, obtiene la matriz bidimensional de colisiones/texturas del nodo correspondiente y Compose dibuja la vista dinámicamente.
3.  **Patrón MVVM y Clean Architecture:** * **Presentation:** Jetpack Compose, ViewModels, StateFlow.
    * **Domain:** Lógica pura de juegos, algoritmos de *pathfinding* (A* para navegación) y reglas de vehículos.
    * **Data:** Repositorios, clientes de red (WebSockets) y base de datos local.

## 🚀 Características del Proyecto

* **Mapeo Híbrido (Exterior/Interior):** Uso de `osmdroid` para la navegación en el mundo real y Canvas de Compose para el dibujo de matrices de interiores.
* **Control de Vehículos:** Sistema de movimiento con distintos tipos de transporte y un algoritmo de *snap-to-road* (ajuste a la carretera) leyendo los metadatos de las vías de OSM.
* **Módulos de Juegos Embebidos:** Integración de minijuegos nativos (Pacman, Supervivencia Zombie, Basquetbol) que se ejecutan como eventos dentro de zonas específicas del mapa.
* **Conectividad Multijugador:**
    * **Local:** Integración de la API de Bluetooth de Android para descubrir e interactuar con dispositivos cercanos.
    * **Online:** Comunicación en tiempo real a través de WebSockets utilizando un servidor Node.js.

## 📂 Estructura del Código

El proyecto sigue una estructura modular orientada a características (*Feature-based*), pensada para facilitar un futuro refactor hacia múltiples submódulos de Gradle:

```text
app/src/main/java/ovh/gabrielhuav/pow/
│
├── core/                   # UI genérica (Theme, Type), Navegación, Utilidades compartidas
├── data/                   # Implementación de repositorios, BD Room, DTOs y Hardware (BT, Sockets)
├── domain/                 # Casos de uso, interfaces de repositorios, modelos puros
│
├── features/               # Dominios funcionales de la aplicación
│   ├── map_exterior/       # Lógica y UI de OpenStreetMap y vehículos
│   ├── map_interior/       # Motor de renderizado dinámico de matrices bidimensionales
│   ├── minigames/          # Componentes aislados para Pacman y Zombies
│   └── multiplayer/        # Flujos de conexión y emparejamiento (Lobby)
│
└── MainActivity.kt         # Entry point (Single-Activity Architecture)