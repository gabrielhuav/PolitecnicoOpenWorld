# Limpieza medida — alcance y ejecución

Medición del checkout `pr/158`, 2026-11-04. El dueño dio autorización expresa durante
la sesión para continuar y ejecutar los cambios sin confirmaciones adicionales.
Estos pesos corresponden a archivos versionados presentes, no al historial de `.git`
ni al APK. Borrar del árbol actual no elimina objetos históricos ni reduce por sí
solo el tamaño de un clon completo. No se propone reescribir historia.

## Lote 1 ejecutado: un solo archivo

| Ruta exacta desde la raíz | Peso | Justificación |
|---|---:|---|
| `detekt-cli.zip` | 61,37 MiB | Instalador comprimido. La CLI ya está extraída en `detekt-cli-1.23.8/`; CI descarga su propia copia, no consume este ZIP versionado. |

Se retiró **solo `detekt-cli.zip`** del checkout y de Git,
y se añadió `/detekt-cli.zip` a `.gitignore`. Se conserva `detekt-cli-1.23.8/`, por lo que
los comandos locales documentados siguen funcionando. Se revisaron referencias y
`git diff --stat`. Autorizado por el dueño en esta sesión.

## Lo que requiere más trabajo antes de borrar

`newSFAssets/` ocupa **1.037,7 MiB / 9.589 archivos versionados** antes de esta reparación.
El GEN canónico `GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio/` tiene
6.668 archivos / 153,03 MiB y es necesario para reconstruir peleadores completos.
No es basura ni se debe eliminar.

Comparación SHA-256 contra el contenido del GEN canónico:

| Carpeta bajo `newSFAssets/` | Archivos | MiB | Archivos idénticos en canónico | MiB idénticos |
|---|---:|---:|---:|---:|
| `GEN_lallorona_intermedio` | 200 | 5,58 | 165 | 4,57 |
| `GEN_prankedy_intermedio` | 338 | 5,07 | 2 | <0,01 |
| `GEN_prankedy_senortienda_rey_paparazzi_uniform_intermedio` | 712 | 15,70 | 171 | 7,16 |
| `GEN_prankedy_senortienda_uniform_intermedio` | 356 | 8,15 | 71 | 2,57 |
| `GEN_reygrupero_intermedio` | 242 | 2,64 | 1 | <0,01 |
| `GEN_robot_intermedio` | 242 | 3,20 | 1 | <0,01 |
| `GEN_senortienda_intermedio` | 178 | 3,07 | 21 | 0,19 |

Hay **432 archivos / ~14,49 MiB** cuyo contenido aparece en el canónico, pero ninguna
de esas carpetas es completamente redundante. Borrarlas enteras perdería variantes.
Además, tener el mismo hash no demuestra que una ruta se pueda quitar: los scripts
históricos pueden necesitar su nombre y ubicación. Una segunda fase necesitaría
un manifiesto archivo → sustituto, ajuste de referencias y prueba de reconstrucción.

Los 11 PNG sueltos de la raíz **no tienen copia byte-idéntica versionada**, comprobado
por tamaño y SHA-256. No se consideran duplicados ni se propone borrarlos.

`additional_assets` (107,5 MiB), `nuevoMaterial17JUL` (245,8 MiB) y `nuevoMaterial18JUL`
(105,5 MiB) contienen fuentes; su tamaño no demuestra desuso. Revisar procedencia y
consumidores antes de plantear un traslado o borrado. Las fuentes nuevas de Alexis
también deben conservarse para que el CI reconstruya su atlas.

## Documentación

`README for IAS/_SESION_ACTUAL.md` conserva sesiones antiguas y supera el techo que
indica su cabecera. Se conserva el contenido previo completo en
`_ARCHIVO/SESION_previa_2026-11-04.md` y el archivo activo se sustituye por el estado vigente.
No se deben quitar tombstones, gotchas ni contratos para reducir líneas.

Se creó un tutorial único de CI de peleadores y se enlazó desde los índices para
evitar añadir otra receta aislada. `PR_DESCRIPTION.md`, que ya estaba sin seguimiento
al iniciar, se deja intacto.
