# CET 1 — fuentes de los tres escenarios

`CET1.png`, `CET1 Noche 1.png` y `CET1 Noche 2.png` son las fuentes actuales de
día, noche y noche tenebrosa. Se prepararon con la herramienta integrada
`image_gen`; los prompts completos están en `generation_prompts.json`.
Las dos ilustraciones originales del PR se conservan en
`../legacy_static_backgrounds_pre_webp/` como referencias de procedencia.

Las fuentes actuales no llevan un logo incrustado: el pipeline aplica el logo
POW canónico después del paneo, una sola vez por cuadro y en posición fija.

Desde la raíz exterior del repositorio, con Python y Pillow instalados:

```sh
python3 PolitecnicoOpenWorld/tools/build_map_backgrounds.py \
  --input additional_assets/STREETFIGHTER/cet1 \
  --static-out /tmp/pow-cet1-generated-static
python3 -m unittest discover \
  -s PolitecnicoOpenWorld/tools/tests -p test_map_backgrounds.py -v
```

La generación PNG no requiere FFmpeg. El script escribe únicamente los tres
tríos CET 1 en los assets de producción y comprueba todos los fondos del tema
compartido. Los estáticos auxiliares del primer comando quedan fuera del repo.

Cada trío contiene un atlas WebP estático de 1920×1890, su JSON (cuadros 480×270,
28 cuadros, 6 fps, vuelta ping-pong) y una miniatura WebP de 256×144 para el
selector. El motor reproduce subrectángulos del atlas; no se usa WebP animado.
Las fuentes de alta resolución permanecen fuera del paquete Android.
