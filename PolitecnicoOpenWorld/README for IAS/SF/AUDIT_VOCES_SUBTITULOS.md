# AUDIT de VOCES y SUBTITULOS — HUELUM VS. GOYA

> Generado por `tools/sf_voice_subtitle_audit.py`. **No editar a mano:** se regenera. Las correcciones se escriben en `tools/_audio_review/_SUBTITULOS_AUDIT.csv` (columnas `ES_CORREGIDO` / `EN_CORREGIDO` / `NOTAS`) y se aplican con `tools/sf_apply_subtitle_fixes.py`.

Para escuchar cada clip: `tools/_audio_review/<clip>.mp3`.


## Resumen

| métrica | valor |
|---|---|
| clips con voz | 69 |
| con subtítulo `es` curado | 7 |
| sin subtítulo `es` | 62 |
| hay draft de Whisper para curar | 53 |
| sin draft (probable grito) | 16 |
| a RECORTAR (largos) | 29 |
| a NORMALIZAR | 2 |

## (generico hombres)

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_male_attack_grunt` | attack (fallback) | 2.00s | -16.1 | SAC! SAC! | — | CURAR `es` (hay draft) |

## CHARRO_NEGRO

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_charro_attack_1` | attack | 4.00s | -16.1 | — | — | **RECORTAR** (4.00s); ¿grito? -> poner `-` si no lleva subtitulo |
| `special_charro_attack_2` | attack | 4.00s | -18.7 | Rrrrr. Rrrrrrr. | — | **RECORTAR** (4.00s); **NORMALIZAR** (-18.7 LUFS); CURAR `es` (hay draft) |
| `special_charro_attack_3` | attack | 5.10s | -14.9 | — | — | **RECORTAR** (5.10s); ¿grito? -> poner `-` si no lleva subtitulo |
| `special_charro_hurt_1` | hurt | 3.00s | -15.7 | ¡Cri, cri, cri, cri, cri! | — | **RECORTAR** (3.00s); CURAR `es` (hay draft) |
| `special_charro_hurt_2` | hurt | 3.00s | -15.1 | Ssssssssssssssss | — | **RECORTAR** (3.00s); CURAR `es` (hay draft) |
| `special_charro_hurt_3` | hurt | 2.99s | -16.0 | — | — | **RECORTAR** (2.99s); ¿grito? -> poner `-` si no lleva subtitulo |
| `special_charro_negro` | power (fallback) | 2.80s | -16.1 | Aaaaaaaaaaaaaaaaaaaaa | — | CURAR `es` (hay draft) |

## ESCOMBOY

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_escomboy_attack` | attack | 10.70s | -16.6 | — | — | **RECORTAR** (10.70s); ¿grito? -> poner `-` si no lleva subtitulo |

## ESCOMBOY|ESCOMGIRL|PARAMEDICO_CRUZ_ROJA

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_power_electricity` | power | 3.22s | -15.4 | — | — | ¿grito? -> poner `-` si no lleva subtitulo |

## ESCOMGIRL

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_escomgirl_attack` | attack | 3.15s | -14.6 | — | — | **RECORTAR** (3.15s); ¿grito? -> poner `-` si no lleva subtitulo |
| `special_escomgirl_hurt` | hurt | 3.00s | -16.0 | — | — | **RECORTAR** (3.00s); ¿grito? -> poner `-` si no lleva subtitulo |
| `special_escomgirl_loss` | loss | 3.70s | -15.2 | — | — | ¿grito? -> poner `-` si no lleva subtitulo |

## GRANADERO|POLICIA_CDMX_HOMBRE|POLICIA_GRANADERO_HOMBRE

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_pol_h_attack` | attack | 1.99s | -17.6 | Bueno, solo ven si sabe por qué lo detuvimos | Buenas joven, ¿si sabe porque lo detuvimos? | verificar que el texto case con el audio |
| `special_pol_h_intro` | intro | 15.00s | -16.9 | ¡Está prohibido beber en vía divina! | ¡Está prohibido beber en la vía pública! | verificar que el texto case con el audio |

