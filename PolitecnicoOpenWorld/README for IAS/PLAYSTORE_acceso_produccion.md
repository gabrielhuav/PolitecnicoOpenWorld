# 🚀 Play Store — Solicitud de ACCESO A PRODUCCIÓN (cuestionario + lo que se envió)

**Enviada:** 2026-08-03, 21:05 · **Estado:** en revisión

> **Para quién es:** para quien tenga que reenviar esta solicitud si Google la rechaza, o
> prepararla de nuevo para otra app. Guarda las **respuestas textuales** que se mandaron y los
> hechos que las sostienen, porque el formulario NO deja consultar lo enviado después.
>
> Este documento es el compañero de `PLAYSTORE_formulario_seguridad_datos.md`, que cubre otra
> cosa: el formulario de **Seguridad de los datos** y las páginas de políticas. Los dos hay que
> tenerlos en orden, pero son trámites distintos y se resuelven por separado.

---

## 1. Qué es este trámite

Desde 2023 Google exige, a las cuentas de **desarrollador personal**, correr una **prueba cerrada
con al menos 12 verificadores durante 14 días seguidos** antes de poder publicar en producción.
Cumplido eso, se habilita un formulario de **solicitud de acceso a producción** con tres bloques:

1. **Acerca de la prueba cerrada**
2. **Acerca de tu juego**
3. **Acerca del nivel de preparación para publicar en producción**

⚠️ **Cada campo de texto tiene un tope de 300 caracteres.** No avisa hasta que pegas de más.

---

## 2. Lo que se envió, textual

### Bloque 1 · Acerca de la prueba cerrada

**P: ¿Cómo reclutaste usuarios para la prueba cerrada? Por ejemplo, ¿le pediste a amigos y
familiares que participaran? ¿o recurriste a un proveedor de pruebas pagado?**  · *275/300*

> Recluté a estudiantes de mis cursos en ESCOM-IPN; más de 50 participaron como verificadores en
> la prueba cerrada vía Play Store. El juego está ambientado en su campus (Zacatenco), así que son
> el público objetivo. También probamos a fondo la versión del repositorio de GitHub.

**P: ¿Qué tan fácil fue reclutar verificadores para tu juego?**
(Muy difícil / Difícil / Ni fácil ni difícil / Fácil / Muy fácil)

> **Fácil**

*Por qué no "Muy fácil": hay acceso directo a estudiantes que además son el público objetivo, pero
hubo que coordinarlos e instalar desde un track cerrado. "Fácil" es lo honesto.*

**P: Describe el nivel de participación de los verificadores durante la prueba cerrada. Cuéntanos
si los verificadores usaron o no todas las funciones del juego, y si ese uso fue coherente con la
forma en la que esperarías que juegue un usuario real. De lo contrario, describe las diferencias
que esperarías notar.**  · *283/300*

> Probaron las funciones principales: mundo abierto sobre el mapa real, Modo Historia (Misión 1),
> minijuego de supervivencia y ajustes. Jugaron como un usuario real: explorando, completando
> objetivos y reportando fallos. El multijugador tuvo menos uso porque requiere dos dispositivos.

### Bloque 2 · Acerca de tu juego

> ⚠️ **PENDIENTE DE REGISTRAR.** Estas preguntas se contestaron en el formulario pero no quedaron
> copiadas. Si alguien las tiene a mano, pégalas aquí: sin ellas, un reenvío tendría que
> improvisar respuestas nuevas y **la incoherencia entre envíos es justo lo que dispara escrutinio**.

### Bloque 3 · Nivel de preparación para publicar en producción

**P: ¿Cómo decidiste que tu juego estaba listo para producción?**  · *272/300*

> Tras semanas de pruebas exhaustivas con más de 50 verificadores y retroalimentación casi diaria
> de mis alumnos, los errores reportados disminuyeron y las funciones principales se mantuvieron
> estables. El juego se juega sin ningún fallo crítico, así que lo consideré listo.

---

## 3. Los hechos que sostienen las respuestas

Si hay que reescribir algo, que sea sobre esta base — no sobre lo que suene mejor:

- **Más de 50 verificadores**, estudiantes de los cursos del autor en **ESCOM-IPN**.
- El juego está ambientado en el campus **Zacatenco** del propio IPN → los verificadores **son** el
  público objetivo, no un grupo de conveniencia.
- Distribución por la **pista cerrada (`alpha`)** de Play, que es la que sube el CI
  (`android-release.yml`, job `playstore-closed-testing`).
- Pruebas adicionales sobre la versión del **repositorio de GitHub**, fuera de Play.
- Retroalimentación **casi diaria** durante semanas.
- Función con menos uso: **multijugador**, porque necesita dos dispositivos conectados a la vez.
  Aun así varios lo probaron en pareja. Esto se declaró; no se ocultó.

---

## 4. Tiempos y qué esperar

- Google dice **7 días o menos**, "ocasionalmente puede llevar más". Enviada el **2026-08-03**,
  la ventana llega hasta el **~2026-08-10**.
- La respuesta llega **por correo al propietario de la cuenta** de Play Console. Revisa spam.
- **No reenvíes ni crees otra solicitud mientras está en revisión**: no acelera nada y puede
  reiniciar la cola. Es el mismo error que ya costó tiempo con el formulario de seguridad de datos
  (ver `PLAYSTORE_formulario_seguridad_datos.md` §2).
- Si aprueban: se habilita publicar en producción.
- Si rechazan: el correo dice **qué** corregir. Corrige **eso** y reenvía; no reescribas todo.

---

## 5. Antes de publicar en producción (cuando llegue la aprobación)

El acceso a producción es solo el permiso. Publicar de verdad requiere que siga en orden lo del
otro documento — y esas son las tres cosas por las que Google **ya nos rechazó** el 2026-07-22:

1. `policy_en_es.html` y `account-deletion.html` cargan (no 404). La rama **`gh-pages`** es la que
   las sirve; el job `play-compliance` del CI lo verifica antes de cada subida y bloquea si falla.
2. El correo de contacto correcto en ambas páginas: `gabrielhuav@gmail.com`.
3. **Formulario = realidad = política.** Si entra un SDK nuevo, se declara.

Además, el pipeline actual sube a **`alpha` (prueba cerrada)**, no a producción: eso está fijado en
`android-release.yml` (`tracks: alpha`). Pasar a producción es **cambiar ese track a propósito**, no
algo que ocurra solo al ser aprobado.
