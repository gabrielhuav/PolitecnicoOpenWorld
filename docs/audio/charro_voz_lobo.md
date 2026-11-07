# Charro Negro: voces lupinas

Las tres voces nuevas del PR #188 usan el perfil
`adult_wolf_vocoder_and_vocalizations`: una voz bestial con un timbre más
grave y corpulento de lobo adulto. Usa aullidos reales del U.S. Fish and
Wildlife Service y gruñidos de diseño sonoro CC0 de SFXMint. La voz hablada
pierde claridad de forma deliberada.

Un vocoder de diez bandas transfiere la articulación de las frases al
aullido. El tono y los formantes del aullido bajan a un factor 0.68 y los
del gruñido a 0.78, conservando el tempo. Se refuerzan las resonancias de
220–240 Hz y se atenúan los agudos para reducir el timbre fino. Los
gruñidos siguen la envolvente de las sílabas y tienen mayor peso en la
mezcla. Se expone el aullido al principio y al final de cada clip dentro
de su duración original. La voz humana baja de tono (factor 0.80) y queda
como capa de apoyo.

Fuentes, licencias y modificaciones: `newSFAssets/CharroNegro/audio_sources/README.md`.

Se sustituyen únicamente estos archivos en `STREETFIGHTER/SOUNDS`:

| Archivo | Evento | Duración final | LUFS | Pico verdadero |
| --- | --- | ---: | ---: | ---: |
| `special_charro_hurt_1.m4a` | Daño | 4.519002 s | -16.45 | -1.61 dBTP |
| `special_charro_negro.m4a` | Especial | 3.605011 s | -16.16 | -2.89 dBTP |
| `special_charro_negro_win.m4a` | Victoria | 7.027007 s | -16.08 | -2.97 dBTP |

Los nombres, las frases ES/EN y su registro en el juego permanecen como en
el PR #188. La diferencia de duración es inferior a 0.2 ms. La codificación
es AAC, mono, 44100 Hz, 128 kbit/s. Una compresión suave controla los picos
de los graves antes de normalizar en dos pasadas con objetivo -16 LUFS y
-3 dBTP. Se vuelve a medir el AAC final para comprobar que el pico real
quede por debajo de -1.5 dBTP tras la codificación.

## Reproducir

Desde la raíz del repositorio, con FFmpeg (filtro `rubberband`), ffprobe,
NumPy y SciPy:

```powershell
python PolitecnicoOpenWorld/tools/process_charro_wolf.py --output-dir .codex-tmp/charro-wolf/lobo-adulto-v4
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