## GRANADERO|POLICIA_GRANADERO_HOMBRE|POLICIA_GRANADERO_MUJER

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_granadero_win` | win | 27.57s | -17.0 | — | — | ¿grito? -> poner `-` si no lleva subtitulo |

## LA_LLORONA

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_llorona_attack` | attack | 4.00s | -15.1 | ¡Dónde está mi tío! | — | **RECORTAR** (4.00s); CURAR `es` (hay draft) |
| `special_llorona_hurt` | hurt | 4.10s | -16.1 | — | — | **RECORTAR** (4.10s); ¿grito? -> poner `-` si no lleva subtitulo |
| `special_llorona_power` | power | 6.00s | -17.0 | ¡Ay, sí, oh! | — | CURAR `es` (hay draft) |

## LA_PRESIDENTA

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_la_presidenta_attack` | attack | 3.19s | -17.4 | jajajajajaja | — | **RECORTAR** (3.19s); CURAR `es` (hay draft) |
| `special_la_presidenta_hurt` | hurt | 2.22s | -16.1 | Una decisión soberana. | — | CURAR `es` (hay draft) |
| `special_la_presidenta_power` | power | 4.90s | -16.2 | y algunos de los. Hoy estamos | — | CURAR `es` (hay draft) |
| `special_la_presidenta_win` | win | 3.84s | -15.3 | porque patria se escribe con a de mujer | Porque patria se escribe con A de mujer | verificar que el texto case con el audio |

## LA_TZITZIMIME

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_la_tzitzimime_attack` | attack | 3.16s | -14.9 | — | — | **RECORTAR** (3.16s); ¿grito? -> poner `-` si no lleva subtitulo |
| `special_la_tzitzimime_hurt` | hurt | 3.34s | -16.0 | — | — | **RECORTAR** (3.34s); ¿grito? -> poner `-` si no lleva subtitulo |
| `special_la_tzitzimime_power` | power | 6.80s | -17.6 | — | — | ¿grito? -> poner `-` si no lleva subtitulo |
| `special_la_tzitzimime_win` | win | 2.83s | -16.1 | — | — | ¿grito? -> poner `-` si no lleva subtitulo |

## PAPARAZZI_1

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_paparazzi_5` | power | 5.70s | -15.6 | Pero, ¿para qué, o qué? Dime, ¿de qué se trata? Contésame, p | — | CURAR `es` (hay draft) |
| `special_papz1_hurt_1` | hurt | 7.00s | -16.1 | de la chingada que no me estés molestando, ¿eh? Bueno, me va | — | **RECORTAR** (7.00s); CURAR `es` (hay draft) |
| `special_papz1_hurt_2` | hurt | 2.97s | -16.1 | Gabron, ya te dije que me dejes de estar chingando ¿no? | — | **RECORTAR** (2.97s); CURAR `es` (hay draft) |
| `special_papz1_hurt_3` | hurt | 2.97s | -16.1 | Que te calman así... Parale... | — | **RECORTAR** (2.97s); CURAR `es` (hay draft) |

## PAPARAZZI_5

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_paparazzi_5_attack` | attack | 3.54s | -14.6 | ¿Quieres que estén en tu madre, huy? ¡Dímelo! | — | **RECORTAR** (3.54s); CURAR `es` (hay draft) |
| `special_paparazzi_5_hurt` | hurt | 2.50s | -17.1 | ¿Qué te pasa? | — | CURAR `es` (hay draft) |

## PARAMEDICO_CRUZ_ROJA

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_paramedico_cruz_roja_win` | win | 4.00s | -16.6 | No olvides que saber primeros auxilios, marca la diferencia  | No olvides que saber primeros auxilios marca la di | verificar que el texto case con el audio |

## POLICIA_CDMX

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_policia_cdmx_mujer_win` | win | 13.86s | -16.1 | Si le hice el policía me comprometí como mujer a que la ciud | Si dices policía, me comprometí como mujer a que l | verificar que el texto case con el audio |

