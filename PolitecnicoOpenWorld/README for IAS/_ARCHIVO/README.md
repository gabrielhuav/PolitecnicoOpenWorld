# _ARCHIVO — docs históricos (NO son tareas pendientes)

> **ES:** Todo lo de esta carpeta está **✅ EJECUTADO o SUPERADO**. Se conserva porque el código y
> los docs 00–09 lo citan por nombre como registro ("ver PLAN_DI_hilt.md", tombstones, etc.) y
> porque documenta el *porqué* de decisiones pasadas. **No retomes nada de aquí como tarea.**
> **EN:** Everything here is **done or superseded**. Kept because code/docs reference these names
> as historical record. Do NOT pick anything up from here as pending work.

| Archivo | Qué fue | Estado |
|---|---|---|
| `PLAN_dedup_routing.md` | Diseño de la de-dup de la cadena de routing (Etapa 2 senior) | ✅ Ejecutado 2026-07-04 (`RoadRouter.kt` + tests) |
| `PLAN_descomponer_WorldMapViewModel.md` | Diseño de la descomposición del VM en managers (Etapa 3) | ✅ Ejecutado 2026-07-04 (6 managers + fachada `combine`) |
| `PLAN_DI_hilt.md` | Diseño de la migración a Hilt (Etapa 4) | ✅ Ejecutado 2026-07-04 (9 VMs `@HiltViewModel`) |
| `PENDIENTE_calidad.md` | Deuda detekt + camino al gate bloqueante (Etapa 5) | ✅ Gate bloqueante activo y `config/detekt/baseline.xml` COMMITEADO. Deuda perdonada por el baseline: quemarla en PRs chicos (params sin uso en firmas, UseRequire…) |
| `ANALISIS_codigo.md` | Informe 2026-06-21: clases grandes, duplicación, perf, prioridades | Insumo del programa senior; casi todo ejecutado |
| `PROMPT_nueva_optimizacion.md` | Prompt de arranque de sesión (estado 2026-06-21) | Superado: usa `GUIA_mantenimiento_no_senior.md` + el prompt de reuso de `00_INDEX.md` |
| `CHECKPOINT_2026-07-04_siguiente_sesion.md` | Checkpoint sesión M2/M3 + selector de misiones | Sus 4 tareas ✅ hechas; detalle vive en docs 00–09 y CAMPAIGN |
| `CHECKPOINT_2026-07-08_siguiente_sesion.md` | Checkpoint fase 1 M2 en el lobby + vida universitaria | Superado por `CHECKPOINT_2026-07-11_sesion_QA.md` |
| `CHECKPOINT_2026-07-11_sesion_QA.md` | QA Misiones 2/3 + fixes SF + cómics M2→M3 | ✅ Ejecutado (compilado y committeado); el estado vigente vive en 07 + `../AUDIT_SF_MULTIPLAYER.md` |

*(La receta viva del patrón manager/refactor sigue en `../CHECKPOINT_SENIOR_refactor.md`, que NO
está archivado a propósito.)*
