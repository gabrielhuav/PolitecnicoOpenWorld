# Memoria compartida — estado vivo

Actualizado: **2026-11-21 · PR #188 (unido) y PR #189 · prioridad: lanzar Titulación por Combate en
la App Store**.
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

## Hecho después, en el PR #189 (Windows, 2026-11-21)

- PR #188 unido: release 1.0.0.27 (corrida 87) bien; con `manual-play-upload` el AAB quedó como
  artefacto sin subir a Play; `main` en 1.0.0.28.
- PR #189 (cámara norte-arriba y colisiones del mundo abierto, de NagaPams) actualizado con `main` y con
  la etiqueta `manual-play-upload`. QA en emulador: cámara, rumbos 0/90/180/270, mover y recentrar,
  bajar del coche, choques. Encontrado y corregido: un coche encimado en un edificio con la calle detrás
  quedaba clavado (`resolveVehicleDrivingStep`) y el punto del escenario DEV-08 estaba dentro del techo.
- Notas de Play de la 1.0.0.28 reescritas (las de antes eran de septiembre).
- Purga de docs: 30 archivos ejecutados o sin referencias fuera de `README for IAS` (prompts de traspaso,
  checkpoints, historiales, QA viejas, maquetas HTML); 0 enlaces rotos en 62 docs. Siguen en git.
- App Store: ícono 1024, `PrivacyInfo.xcprivacy` e `ITSAppUsesNonExemptEncryption` en `iosApp/`, y
  traspaso [`PROMPT_MAC_appstore_final.md`](PROMPT_MAC_appstore_final.md).

## Siguiente (necesita Mac) → seguir `PROMPT_MAC_appstore_final.md`

1. Compilar ambos frameworks y correr los tests iOS de `:shared` (ahora deben ser **358**).
2. Recorrido en simulador e iPhone: ícono, menú con solo TITULACIÓN POR COMBATE, COLECCIONABLES y AJUSTES,
   sin MULTIJUGADOR en el modo pelea; comprobar que el manifiesto y el ícono entran al bundle.
3. Decisiones del dueño: Modo Desarrollador en Release, iPad sí/no, peleadores que retratan personas
   reales (La Presidenta).
4. App Store: cuenta Apple Developer (99 USD/año; aún no existe, ver doc 14), TestFlight, ficha, capturas
   de pantalla grande, privacidad y URL de política (revisar si la de Play cubre iOS).