## POLICIA_CDMX_HOMBRE

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_pol_h_win` | win | 2.47s | -16.0 | Se crea más chingón que nosotros, o qué por eso? | ¿Se cree más chingón que nosotros o qué joven? | verificar que el texto case con el audio |
| `special_policia_cdmx_hombre_power` | power | 4.00s | -15.8 | trabajamos permanentemente en estrategias de seguridad vial. | — | CURAR `es` (hay draft) |

## POLICIA_CDMX|POLICIA_GRANADERO_MUJER

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_pol_m_attack_1` | attack | 1.00s | -16.0 | ¡Ahhh! | — | CURAR `es` (hay draft) |
| `special_pol_m_attack_2` | attack | 2.00s | -16.1 | Ya, ya, ya, ya, ya. | — | CURAR `es` (hay draft) |
| `special_pol_m_hurt` | hurt | 2.30s | -16.1 | ¡Pone jocada! ¡Te regalas! | — | CURAR `es` (hay draft) |

## PRANKEDY

| clip (.mp3) | evento | dur | LUFS | `es` actual | estado |
|---|---|---|---|---|---|
| `special_prankedy_attack_1` | attack | 2.71s | -16.0 | A ver... \| voltéate, voltéate... \| voltéate... Hazme c... | **COMPLETADO** |
| `special_prankedy_attack_2` | attack | 4.90s | -16.0 | ¿Y qué, me vas a pegar o solo te vas a desvestir? | **COMPLETADO** |
| `special_prankedy_attack_3` | attack | 3.53s | -16.0 | ¿Qué pasó...? \| ¿Eh...? | **COMPLETADO** |
| `special_prankedy_attack_4` | attack | 4.77s | -16.0 | ¿Y cómo o qué? \| ¿Qué va a hacer o qué órale? \| Nos agarramos aquí a putazos \| órale de una vez. | **COMPLETADO** |
| `special_prankedy_hurt_1` | hurt | 1.54s | -16.0 | Ya, hermano, ya... \| Cálmate, ya. | **COMPLETADO** |
| `special_prankedy_hurt_2` | hurt | 2.28s | -16.0 | Hermano, ya cálmate \| Ya cálmate, ya. | **COMPLETADO** |
| `special_prankedy_hurt_3` | hurt | 3.03s | -16.0 | Cálmate, ya \| Cálmate, ya, hermano | **COMPLETADO** |
| `special_prankedy_hurt_4` | hurt | 1.34s | -16.0 | ¡Me vas a dejar inválido! | **COMPLETADO** |
| `special_prankedy_loss` | loss | 7.65s | -16.0 | ¡Aaaah! \| La broma, pues, terminó mal... \|¡Ummhh! \| Tengo... fracturado un ojo... \| aquí tengo también una marca del golpe... | **COMPLETADO** |
| `special_prankedy_lowhp` | lowHp | 6.97s | -16.0 | -Uuuu.. \| -¿U qué? \| ¿U qué pend....? \| Te pones al pedo todavía... | **COMPLETADO** |
| `special_prankedy_power` | power | 3.62s | -16.0 | No se sienta un c... eh mi perro, \| que ahorita lo... \| ahorita lo aterrizo, eh. | **COMPLETADO** |
| `special_prankedy_win` | win | 5.40s | -16.0 | Esque yo podré ser ciego, pero... \| sí me doy cuenta cuando alguien es miserable, eh. | **COMPLETADO** |

