# Metodología v2 — frases icónicas multi-fuente (NO instalar en APK hasta aprobación)

## Problema de la v1
- 1 vídeo genérico por peleadór + “pico de energía” → no es una **frase famosa**.
- Paparazzi 1 y 5 usaban el **mismo** audio / ytsearch genérico.
- Prankedy no priorizaba su **canon** (Paparazzi series, Señor de la Tienda, tanque de gas).

## Principios v2

1. **Primero la frase, luego el archivo**  
   Cada peleadór tiene un catálogo de *catchphrases / momentos* con:
   - `phrase_id`, texto esperado (o descripción del momento),
   - **varias** fuentes (URL YouTube canónica, reupload, X, local MP3),
   - `priority` por views/canonicidad,
   - ventanas de tiempo sugeridas si se conocen.

2. **Multi-fuente, multi-clip**  
   Por peleadór se bajan **≥3 fuentes** cuando sea posible y se extraen **varios candidatos**
   (top-N picos de voz en zonas de habla, no un solo peak del archivo entero).

3. **Canon del canal Prankedy (prioridad máxima)**  
   | Personaje | Fuentes canónicas |
   |-----------|-------------------|
   | PAPARAZZI_1 | `Paparazzi 1` oficial `7Ug2kyQeRHQ` (~8.1M) + local `PAPARAZZI 1!! BROMA.mp3` |
   | PAPARAZZI_5 | `Paparazzi 5` oficial `AEmVeK88HIs` (~5.5M) — **no** reusar Pap1 |
   | SENOR_TIENDA | `gzXw_4kfZBo` (~24M), `0z8RQXE_2NY` Demando…, `2USZ8cH9Vjg` Adiós… |
   | PRANKEDY | varias bromas propias + tanque de gas + drive-thru / negocios |

4. **Playlist Paparazzi completa** (canal @Prankedy)  
   IDs obtenidos 2026-07-18 (flat-playlist):
   ```
   7Ug2kyQeRHQ Paparazzi 1 (8.1M)
   svPQhmXsyOg Paparazzi 2 (4.9M)
   cjLoWWgxRpQ Paparazzi 3 (3.1M)
   o3a925d9PgA Paparazzi 4 (3.1M)
   AEmVeK88HIs Paparazzi 5 (5.5M)
   … 6–19 … ufjkSjGWJlc Paparazzi 16 (5.8M)
   6dfQvumn5Ts Paparazzi 20
   ```

5. **Extracción de candidatos**  
   - Mono 44.1 kHz WAV.
   - Top-K ventanas ~1.0–1.2 s por **speech-score** (RMS × ZCR × mid), no solo RMS.
   - Guardar en `candidates/<FIGHTER>/<phrase_id>__srcN__peakK.wav` + `candidates_report.json`.
   - **No** copiar a `assets/` hasta que el dueño apruebe.

6. **YouTube anti-bot**  
   Preferir: `yt-dlp --cookies-from-browser chrome` (o edge/firefox).  
   Sin cookies, muchos vídeos canónicos fallan (403 / “Sign in to confirm you’re not a bot”).

7. **Legal**  
   Clips cortos transformativos para SFX de juego; atribución y riesgo de derechos de imagen/voz
   en release comercial. Preferir material que el dueño aporte (local).

## Scripts
- `catalog_phrases_v2.json` — catálogo frase-first (+ playlists Paparazzi y Señor Tienda).
- `scrape_phrases_v2.py` — descarga multi-fuente + top-K candidatos (sin install).
- `../voice_lab.py` — juntar / normalizar / pitch / FX / STT opcional (sin deepfake de famosos).
- `DETALLE_FRASES_POR_PELEADOR.md` — **qué se pone en cada peleadór** (frase + fuente + archivo).
- Informe: `candidates/FINDINGS_REPORT.md` (generado al scrapear).

## Voice lab vs deepfake

| Voice lab (sí) | Deepfake clone (no por defecto) |
|----------------|----------------------------------|
| VAD + picos de habla | Entrenar RVC/Tortoise con horas de voz ajena |
| Whisper STT para localizar palabras | Generar frases nuevas con voz de Sheinbaum/Prankedy |
| Pitch/formant/FX pelea | Alto riesgo legal sin licencia de la persona |

Personajes **ficticios** (robot, llorona sintética) sí pueden usar síntesis TTS más adelante.
