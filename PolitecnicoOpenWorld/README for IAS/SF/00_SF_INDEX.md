# 🥊 MODO PELEAS — "HUELUM VS. GOYA" · Índice

> Este es el índice del **modo de peleas**. El otro modo del juego (mundo libre POW) vive
> en `../MUNDO/`. Lo compartido por ambos (arquitectura, capa de datos, convenciones) está
> en la raíz de `README for IAS/`.
>
> ⚠️ **En el CÓDIGO el modo se sigue llamando `street_fighter` / `Sf*`** (ids, paquetes,
> clases). "Huelum vs. Goya" es solo el nombre de cara al jugador. No renombres.

## Por dónde empezar

1. `../09_CONVENTIONS_GOTCHAS.md` — convenciones OBLIGATORIAS (MVVM, estado inmutable
   `_state.update { it.copy(...) }`, strings ES+EN con paridad, CRLF en `.kt`).
2. `../_SESION_ACTUAL.md` — **qué se está haciendo AHORA MISMO** y qué sigue.
3. `DISENO_ARCADE_SF_POW.md` — decisiones cerradas del modo, por fecha. El documento vivo.
4. El doc concreto de lo que vayas a tocar (tablas de abajo).

## Mapa de documentos

### Diseño y estado del modo

| Archivo | Contenido |
|---|---|
| `DISENO_ARCADE_SF_POW.md` | **El doc central.** Roster, escalera del arcade, moveset 3rd Strike, combos, tutorial, fatality, y el registro de cambios por fecha. |
| `SF_STAGES_MAPS_UNLOCK.md` | Escenarios y cómo se desbloquean. |
| `QA_SF_STAGES_2026-07-18.md` | QA de escenarios. |
| `AUDIT_SF_MULTIPLAYER.md` | Auditoría del 1v1 en red (WS y BT/LAN). |

### Assets gráficos (sprites)

| Archivo | Contenido |
|---|---|
| `FLUJO_ASSETS_SF.md` | **El pipeline completo** de hoja croma → atlas empaquetado. |
| `GUIA_regeneracion_sprites_croma.md` | Guía por peleador: qué hoja produce qué animación, y el histórico de cada regeneración. |
| `ASSETS_STREETFIGHTER_MIGRACION.md` | Migración de los assets originales a los propios de POW. |

### Audio y voces

| Archivo | Contenido |
|---|---|
| `AUDIO_INVENTARIO_SF.md` | Inventario de audio del modo. |
| `SF_SPECIAL_VOICES_SFX.md` | Voces de special y SFX. |
| `AUDIT_VOCES_SUBTITULOS.md` | **Generado**, no editar a mano: audit por clip (duración, LUFS, draft, subtítulo). |
| `PROMPT_traspaso_audio_subtitulos.md` | Traspaso listo para pegar a otra IA con el trabajo de audio pendiente. |

### Traspasos

| Archivo | Contenido |
|---|---|
| `PROMPT_SOL56_TANDAS_NUEVAS.md` | Tandas de hojas nuevas. |

## Herramientas del modo (en `tools/`)

| Herramienta | Para qué | Coste |
|---|---|---|
| `sf_audit_frames_auto.py --sheets` | **Audit AUTOMÁTICO** de los atlas ya empaquetados. Señala celdas con varias siluetas, cuadros duplicados, vacías, saltos de escala y figuras cortadas. Con `--sheets` dibuja `_PROBLEMAS_<char>.png` con SOLO lo sospechoso. | segundos |
| `sf_audit_sheets.py [--only <char>]` | Hojas de contacto COMPLETAS: todas las animaciones de un peleador. Para revisión humana exhaustiva. | ~1 min |
| `sf_contact_sheet.py <char>` | Hoja rápida: 1 cuadro por animación. | segundos |
| `sf_voice_subtitle_audit.py` | Audit de voces y subtítulos (SOLO LECTURA). | ~1 min |
| `sf_apply_subtitle_fixes.py` | Aplica al JSON las correcciones del dueño desde el CSV. | segundos |
| `slice_sf_chroma_sheets.py <hoja> <char> --list` | **Dry-run** del recorte: enseña los blobs sin escribir. | segundos |
| `pack_sf_character.py <char> <Titulo> --gen <STAGING>` | Empaqueta atlas + JSON. | ~30 s |
| `fix_llorona_projectile.py --gen <STAGING>` | Re-extrae los `proj-*` de La Llorona. | segundos |

**Salida de las hojas:** `tools/_audit_sheets/`
**MP3 para escuchar:** `tools/_audio_review/` (⚠️ en `.gitignore`, no viaja por git)

## Las reglas que más caro han salido

1. **NUNCA re-recortes hojas que ya están bien.** Una sesión re-recortó las 520 hojas de los
   18 peleadores "por si acaso" y metió una regresión que hubo que revertir con `git checkout`.
   Recorta SOLO la hoja que falla, verifica con `--list` y con la hoja de contacto, y **solo
   entonces** empaqueta.
2. **La hoja 12 es especial** (rejilla 4×3, fila 3 = efectos del proyectil). Si la tocas, hay
   que re-aplicar `tools/fix_llorona_projectile.py`.
3. **`SHEETS` en `slice_sf_chroma_sheets.py` lo comparten los 18 peleadores.** Si una hoja
   concreta trae otra cantidad de poses, se anota en `SHEET_OVERRIDES` por `(personaje, hoja)`;
   tocar la tabla global rompe a los demás.
4. **Antes de dar por bueno un cambio de recorte**, corre el audit automático y compara contra
   el slicer commiteado: un cambio de umbral puede arreglar a uno y romper a otros diecisiete.
5. **La Llorona es el caso difícil**: su arte tiene efectos que el croma separa del cuerpo.
   Verificación visual obligatoria cuadro a cuadro.
