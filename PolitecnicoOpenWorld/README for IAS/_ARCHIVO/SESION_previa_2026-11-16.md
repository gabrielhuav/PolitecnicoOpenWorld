# Memoria compartida — estado vivo

Actualizado: **2026-11-16 · PR #188 · QA física cerrada; galería en grupos de tres**.
Ventana de dos días, máximo 200 líneas. El detalle e incidencias permanecen en los informes enlazados.

## Checkout y reglas

- Activo: `/Users/gabrielhuav/.codex/worktrees/pr188-iphone-qa/PolitecnicoOpenWorld`.
- Rama `codex/pr188-iphone-qa`; base PR 188 `bfd2148f044892317430ca328b2e6c5b28a15fab`, con las correcciones versionadas en 79e1eb94.
- El checkout original conserva `pr/184` y su historial. Allí solo se sincronizan este puntero y el informe físico.
- **Autorizado el 2026-11-16: commit y push al mismo PR #188; NO merge.** Commit de correcciones 79e1eb94: 79e1eb94d75b31e32c13cdfded08222299e83e2e. Estado: publicado en la rama del PR #188.
- No contraseñas/Touch ID ni Device Hub. Ambient respeta silencio por diseño; audio sin cambios.
- JAVA_HOME: `/Applications/Android Studio.app/Contents/jbr/Contents/Home`.
- Antes de cada build iOS enlazar AMBOS frameworks. Esquema comprobado `iosApp.POW`; reenumerar con -list.
- Firma solo por CLI, nunca equipo en pbxproj. Kotlin conserva CRLF según EditorConfig.
- Temporales y DerivedData fuera del repo: `/tmp/pow-iphone-qa-2026-11-15/` (TMP).

## Resultado y evidencia vigente

Informe principal: [QA física PR 188](../SF/QA_IPHONE_PR188_2026-11-15.md).
iPhone 15 real/iOS 26.7.1, Xcode 27.2;19 peleadores sin Paramédico, 20 lugares × 3 luces = 60 fondos.
Práctica es P1 contra CPU. No se exigió el producto cartesiano de parejas.

- **Ambos frameworks + APK + Xcode firmado + instalación PASS** después de los cambios finales.
- **849 ejecuciones PASS**:153 app + 348 sharedJVM + 348 sharediOS;0 fallos/errores/omitidas XML.
  Base 831 + 5 regresiones de geometría y 4 de proyectiles en cada destino compartido =849.
- Práctica Estudiante/Estudianta: movimiento, ataques/daño, puntuación y final observados en el iPhone.
- **Galería 60/60, roster 19/19,61 PNG originales**:60 de matriz +extra Charro con 4 GOLPES.
  Las 60 fotos de matriz son distintas por SHA;25 muestran 2 GOLPES. Pareja/HUD/rostros/mapa/luz revisados.
- **19 retakes finales PASS**,0 fallos/omitidos;1376,777 s de sesión. Se revisaron originales y selector dorado.
- Cámara ESCOM zoom 1,3: PASS 45,3 s, centro/izquierda/derecha/salto. Biblioteca zoom 1,0: repetición
  final PASS 65,978 s con izquierda 5 s/derecha 10 s/salto/salida; sustituye un TIMEOUT previo, descrito en informe.
- Charro/Charro IA 90 s: método PASS 117,67 s, 91 PNG, estable; dos victorias de ronda y comienzo de ronda 3.
  **Ese primer duelo NO terminó la partida**. Su muestra 53 aporta el extra 4 GOLPES.
- Charro/Charro IA 180 s: **método PASS 208,254 s**,181 PNG revisados; **partida P1 gana 2–0**.
  Dos medallas desde 63 s, EXTRAORDINARIO/PACTO COBRADO, Revancha desde 67 s y resultado hasta 180 s.
  No se observó ACABALO en las muestras de 1 s; no afirmar un texto/evento entre capturas.
- Dueño confirmó audio general y **«si se escucha»** para el Charro. Los PNG no graban audio;
  no identifican cada variante aleatoria hurt1. No se modificaron clips ni sesión Ambient.
