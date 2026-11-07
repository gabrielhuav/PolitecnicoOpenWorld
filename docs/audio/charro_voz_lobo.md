# Charro Negro: voces lupinas

Las tres voces nuevas del PR #188 usan el perfil `very_bestial`, solicitado
por el usuario: domina el gruñido lupino y se sacrifica claridad al hablar.
El procesamiento mezcla una voz a 0.70 veces el tono original con una capa
de garganta a 0.47, filtrada, saturada y modulada a 37 Hz. Una tercera capa
rasposa a 0.88 refuerza el rugido. Los pesos respectivos son 0.40, 0.60 y
0.18. Todas mantienen el tempo. No se usan muestras externas de animales.

Se sustituyen únicamente estos archivos en `STREETFIGHTER/SOUNDS`:

| Archivo | Evento | Duración final | LUFS | Pico verdadero |
| --- | --- | ---: | ---: | ---: |
| `special_charro_hurt_1.m4a` | Daño | 4.519002 s | -15.90 | -2.00 dBTP |
| `special_charro_negro.m4a` | Especial | 3.605011 s | -15.89 | -2.00 dBTP |
| `special_charro_negro_win.m4a` | Victoria | 7.027007 s | -16.01 | -2.01 dBTP |

Los nombres, las frases ES/EN y su registro en el juego permanecen como en
el PR #188. La diferencia de duración es inferior a 0.2 ms. La codificación
es AAC, mono, 44100 Hz, 128 kbit/s. El volumen se normaliza en dos pasadas
antes de medir nuevamente el archivo AAC final.

## Reproducir

Desde la raíz del repositorio, con FFmpeg (filtro `rubberband`) y ffprobe:

```powershell
python PolitecnicoOpenWorld/tools/process_charro_wolf.py --output-dir .codex-tmp/charro-wolf/bestial
```

El script recupera los originales del commit
`b191a6f8ddec7d6bf4fa0b3b03c4b8b89b656301`, incluido en la historia de esta
rama. Nunca vuelve a procesar una voz que ya tiene el efecto. Comprueba
duración, codec, canales, frecuencia, sonoridad y pico verdadero antes de
guardar cada resultado. El reporte contiene hashes de entrada y salida.
La salida se prepara fuera de los assets para permitir una escucha previa;
los tres M4A verificados se copian después a `STREETFIGHTER/SOUNDS`.

La validación técnica no sustituye la escucha. Para revisar el resultado en
el juego, reproducir daño, especial y victoria del Charro con subtítulos,
primero en el escaparate de audio y después en combate.
