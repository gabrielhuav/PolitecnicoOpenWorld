# PROMPT (trabajo FINAL) — Encuadre panorámico + salto en TODOS los mapas de HUELUM VS. GOYA

> Tarea diferida acordada con el dueño (2026-07-18). Hacerla AL FINAL, mapa por mapa, sin prisa.
> Objetivo: que los 16 escenarios se vean panorámicos y los peleadores SIEMPRE pisen el suelo
> (no "flotando"), y que al saltar se revele más del mapa hacia arriba — como ya quedó en los 5
> mapas nuevos (Facultad de Medicina, FES Aragón, Pirámide del Sol, UAM Cuajimalpa, Zócalo).

## Contexto imprescindible (leer antes)

- `README for IAS/09_CONVENTIONS_GOTCHAS.md` (MVVM, CRLF, gotcha miembro-vs-extensión, docs).
- `README for IAS/QA_SF_STAGES_2026-07-18.md` (historia del framing + salto + recomendación).
- Regla del dueño: los 11 mapas YA aprobados NO se tocan salvo que en dispositivo se vean mal.
  **NO aplicar un zoom único a los 16 en bloque** (el zoom correcto depende de cada atlas).

## Cómo funciona el sistema (ya está listo; solo hay que AFINAR por mapa)

Archivo: `app/src/main/java/.../features/streetfighter/ui/StreetFighterScreen.kt`

1. **Encuadre por escenario** — `data class SfBgFraming(zoom, offsetY)` + tabla
   `SF_BG_FRAMING: List<Pair<String, SfBgFraming>>` (match por SUBSTRING del archivo → cubre las
   3 luces día/noche_1/noche_2 de una vez). Ej. actual:
   ```
   "facultad_medicina" to SfBgFraming(zoom = 1.35f),
   "fes_aragon"        to SfBgFraming(zoom = 1.30f),
   "piramidesol"       to SfBgFraming(zoom = 1.30f),
   "uam_cuajimalpa"    to SfBgFraming(zoom = 1.30f),
   "zocalo"            to SfBgFraming(zoom = 1.30f),
   ```
   - `zoom > 1` amplía el fondo ANCLÁNDOLO AL PISO (recorta el cielo por arriba) → panorámico y
     peleadores en el suelo. `offsetY` (unidades de escena, + baja la imagen) afina el anclaje.
   - Sin entrada en la tabla = `zoom 1, offsetY 0` = comportamiento clásico (los 11 aprobados).
   - NO regenera assets; solo cambia cómo se DIBUJA (en `drawAnimatedBackground`/`drawFullBackground`).

2. **Parallax vertical de SALTO** — `drawScene` calcula `jumpFrac` (0..1, altura del peleadór más
   alto; apex ≈ 90 px, divisor `/90f`) y lo pasa a los dibujantes. En mapas CON zoom (headroom de
   cielo) el fondo BAJA hasta `headroom` al saltar → se ve más arriba. En mapas SIN zoom no hay
   headroom → sin efecto. **Consecuencia:** para que un mapa tenga "salto con cámara" DEBE tener
   una entrada en `SF_BG_FRAMING` con `zoom > 1`.

## Los 16 mapas (slug para `SF_BG_FRAMING`) — estado

Fuente: `SfStageCatalog.kt` (`Stage.slug`). Ya con framing (nuevos): `facultad_medicina`,
`fes_aragon`, `piramidesol`, `uam_cuajimalpa`, `zocalo`.

Faltan por revisar/afinar (los 11 "aprobados" — tocar SOLO si en dispositivo se ven cuadrados o
con peleadores flotando): `escom`, `queso_ipn`, `esime_azc`, `cecyt_9`, `cecyt_2`,
`unam_biblioteca_cu`, `fes_acatlan`, `uam_azcapo`, `islamunecas`, `mictlan`,
`campos_agave_jalisco`.

## Procedimiento (uno por uno, verificado en dispositivo)

1. Rebuild y entra a una pelea en el mapa (las 3 luces).
2. ¿Se ve cuadrado / peleadores flotando? Agrega/edita su entrada en `SF_BG_FRAMING`:
   - Empieza con `zoom = 1.30f`. Sube (1.35–1.45) si sigue viéndose "alto"; baja (1.15–1.25) si
     recorta demasiado contenido útil.
   - Si el piso no queda exacto, ajusta `offsetY` (p. ej. `+6f` baja la imagen, `-6f` la sube).
3. Verifica el SALTO: debe revelar más cielo/edificio arriba sin mostrar negro abajo (el sistema
   ya lo acota a `headroom`; si se ve poco recorrido, se puede bajar el divisor `/90f` en
   `drawScene` — afecta a TODOS los mapas con zoom, hazlo con criterio).
4. Repite por mapa. Los que ya se ven bien SIN zoom, déjalos fuera de la tabla.

## Alternativa (si el encuadre por código no basta)

Si un mapa necesita más que zoom (p. ej. el horizonte está muy alto en el atlas), regenerar su
video con recorte distinto y re-procesar con `tools/build_map_backgrounds.py`
(ver `QA_SF_STAGES_2026-07-18.md` → sección de material nuevo: nombres limpios → slug exacto,
logo POW sobre Gemini, `method=6` en el pipeline oficial). Preferir SIEMPRE el encuadre por
código primero (no infla el APK ni re-genera).

## Multiplayer

El framing + salto son 100% render del cliente sobre `onlineMapFile` (mismos assets en el APK),
así que aplican IGUAL en Render/BT/LAN sin tocar nada de red (ver `AUDIT_SF_MULTIPLAYER.md`
§AUDIT 2026-07-18m). No hay trabajo extra de MP para esto.

## Al terminar

- Protocolo de docs 09: actualizar `QA_SF_STAGES_2026-07-18.md` (marcar cada mapa afinado) y, si
  es user-facing, `README.md` bilingüe (EN+ES).
- Verificar CRLF y balance de llaves en `StreetFighterScreen.kt` (Read, no bash).
- Commit/PR a `main` (dispara el CI/CD; ver `pow-production-cicd-flow`).