- **Limpieza PASS 9,748 s**: subtítulos restaurados a su original OFF; AX Selected true→false y PNG.
  Hitboxes/FPS OFF, DevMode ON; regreso al menú verificado. Primera calibración con value vacío
  falló sin tocar el Switch; fallbackSelected solo con Switch único/hittable y transición comprobada.
- Solo se desinstaló nuestro runner `ovh.gabrielhuav.POW.GestureUITests.xctrunner`; outcome success.
  POW permanece instalado; lanzamiento CLI final success. No se borraron otras apps por la IA.
- **Dueño: restaurar AutoLock temporal 5 min a su valor anterior.** La IA no cambió ese ajuste.

## Correcciones instaladas y versionadas

1. PowAppIos: `ForzarHorizontal()` en toda la ruta PELEA, incluidos selectores; libera al salir.
2. SfMenuOverlays/SfStageSelectOverlay: insets horizontales de tarjetas; StreetFighterScreen los
   aplica solo a controles/guías. L1 antes bajo notch, ahora x 75..131 dp y visible en originales físicos.
3. SfSceneRenderer/SfBackgroundGeometry: COVER del Canvas y paneo del excedente real, con límites
   de salto/shake. Antes podía descubrir~273 px negros al calcular sobre 382 unidades virtuales.
4. SfFireballs.advance: KO por proyectil llamaba `endRound→openRemateWindow→fireballs.clear()` y
   luego `ListIterator.set()` abortaba. Guarda COLLIDED antes del callback y retorna si éste limpia
   o reemplaza el elemento, sin reinsertar ni ocultar excepciones. Cuatro regresiones deterministas.

Crash histórico: SIGABRT/ConcurrentModificationException, Main, `ArrayList.Itr.set→updateFireballs→tick`.
No atribuirlo a audio/AX ni a otro hilo. Repeticiones corregidas 90 s/180 s estables, con final en la segunda.
Solo compartir `TMP/charro-crash-sanitized.json`, no IPS/logs crudos con identificadores.
VM 2435 líneas, Screen 2011, Renderer 1236; docs 09/10 reflejan helper y separación. Audio/pbxproj sin cambios.

## Galería permanente y publicación

Canónico: pr-screenshots/iphone15-pr188/ en la raíz del worktree; README, coverage-plan.json,
metadata y SHA. **41 fotos preceden a los arreglos finales de fondo/proyectiles y 19 son retakes posteriores**;
los flags del metadata lo distinguen. No presentar toda la galería como una sola suite del último binario.

- El dueño eligió **desplegables dentro del PR y los README**. El README principal queda íntegramente
  en inglés, con la galería cerca del inicio y las capturas de acción más atractivas primero.
  README principal y galería conservan las 61 imágenes en **21 bloques cerrados**, máximo tres por
  bloque y una en el último. Los PNG originales no se borran ni se reexportan.
- Conservar los 61 PNG originales, metadatos y referencias: no recortar, reexportar ni quitar HUD.
  hero-selection.json fija 10 parejas que cubren 19 peleadores y el extra de 4 GOLPES: 11 muestras.
  Incluye Granadera con Granadero H; no sustituir ni retirar esos archivos al regenerar.
- Descripción del PR: **61 originales en 21 bloques cerrados**, 20 grupos de tres y uno de una.
  Se subieron 50 originales y se conservaron las 11 URL previas; publication.json registra URL y SHA.
  Encabezados, resúmenes, pies de foto y prosa de QA están en **inglés**, con nombres propios respetados.
  Comentario de QA en inglés: resumen, enlace a la descripción y ocho ZIP, sin duplicar imágenes.
  Los 61 adjuntos y ocho ZIP quedan conservados.
- loading="lazy" se mantiene en la fuente, con ancho y altura automática, sin height.
  GitHub filtra loading y JavaScript. Los desplegables controlan **visibilidad**, no garantizan
  cero descargas antes del clic; no prometer un botón de carga estricta dentro de GitHub.
