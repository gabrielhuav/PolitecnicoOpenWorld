# Charro Negro: voces lupinas

Las tres voces nuevas del PR #188 se procesan con un tono más grave y una
capa de garganta para darles un gruñido de lobo. Se conserva la voz principal
para que las frases puedan entenderse. El procesamiento mezcla una voz a
0.81 veces el tono original con una capa a 0.53, filtrada y saturada suavemente.
Ambas mantienen el tempo. No se usan muestras externas de animales.

Se sustituyen únicamente estos archivos en `STREETFIGHTER/SOUNDS`:

| Archivo | Evento | Duración final | LUFS | Pico verdadero |
| --- | --- | ---: | ---: | ---: |
| `special_charro_hurt_1.m4a` | Daño | 4.519002 s | -15.88 | -1.93 dBTP |
| `special_charro_negro.m4a` | Especial | 3.605011 s | -16.21 | -1.86 dBTP |
| `special_charro_negro_win.m4a` | Victoria | 7.027007 s | -16.26 | -1.83 dBTP |

Los nombres, las frases ES/EN y su registro en el juego permanecen como en
el PR #188. La diferencia de duración es inferior a 0.2 ms. La codificación
es AAC, mono, 44100 Hz, 128 kbit/s. El volumen se normaliza en dos pasadas
antes de medir nuevamente el archivo AAC final.

## Reproducir

Desde la raíz del repositorio, con FFmpeg (filtro `rubberband`) y ffprobe:

```powershell
python PolitecnicoOpenWorld/tools/process_charro_wolf.py --output-dir .codex-tmp/charro-wolf/processed
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
