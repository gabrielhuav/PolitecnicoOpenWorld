# PENDIENTES del proyecto (corte 2026-07-20, post-release 1.0.0.12)

> Lista VIVA de deudas tras el release a Play Store (workflow CI/CD verde, run 29683280798).
> Divididas por QUIÉN las desbloquea. Al completar una, márcala y actualiza los docs 00–09.

## 🧑‍🎨 BLOQUEADAS EN EL DUEÑO (assets/oído)

1. **Mapas UAM Azcapotzalco y UAM Cuajimalpa:** quiere actualizarlos, pero los 6 atlas
   actuales YA vienen de los videos más recientes en disco (28 frames, fuentes
   `UAM Azcapo*.mp4` 17JUL / `UAM Cuajimala New*.mp4` 18JUL). **Faltan videos nuevos** →
   al llegar, correr `tools/build_map_backgrounds.py` (limpiar stems, ver QA_SF_STAGES).
2. **Sprites nuevos (tandas 5–8, hojas 20–33) con ChatGPT/Sol 5.6:** dash/backdash, bloqueo,
   parry, ataques agachado, aéreos, PATADA LARGA, overhead/taunt, agarres, derribos y super
   arts. El prompt completo se entregó al dueño el 2026-07-20 (conversación con Fable).
   Cuando lleguen los PNG: rebanar con el pipeline croma + AGREGAR ESTADOS nuevos en
   `SfFighterState` + cajas en JSON + handlers (ver DISENO §P1).
3. **Audios escom (los 4 clips):** escuchar `tools/_audio_review/special_escom*.mp3` y
   decidir: ¿la voz de escomgirl es correcta? ¿`special_escomboy_attack` (10.7 s!) se
   recorta o se reasigna? El análisis de pitch/Whisper no pudo resolverlo (son gritos).
   Detalle: `AUDIO_INVENTARIO_SF.md` §AUDIT 2026-07-20.
4. **Frases de especiales (subtítulos):** escribir/validar las frases en el LUGAR ÚNICO
   **`assets/STREETFIGHTER/DATA/voice_phrases.json`** (`es`/`en`; el `draft` Whisper es solo
   referencia) escuchando los mp3 de `tools/_audio_review/`. El código ya está cableado y
   APAGADO (`SfVoicePhrases.kt` + `emitVoiceLines`); al terminar solo se enciende
   `voiceSubtitlesEnabled = true`. (La de La Presidenta win ya quedó corregida.)
5. **Assets de personajes ("Llorona de espaldas" etc.):** revisar las hojas de contacto de
   los 17 peleadores en `tools/_contact_sheets/` (1 frame por animación, regenerables con
   `tools/sf_contact_sheet.py <personaje> [first|mid]`). Los frames EMPACADOS de La Llorona
   están de frente → el "de espaldas" hay que cazarlo en dispositivo (¿espejado/turn?).
6. **FES Acatlán variante de día:** regenerar video/atlas con frames coherentes (QA doc).

## 💻 CÓDIGO (siguiente sesión de programación)

1. **Combos:** probar en dispositivo el corte 2026-07-20 (chain/special cancels, contador,
   escalado). Afinar ventanas con el dueño. Luego: los movimientos nuevos cuando lleguen
   las tandas 5–8 (cada uno = estado nuevo + validFrom + handler + cajas).
2. **Rebuild + prueba en dispositivo** de todo lo del 2026-07-20 (combos + frase Presidenta).
3. (Si el dueño confirma contenidos escom) recortes/remapeos de esos .ogg.

## ✅ Cerrado en esta pasada (2026-07-20)

- Audit MP3→OGG retomado: `tools/sf_audio_audit.py` + reporte + `_audio_review/` regenerado
  (70→~86 mp3). Frase win de La Presidenta corregida en el VM (verificada con Whisper).
- Combos 3rd Strike primer corte implementado (VM+State+Screen+strings ES/EN).
- QA visual: `tools/sf_contact_sheet.py` + 17 hojas de contacto generadas.
- Docs actualizados: QA_SF_STAGES (subtítulos multilínea ya estaba hecho; UAM bloqueado),
  AUDIO_INVENTARIO (§audit), DISENO (§2026-07-20 y §P1), 07 (§combos), 00_INDEX.