- Este cambio es de presentación; **no hubo otra ejecución de las 849 pruebas ni nuevo build**.
  El dueño autorizó commit y push al mismo PR #188 el 2026-11-16. El commit de correcciones 79e1eb94 está publicado en la rama del PR #188. README, documentación y galería se incorporan con el commit de esta entrega. No se autoriza merge ni se creó otro hosting.
- Actualizar solo el comentario existente y la sección QA de la descripción, sin duplicar informes:
  https://github.com/gabrielhuav/PolitecnicoOpenWorld/pull/188#issuecomment-6907989505

## Otras plataformas y límites

- Android último APK build/install/SHA PASS. **UI tras fondo/proyectiles NO EJECUTADA**: CUA no expuso
  superficie de Android Studio/emulador; tampoco funcionó el único fallback Spotlight autorizado.
  Humo anterior tras insets sí probó práctica/daño 200 P1/pausa/resume/restart; no atribuirlo al binario final.
  App Android force-stop para no mezclar sonido. [Informe Android](../SF/QA_ANDROID_PR188_2026-11-15.md).
- Simulador iPhone 17/iOS 27.0 final: build/install/launch y menú PASS; no repetir como combate final probado.
  Humo previo Android+iOS y plugins corresponde a `pr/184`: [informe anterior](../SF/QA_ANDROID_IOS_2026-11-15.md).
- Mac:92 audios decodifican, 91 rutas activas, 19 atlas/JSON y 337/337 recursos del bundle iguales por ruta/case/SHA.
  No sustituye escucha física. SelectorDevMode 20/60 vs progresión 18/54: extras Planetario/CET 1.
- Biblioteca contiene FESARAGON en su propio asset; borde dorado/miniatura de Biblioteca comprobados.
- Capturas válidas: XCUIScreen original 1178 × 2556/EXIF 8→2556 × 1178 visual. Rechazados app.screenshot
  recortado, 110 CLI negros, selector/carga/Mirroring y banners privados. No repetirlos como fotos válidas.
- No cobertura exhaustiva de todos los movimientos, multijugador iOS, fuente 1,4/orientación opuesta,
  ni certificación universal «sin bugs». No forzar HP/medidores/poses para crear evidencia.

## Referencias para continuar sin repetir trabajo

- Builds: `TMP/pr188-projectile-frameworks-tests-android.log`, `post-projectile-test-counts.json`,
  `pr188-projectile-xcodebuild.log`, `pr188-projectile-install.log`; ambos frameworks y 849 ejecuciones PASS.
- Galería final: `uitest-charro-audio/results/gallery-fixed-final19-authorized.xcresult`,
  `uitest-harness/gallery-fixed-final19-attachments/`, selections-fresh19-first10.json/last09.json.
- Charro 180: `uitest-charro-audio/results/audio-charro-final180.xcresult`,
  `charro-final180-review/review.md` y proof.json. Original final 180 s SHA empieza db3b902e.
- Limpieza: `uitest-cleanup/results/cleanup-20261116T180034Z.xcresult`,
  `uitest-harness/cleanup-final-attachments/`, `test-runner-uninstall.json`, `final-main-menu-launch.json`.
- Herramientas fuera del repo: gallery-review-tool/pow-gallery-review, integrate-gallery-selections.py,
  update-gallery-docs.py, package-gallery.py. Respetar selección fijada y SHA de originales al reusar.
- Publicación completa en inglés: pr188-all61-description-en.md, pr188-captions-en.json y
  pr188-new50-image-urls.json en TMP. No volver a limitar la descripción a 11 muestras.
- Presentación: on-demand-gallery-js-proof.json, on-demand-gallery-markup-proof.json y
  on-demand-browser-http.log. El visor opcional tiene carga real por clic; los desplegables de GitHub no.
- CUA cerrado por agentes. Al retomarlo tras resumen: `cua.rewriteDocumentation()` primero.
  Safari PR 188 puede funcionar aunque inventario diga 0 windows; no leer otras pestañas privadas.
- No hay suite física pendiente. La única acción del dueño pendiente registrada aquí es restaurar AutoLock.