## REY_GRUPERO

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_rey_grupero` | intro | 4.50s | -18.4 | ¿Fue el hermano Mergaras en la selfie? Claro, claro. ¿Y a ti | — | **NORMALIZAR** (-18.4 LUFS); CURAR `es` (hay draft) |
| `special_rey_grupero_attack` | attack | 2.11s | -16.0 | Fue ir hermano me regalas una selfie | — | CURAR `es` (hay draft) |
| `special_rey_grupero_hurt` | hurt | 2.23s | -16.0 | ¡Vamos! ¡Vamos! ¡Vamos! | — | CURAR `es` (hay draft) |
| `special_rey_grupero_power` | power | 4.10s | -16.4 | Estos son los de la verga, güey, son los de la verga, güey. | — | CURAR `es` (hay draft) |

## ROBOT

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_robot` | power (fallback) | 4.60s | -15.6 | Sistema activado. Objetivo localizado. | — | CURAR `es` (hay draft) |
| `special_robot_attack` | attack | 4.60s | -14.9 | — | — | **RECORTAR** (4.60s); ¿grito? -> poner `-` si no lleva subtitulo |
| `special_robot_hurt` | hurt | 4.80s | -16.8 | Eeeeeeeeeeeeeeeeeee | — | **RECORTAR** (4.80s); CURAR `es` (hay draft) |
| `special_robot_win` | win | 6.70s | -16.6 | La base de datos de virus ha sido actualizada. | — | CURAR `es` (hay draft) |

## SENOR_TIENDA

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_senor_tienda` | power (fallback) | 5.40s | -16.4 | Si el salis que retira te voy, te rompo tu madre y así derec | — | CURAR `es` (hay draft) |
| `special_senor_tienda_attack_1` | attack | 2.73s | -16.0 | Ahora, hasta exigentes de por mis pendejos. | — | **RECORTAR** (2.73s); CURAR `es` (hay draft) |
| `special_senor_tienda_attack_2` | attack | 4.90s | -16.4 | Otra vez tienes a chingar la madre cabrón. A ver, ¿podre que | — | **RECORTAR** (4.90s); CURAR `es` (hay draft) |
| `special_senor_tienda_hurt_1` | hurt | 1.36s | -16.1 | ¡Ya estoy en la ventana! | — | CURAR `es` (hay draft) |
| `special_senor_tienda_hurt_2` | hurt | 2.00s | -16.1 | ¡Sera, este es el Big Wanker! | ¡Ya me agarraste de tu puerquito! | verificar que el texto case con el audio |
| `special_senor_tienda_win` | win | 9.00s | -16.3 | Ya estuvo cabrón, ahora si vete para acá, vamos a rompernos  | — | CURAR `es` (hay draft) |

## YOALLI_EHECATL

| clip (.mp3) | evento | dur | LUFS | draft Whisper | `es` actual | acción propuesta |
|---|---|---|---|---|---|---|
| `special_yoalli_ehecatl` | power | 3.70s | -16.0 | Música | — | CURAR `es` (hay draft) |
| `special_yoalli_ehecatl_attack_1` | attack | 2.54s | -16.3 | ¡Aaaaaaah! | — | **RECORTAR** (2.54s); CURAR `es` (hay draft) |
| `special_yoalli_ehecatl_attack_2` | attack | 3.82s | -15.4 | — | — | **RECORTAR** (3.82s); ¿grito? -> poner `-` si no lleva subtitulo |
| `special_yoalli_ehecatl_hurt_1` | hurt | 1.77s | -16.4 | ¡Aaaaaaah! | — | CURAR `es` (hay draft) |
| `special_yoalli_ehecatl_hurt_2` | hurt | 2.07s | -16.6 | ¡Aaaaaah! | — | CURAR `es` (hay draft) |

## Hallazgos estructurales

- special_phrases[PRANKEDY].audio no existe: special_prankedy.ogg
- special_phrases[PAPARAZZI_1].audio no existe: special_paparazzi_1.ogg
- special_phrases[LAZARO].audio no existe: special_lazaro.ogg
- special_phrases[POLICIA_CDMX_HOMBRE].audio no existe: special_policia_cdmx_hombre.ogg
- special_phrases[PARAMEDICO].audio no existe: special_paramedico.ogg
