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
| `PENDIENTES_SF_2026-07-16.md` | Pendientes SF de media sesión (IA ✅, stun-lock, revancha, LAN UDP) | Superado / parcialmente hecho; estado vivo en **07** |

*(La purga del 2026-11-21 retiró de aquí los checkpoints, historiales de sesiones, sesiones previas
del 4 y 5 de noviembre, el changelog viejo del 00 y los prompts de traspaso ya ejecutados: siguen en el historial de git.
La receta viva del patrón manager/refactor sigue en `../CHECKPOINT_SENIOR_refactor.md`.
El estado del modo pelea se actualiza en **07 §HUELUM VS. GOYA**, no aquí.)*
