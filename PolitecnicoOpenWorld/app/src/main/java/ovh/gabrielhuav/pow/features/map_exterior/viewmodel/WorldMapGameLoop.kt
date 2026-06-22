package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

// ─── DE-DUP par 8 (2026-06-21) ────────────────────────────────────────────────────────────────
// startGameLoop() VIVE AHORA SOLO COMO MIEMBRO en WorldMapViewModel.kt (NO se movió a extensión).
//
// Motivo: era el game loop (corre cada tick, máximo riesgo) y el gemelo divergía MASIVAMENTE en
// ambos sentidos (miembro 441 líneas con campaña/tráfico/aceleración dinámica/Prankedy; esta
// extensión 302 líneas, más vieja, pero con un bloque de AUDIO único que estaba muerto). Mover el
// miembro a la extensión habría re-enlazado su cascada gigante (subsistema de calles/tráfico) y
// arriesgado un break como el del par 2.
//
// Decisión (usuario, 2026-06-21): FUSIONAR el audio dentro del MIEMBRO (aditivo, sin re-enlace de
// cascada) y borrar esta extensión muerta. El miembro de WorldMapViewModel.kt es ahora la única y
// canónica versión, con su lógica completa + el bloque de audio (caminar/correr/coche/zombi-cerca).
// Ver README for IAS/DEDUP_VM_pendiente.md, par 8.
