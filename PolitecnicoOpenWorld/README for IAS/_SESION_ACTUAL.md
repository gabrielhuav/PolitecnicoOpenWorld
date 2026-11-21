# Memoria compartida — estado vivo

Actualizado: **2026-11-21 · PR #188 · prioridad: lanzar Titulación por Combate en la App Store**.
Ventana de dos días, máximo 200 líneas. La sesión anterior (QA física del 2026-11-15/16) está en
[`_ARCHIVO/SESION_previa_2026-11-16.md`](_ARCHIVO/SESION_previa_2026-11-16.md).

## Decisión del dueño (2026-11-21)

- **iPhone se publica en la App Store SOLO con Titulación por Combate** (+ Coleccionables y Ajustes).
  Android ya está en Google Play y conserva TODO: mundo abierto, Modo Historia, zombis y multijugador.
- **No se borra código**: lo exclusivo de Android solo deja de pintarse en iOS.
- El README público presenta Titulación por Combate como la estrella del juego.
- Nunca tocar las páginas de políticas de Play Store (`account-deletion.html`,
  `PLAYSTORE_formulario_seguridad_datos.md`, la política en `gh-pages`).

## Hecho en esta sesión (Windows, rama del PR #188)

- `PowModos.kt`: `MODOS_EN_OBRAS_VISIBLES = false` → iOS ya no pinta MUNDO LIBRE / MODO HISTORIA /
  MULTIJUGADOR marcados EN OBRAS (Guideline 2.1).
- `sfMultijugadorDisponible()` (nuevo, `PowModos.kt`) + `SfMenuOverlays.kt`: el botón MULTIJUGADOR del
  menú del modo pelea solo se pinta en Android. En iOS quedaba muerto: `PowAppIos` no pasa
  `onlinePlatformOverlays` y pulsarlo no abría nada.
- `PowModosTest`: +2 tests (multijugador de Titulación solo en Android; interruptor apagado en el repo).
- Tests en Windows: **153 `:app` + 350 `:shared` (`testAndroidHostTest`) PASS**. iOS NO se compiló aquí.
- README: Titulación como estrella, tabla de plataformas, 9 capturas visibles (Llorona/Charro,
  Policía/Presidenta y Tzitzimime/Yoalli en sus tres luces) + 52 en 18 desplegables, changelog podado,
  arquitectura y servidores corregidos (`MultiplayerInteriores/`, `MultiplayerSF/`).
- Docs: retirados 5 traspasos ya ejecutados; `12_PLAN_MUNDO_ABIERTO_iOS.md` marcado EN PAUSA; regla
  nueva en `09` (§ "Qué puede enseñar iOS").

## Siguiente (necesita Mac)

1. Enlazar ambos frameworks, compilar en Xcode y correr los tests iOS de `:shared` (deben ser 350).
2. Verificar en el iPhone: el menú principal muestra solo TITULACIÓN POR COMBATE, COLECCIONABLES y
   AJUSTES, y el menú del modo pelea ya no muestra MULTIJUGADOR.
3. App Store: cuenta Apple Developer (99 USD/año; aún no existe, ver doc 14), ficha, capturas,
   etiquetas de privacidad y URL de política de privacidad (revisar si la de Play cubre iOS).
4. Decidir si el Modo Desarrollador de Ajustes (desbloquea todo y abre herramientas de QA) se esconde
   en la build de la App Store.
