# Caches GEN: variantes y referencias compartidas

El GEN canónico sigue siendo `GEN_prankedy_senortienda_rey_paparazzi_fullcombat_intermedio/`.
Las siete caches antiguas conservan **1.836 archivos de variantes**. Se retiraron
432 copias idénticas (15.192.173 bytes / 14,49 MiB) y su relación completa queda en
`gen_duplicates.json`: ruta antigua, destino canónico, SHA-256 y tamaño original.
No se cambiaron los archivos canónicos ni los atlas de producción.

El packer reconstruye una vista temporal de la cache antigua al recibir `--gen`.
El slicer y los importadores leen la calibración referenciada cuando falta la copia
antigua. Una variante física local tiene prioridad sobre la referencia. Las escrituras
siguen en la cache indicada, sin sobrescribir el destino canónico.

Desde la raíz del repositorio:

```powershell
python PolitecnicoOpenWorld/tools/sf_gen_cache.py check
python PolitecnicoOpenWorld/tools/sf_gen_cache.py materialize newSFAssets/GEN_lallorona_intermedio .codex-tmp/llorona-historica
```

`materialize` exporta todos los bytes de esa variante a una carpeta **nueva** para
herramientas externas que necesitan archivos físicos o hacen su propio glob/listdir.
No agrega poses actuales a una cache histórica ni intenta completar animaciones que
nunca tuvo. No usa enlaces simbólicos y funciona en Windows sin privilegios adicionales.
Las carpetas históricas incompletas siguen siendo incompletas: para el roster actual,
usar el GEN canónico indicado en la documentación SF.

CI verifica los hashes de cada destino y rechaza copias idénticas reintroducidas.
Los metadatos JSON incluyen además tamaño y hash de su forma LF en Git, para que
la conversión CRLF/LF del checkout de Windows/Linux no se confunda con otro contenido.
Las imágenes se comprueban por sus bytes exactos, sin normalización.
Si se modifica un destino canónico referenciado, hay que conservar primero sus bytes
anteriores para las variantes históricas y actualizar el manifiesto; cambiar el hash
sin conservar esa variante alteraría el historial.
