# ESM IPN: escenario de La Planchada

Arte original creado el 2026-11-06 mediante la herramienta integrada `image_gen.imagegen`.
La foto compartida por el usuario sirve como referencia arquitectónica: fachada en forma de cuña,
escudo IPN/ESM, entrada acristalada y árboles. Los tres PNG conservan el mismo encuadre.
No se conservan la insignia de aniversario, fecha ni cintillo editorial de la fotografia.
Los prompts finales y las referencias de cada variante estan en `generation_prompts.json`.

## Reconstruccion

Desde la raiz del repositorio:

```powershell
python -X utf8 PolitecnicoOpenWorld/tools/build_esm_backgrounds.py
```

La salida de revision queda en `.codex-tmp/esm-preview/`. El script usa el pipeline
existente `build_map_backgrounds.py`, sin regenerar escenarios anteriores. Se aplica
el logo POW canonico de `nuevoMaterial17JUL/logoPOW.png`.

Cada variante produce un atlas WebP lossless RGB de 1920x1890, 28 cuadros de 480x270,
JSON a 6 fps con loop ping-pong y miniatura WebP de 256x144. La animacion es el paneo
Ken Burns reproducible del pipeline, no un video generado. Los PNG estaticos de respaldo
quedan fuera del APK. Para instalar tras revisar:

```powershell
Copy-Item .codex-tmp/esm-preview/IMAGES/fondo_esm* PolitecnicoOpenWorld/app/src/main/assets/STREETFIGHTER/IMAGES/
```

El selector registra día, noche y noche tenebrosa en `SfTheme.kt`; el escenario hogar
de La Planchada se define en `SfStageCatalog.kt`. La enfermera espectral lleva el
uniforme blanco y verde del IMSS; el escenario representa la ESM del IPN que pidió
el usuario. El personaje reutiliza los audios existentes de La Llorona.

## QA

Se revisaron visualmente los tres originales y las miniaturas: arquitectura identificable,
texto de ESM legible, suelo despejado, sin personajes o rival horneado en la imagen,
sin overlays de la fotografia. El empaquetado comprueba limites 2048, rejilla, 28 cuadros,
miniaturas, formato WebP real y diferencias de pixeles en el paneo. La verificacion
completa dentro del dispositivo forma parte del QA del PR; no se afirma aqui ejecutada.
