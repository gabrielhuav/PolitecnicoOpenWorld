# Charro Negro: voces lupinas

Las tres voces nuevas del PR #188 usan el perfil
`wolf_vocoder_and_vocalizations`. El usuario pidió una voz muy bestial y
señaló que la primera mezcla no parecía un lobo. Esta versión usa aullidos
reales del U.S. Fish and Wildlife Service y gruñidos de diseño sonoro CC0
de SFXMint. La voz hablada pierde claridad de forma deliberada.

Un vocoder de diez bandas transfiere la articulación de las frases al
aullido, conservando el tono natural del animal. Los gruñidos siguen la
envolvente de las sílabas. Se expone el aullido al principio y al final de
cada clip dentro de su duración original. La voz humana baja ligeramente
de tono (factor 0.88) y queda como capa de apoyo. La mezcla evita el
subgrave extremo y el trémolo de la versión anterior.

Fuentes, licencias y modificaciones: `newSFAssets/CharroNegro/audio_sources/README.md`.

Se sustituyen únicamente estos archivos en `STREETFIGHTER/SOUNDS`:

| Archivo | Evento | Duración final | LUFS | Pico verdadero |
| --- | --- | ---: | ---: | ---: |
| `special_charro_hurt_1.m4a` | Daño | 4.519002 s | -16.50 | -1.86 dBTP |
| `special_charro_negro.m4a` | Especial | 3.605011 s | -16.37 | -2.01 dBTP |
| `special_charro_negro_win.m4a` | Victoria | 7.027007 s | -16.06 | -1.96 dBTP |

Los nombres, las frases ES/EN y su registro en el juego permanecen como en
el PR #188. La diferencia de duración es inferior a 0.2 ms. La codificación
es AAC, mono, 44100 Hz, 128 kbit/s. El volumen se normaliza en dos pasadas
antes de medir nuevamente el archivo AAC final.

## Reproducir

Desde la raíz del repositorio, con FFmpeg (filtro `rubberband`), ffprobe,
NumPy y SciPy:

```powershell
python PolitecnicoOpenWorld/tools/process_charro_wolf.py --output-dir .codex-tmp/charro-wolf/lobo-v3
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
