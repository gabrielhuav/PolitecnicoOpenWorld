# Plan y Matriz de Aseguramiento de Calidad (QA)

**Proyecto:** Politécnico Open World (POW)  
**Profesor:** Hurtado Avilés Gabriel  
**Alumno / Autor del QA:** Aragón Martínez Manuel Alejandro  
**Rama:** `feature-update-asset-tutorialmundoabierto`  
**SHA Base:** `7ed32539`  
**Versión de la app:** 1.0.0.18  
**Dispositivo Físico Principal:** Samsung Galaxy A54 5G (SM-A546E, Android 14, One UI 6.1, pantalla 6.4" Super AMOLED 2340×1080 px, orientación horizontal landscape)  
**Dispositivo Secundario (Emulador):** AVD `Medium_Phone` (Android 16 / API 36, horizontal landscape 2400×1080, 420 dpi)  
**Fecha:** 30 de septiembre de 2026  

---

## 1. Alcance, Criterios de Aceptación y Análisis de Riesgos

### 1.1 Objetivo del Cambio
Corregir los problemas de desbordamiento visual, recorte de texto y pérdida de botones de acción en la ventana emergente del tutorial de controles (`ControlsTutorialOverlay`) para el Mundo Abierto e Interiores. Integrar adaptabilidad para pantallas horizontales (landscape) y configuraciones de accesibilidad con fuentes de gran tamaño, scroll vertical para lectura completa del contenido y soporte de gestos táctiles horizontales (swipe) para alternar entre páginas.

### 1.2 Justificación e Impacto en la Accesibilidad y UX
A pesar de que el tutorial de controles aparentaba ser un elemento aislado, este defecto comprometía directamente la **accesibilidad** y la **experiencia de usuario (UX)**:
- Para un jugador nuevo que entra por primera vez al mundo abierto, el tutorial es la guía indispensable para comprender las mecánicas principales (conducir, correr, interactuar, atacar, teletransportarse).
- En orientación horizontal, en teléfonos con relaciones de aspecto comunes como el Samsung Galaxy A54 5G, las instrucciones más complejas ("Conducir", "Interactuar") se cortaban a la mitad del renglón.
- Lo más crítico: los botones "Siguiente", "Anterior" y "¡Entendido!" desaparecían por debajo del marco visible de la pantalla, dejando al usuario bloqueado sin saber cómo avanzar o cerrar el diálogo, deteriorando gravemente la experiencia en múltiples modelos de dispositivos móviles.
- Al no contar con soporte para gestos táctiles de deslizamiento (swipe), la interacción resultaba rígida y poco intuitiva para un entorno nativo moderno.

### 1.3 Criterios de Aceptación
* **CA-01 (Éxito - Ruta Feliz):** El usuario puede navegar a través de todas las páginas del tutorial de controles mediante los botones "Anterior" y "Siguiente", o mediante gestos de deslizamiento táctil horizontal (swipe), visualizando el contenido, badge/emoji, puntos indicadores y botones sin ningún recorte en pantalla.
* **CA-02 (Condición Límite / Alterna):** En dispositivos con pantallas horizontales de altura reducida (~360dp a 400dp) o con fuentes ampliadas de accesibilidad, el texto no queda cortado; el usuario puede deslizar verticalmente el contenido dentro de la tarjeta, mientras que la cabecera y la barra inferior de botones permanecen fijas y accesibles.

### 1.4 Matriz de Riesgos Identificados
| ID Riesgo | Descripción del Riesgo | Impacto | Mitigación / Caso que lo cubre |
|---|---|---|---|
| **R-01** | Conflicto de gestos entre el deslizamiento horizontal (pager) y el scroll vertical en pantallas pequeñas. | Medio: El usuario podría no ser capaz de desplazarse verticalmente si el gesto horizontal captura todo el toque. | **CP-02 (Límite)** y **CP-05 (Accesibilidad)**: Disambiguación nativa de gestos anidados de Compose (`HorizontalPager` + `verticalScroll`). |
| **R-02** | Botón de cierre ("✕") o botones de navegación ("Siguiente", "Entendido") desplazados fuera de pantalla en pantallas compactas. | Alto: Bloqueo de la navegación o imposibilidad de cerrar el tutorial. | **CP-01 (Ruta Feliz)** y **CP-04 (Navegación y Estado)**: Anclaje estático del Header y Footer en el layout con `weight(1f, fill = false)`. |
| **R-03** | Regresión en el flujo de configuración o en el tutorial de interiores (`interiorTutorialPages`). | Medio: Dañar el tutorial de interiores al reutilizar el mismo componente común (`ControlsTutorialOverlay`). | **CP-03 (Regresión)**: Verificación del tutorial de interiores desde Ajustes → Controles. |

---

## 2. Matriz de Casos de Prueba (Mínimo 6 Casos)

### Caso 1: CP-01 — Ruta Feliz: Navegación completa del tutorial con swipe y botones
* **Criterio / Riesgo cubierto:** CA-01, R-02
* **Autor de la ejecución:** Aragón Martínez Manuel Alejandro
* **Fecha:** 30/09/2026
* **Dispositivo / Configuración:** Samsung Galaxy A54 5G (Android 14) / AVD `Medium_Phone` (API 36, horizontal)
* **Precondiciones:** La app compila e inicia en el Menú Principal.
* **Pasos:**
  1. Abrir la aplicación y seleccionar "Mundo Libre (pre-alpha)".
  2. Si aparece el diálogo "¿Quieres un repaso rápido de los controles?", presionar "VER TUTORIAL" (o acceder vía Ajustes → Controles → "TUTORIAL · MUNDO ABIERTO").
  3. Verificar que se despliega la primera página ("Moverte").
  4. Deslizar con el dedo hacia la izquierda (swipe) para avanzar a "Correr".
  5. Presionar el botón "Siguiente" para recorrer las siguientes páginas ("Interactuar", "Golpear", "Conducir", "Teletransporte").
  6. En la última página, presionar "¡Entendido!".
* **Resultado Esperado:** Todas las páginas se recorren fluidamente tanto con botones como por deslizamiento horizontal. Al presionar "¡Entendido!", el diálogo se cierra correctamente regresando a la pantalla anterior.
* **Resultado Real:** Aprobado. Las 6 páginas se muestran con su título, badge/emoji y texto descriptivo completo. No existe desbordamiento.
* **Estado:** Aprobado
* **Evidencias:**
  - Antes (Falla): [`Captura 1.1 - Ventana Tutorial Incompleta.jpg`](Captura%201.1%20-%20Ventana%20Tutorial%20Incompleta.jpg), [`Tutorial Desactualizado.mp4`](Tutorial%20Desactualizado.mp4)
  - Después (Corregido): [`Captura 2.1 - Ventana Tutorial Corregida.jpg`](Captura%202.1%20-%20Ventana%20Tutorial%20Corregida.jpg), [`Controles Tutorial Corregido.mp4`](Controles%20Tutorial%20Corregido.mp4)

---

### Caso 2: CP-02 — Condición Límite: Scroll vertical en tarjeta con texto largo en pantalla horizontal
* **Criterio / Riesgo cubierto:** CA-02, R-01
* **Autor de la ejecución:** Aragón Martínez Manuel Alejandro
* **Fecha:** 30/09/2026
* **Dispositivo / Configuración:** Samsung Galaxy A54 5G en modo horizontal (altura viewport ~380dp).
* **Precondiciones:** Abrir el tutorial de controles de mundo abierto.
* **Pasos:**
  1. Navegar a la página 5 ("Conducir") y página 3 ("Interactuar"), que poseen los textos más largos del tutorial.
  2. Observar la visibilidad del texto, los puntos indicadores y los botones inferiores.
  3. Deslizar el dedo hacia arriba sobre el área del texto.
* **Resultado Esperado:** El texto no queda cortado por el borde inferior. Los botones "Anterior" y "Siguiente", así como los puntos indicadores, permanecen visibles dentro de los límites de la pantalla. El contenido permite desplazamiento vertical suave.
* **Resultado Real:** Aprobado. El texto y los botones se adaptan a la altura disponible y el contenido se desplaza verticalmente sin perder la barra de botones.
* **Estado:** Aprobado
* **Evidencias:**
  - Antes (Texto cortado, botones invisibles): [`Captura 1.1 - Ventana Tutorial Incompleta.jpg`](Captura%201.1%20-%20Ventana%20Tutorial%20Incompleta.jpg), [`Captura 1.3 - Ventana Tutorial Incompleta.jpg`](Captura%201.3%20-%20Ventana%20Tutorial%20Incompleta.jpg)
  - Después (Texto completo, scroll vertical fluido): [`Captura 2.1 - Ventana Tutorial Corregida.jpg`](Captura%202.1%20-%20Ventana%20Tutorial%20Corregida.jpg), [`Controles Tutorial Corregido.mp4`](Controles%20Tutorial%20Corregido.mp4)

---

### Caso 3: CP-03 — Regresión: Apertura y funcionamiento del Tutorial de Interiores
* **Criterio / Riesgo cubierto:** R-03
* **Autor de la ejecución:** Aragón Martínez Manuel Alejandro
* **Fecha:** 30/09/2026
* **Dispositivo / Configuración:** Samsung Galaxy A54 5G / AVD API 36.
* **Precondiciones:** App abierta en la pantalla de Ajustes.
* **Pasos:**
  1. Ir a Ajustes → sección "Controles".
  2. Bajar hasta el apartado inferior de tutoriales y pulsar "TUTORIAL · INTERIORES".
  3. Comprobar que se abre la ventana con título "Controles · Interiores".
  4. Recorrer las 5 páginas específicas de interiores ("Moverte", "Correr", "Interactuar", "Atacar", "Modo de golpe e inventario").
  5. Cerrar pulsando "¡Entendido!" o la "✕".
* **Resultado Esperado:** El catálogo de interiores (5 páginas) funciona idénticamente con soporte de swipe y scroll sin alterar su lógica específica ni romper el menú de ajustes.
* **Resultado Real:** Aprobado. Las 5 páginas se cargan con los recursos correspondientes y la ventana se cierra limpiamente.
* **Estado:** Aprobado
* **Evidencias:** Verificado en video [`Controles Tutorial Corregido.mp4`](Controles%20Tutorial%20Corregido.mp4) y pruebas automatizadas en [`ControlsTutorialTest.kt`](../PolitecnicoOpenWorld/shared/src/commonTest/kotlin/ovh/gabrielhuav/pow/features/settings/ui/ControlsTutorialTest.kt).

---

### Caso 4: CP-04 — Navegación y Ciclo de Vida: Botón Atrás, botón ✕ e interrupción
* **Criterio / Riesgo cubierto:** R-02
* **Autor de la ejecución:** Aragón Martínez Manuel Alejandro
* **Fecha:** 30/09/2026
* **Dispositivo / Configuración:** Samsung Galaxy A54 5G / AVD API 36.
* **Precondiciones:** Tutorial abierto en la página 3.
* **Pasos:**
  1. Estando en la página 3, pulsar el botón "✕" en la esquina superior derecha.
  2. Volver a abrir el tutorial y pulsar la tecla/gesto de "Atrás" del sistema Android.
  3. Volver a abrir el tutorial, enviar la aplicación a segundo plano (Home) y regresar a la aplicación.
* **Resultado Esperado:** Al pulsar "✕" o "Atrás", el overlay se descarta inmediatamente. Al reanudar la app desde segundo plano, el estado de la pantalla se mantiene sin crasheos ni bloqueos de interfaz.
* **Resultado Real:** Aprobado. El overlay responde a los eventos de descarte y el ciclo de vida de la actividad no se ve afectado.
* **Estado:** Aprobado
* **Evidencias:** Verificado en sesión interactiva y video [`Controles Tutorial Corregido.mp4`](Controles%20Tutorial%20Corregido.mp4).

---

### Caso 5: CP-05 — Accesibilidad: Visualización con texto del sistema al tamaño máximo
* **Criterio / Riesgo cubierto:** CA-02, R-01
* **Autor de la ejecución:** Aragón Martínez Manuel Alejandro
* **Fecha:** 30/09/2026
* **Dispositivo / Configuración:** Samsung Galaxy A54 5G con Ajustes de Sistema → Fuente y tamaño de pantalla al 130% - 150%.
* **Precondiciones:** Tamaño de fuente del sistema escalado al máximo.
* **Pasos:**
  1. Abrir la aplicación y entrar al Tutorial de Controles.
  2. Observar el tamaño del texto y verificar que no se sobreponga ni desaparezca de la tarjeta.
  3. Deslizar verticalmente para leer todo el texto.
  4. Comprobar que el botón "✕" y los botones de acción sigan teniendo área táctil adecuada.
* **Resultado Esperado:** Con fuente grande, el texto se autoacomoda, la tarjeta no excede la pantalla y el usuario puede hacer scroll vertical para leer las descripciones completas.
* **Resultado Real:** Aprobado. Gracias al `Modifier.verticalScroll` por página y las restricciones del diálogo, el contenido no desborda la pantalla.
* **Estado:** Aprobado
* **Evidencias:** [`Captura 2.2 - Ventana Tutorial Corregida.jpg`](Captura%202.2%20-%20Ventana%20Tutorial%20Corregida.jpg).

---

### Caso 6: CP-06 — Compatibilidad y Entorno: Salto interactivo directo mediante indicadores
* **Criterio / Riesgo cubierto:** CA-01
* **Autor de la ejecución:** Aragón Martínez Manuel Alejandro
* **Fecha:** 30/09/2026
* **Dispositivo / Configuración:** Samsung Galaxy A54 5G en modo oscuro / AVD.
* **Precondiciones:** Tutorial abierto en la página 1.
* **Pasos:**
  1. En lugar de presionar "Siguiente", presionar directamente el 5º punto indicador inferior.
  2. Verificar que el carrusel se desplaza automáticamente a la página 5 ("Conducir").
  3. Presionar el 2º punto indicador.
  4. Verificar que regresa a la página 2 ("Correr").
* **Resultado Esperado:** Los indicadores responden al toque del usuario y animan la transición de página suavemente.
* **Resultado Real:** Aprobado. `pagerState.animateScrollToPage(index)` ejecuta la transición a la página seleccionada instantáneamente.
* **Estado:** Aprobado
* **Evidencias:** [`Controles Tutorial Corregido.mp4`](Controles%20Tutorial%20Corregido.mp4).

---

## 3. Registro de Hallazgos y Cierre de Calidad

* **Defectos Preexistentes Corregidos:**
  - El diálogo del tutorial en `ControlsTutorial.kt` carecía de límites de altura y scroll vertical, causando que en pantallas en orientación horizontal el texto de "Conducir" e "Interactuar" se recortara y los botones de acción quedaran inaccesibles por debajo de la pantalla.
  - La navegación dependía exclusivamente de los botones de texto sin soporte para gestos táctiles de deslizamiento (swipe).
* **Nuevos Hallazgos:** Ninguno. Se verificaron compilación y pruebas sin regresiones.
* **Dictamen de Calidad:** **APROBADO PARA INTEGRACIÓN (GO)**. El cambio cumple los criterios de aceptación, pasa todos los Quality Gates de CI y detekt con exit 0.
