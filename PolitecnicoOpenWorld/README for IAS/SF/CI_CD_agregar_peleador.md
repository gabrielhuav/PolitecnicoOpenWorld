# Agregar un peleador con recortes y GitHub Actions

Actualizado: 2026-11-04. Ejecutar los comandos desde la **raíz del repositorio**.

El flujo es: arte original → receta versionada → recortes en staging → atlas + JSON →
QA automático y visual → integración → PR → release Android existente.
`SF Fighter Assets / fighter-assets` construye previews y valida en cada PR hacia `main`.
No escribe en la rama ni publica una app. El despliegue sigue en `android-release.yml`.

## 1. Preparar el arte y el registro

Lee `00_SF_INDEX.md`, `PIPELINE_agregar_movimientos_y_personajes.md` y
`GUIA_regeneracion_sprites_croma.md`. Guarda las hojas en `newSFAssets/<Nombre>/`.
El contrato es RGBA 256×256, pies en y=224, centro x=128, altura erguida 100 px;
proyectiles/efectos independientes tienen origen `[128,128]`.
Las poses agachadas, saltos y efectos conservan su propia altura.

Un dedicado necesita las hojas 01–30 y cualquier movimiento posterior que use el motor.
La 01 calibra escala; 14/15 se procesan después de 07–09 porque reemplazan poses refinadas.
No vuelvas a procesar hojas correctas de otros peleadores.

Registra el personaje en `SfModels.kt`, al final del enum cuando corresponda, con las
rutas exactas del JSON y WebP. Sigue el patrón del roster actual. Revisa también
`SfArcadeLadder.kt`, `SfStageCatalog.kt`, `SfArcadeRepository.kt`, voces, desbloqueos y
el audit `SfArcadeCampaignAuditTest.kt`; no agregues privilegios gratuitos para pasar el test.
El material de PR #158 ya contiene esas entradas para `ALEXIS_SYNTAXIS`.

## 2. Escribir una receta

Crea `PolitecnicoOpenWorld/tools/sf_fighters/<id>.json` con este formato:

```json
{
  "character": "mi_peleador",
  "title": "MiPeleador",
  "mode": "chroma",
  "sheets": [
    "newSFAssets/MiPeleador/MiPeleador_01_Idle_Turn.png",
    "newSFAssets/MiPeleador/MiPeleador_02_Walk.png"
  ]
}
```

La lista anterior solo ilustra la sintaxis: **incluye todas las hojas reales en orden**.
Los nombres deben permitir extraer el número de hoja; verifica el contrato `SHEETS` del slicer.
`build` ejecuta primero `--list`: cualquier `AVISO` de conteo falla antes de recortar esa hoja.
Después usa el slicer y packer existentes y normaliza el atlas final. Los intermedios se
quedan en `<output>/GEN`, fuera de `app/src`. No se borra la caché histórica.

Para rescatar un PR que solo trae un atlas, usa `mode: "atlas-repair"`, como
`sf_fighters/alexis_syntaxis.json`: conserva el atlas/JSON importados, la hoja nueva,
su rejilla y el mapa explícito celda → clave. **No permite inventar píxeles perdidos con
un escalado:** las poses incompletas se regeneran y se revisan visualmente.

## 3. Construir localmente sin tocar producción

```powershell
python -m pip install -r PolitecnicoOpenWorld/tools/requirements-sf.txt
python -m unittest discover -s PolitecnicoOpenWorld/tools/tests -p test_sf_fighter_pipeline.py
python PolitecnicoOpenWorld/tools/sf_fighter_pipeline.py build PolitecnicoOpenWorld/tools/sf_fighters/alexis_syntaxis.json --output .codex-tmp/sf-preview --report .codex-tmp/sf-qa
```

`build` devuelve código distinto de cero si falla el contrato. La salida contiene
`IMAGES/<Titulo>.webp`, `DATA/<id>.json`, una hoja de contacto y un informe JSON.
Revisa **cada pose**: identidad, cuerpo completo, dirección, props, poderes completos y
ausencia de rival dibujado. Las métricas no evalúan la calidad artística ni garantizan
una animación fluida; compara las poses consecutivas y pruébalas dentro del juego.

Solo después de la revisión, copia los dos archivos finales a
`PolitecnicoOpenWorld/app/src/main/assets/STREETFIGHTER/{IMAGES,DATA}/`.
Para Alexis:

```powershell
Copy-Item .codex-tmp/sf-preview/IMAGES/AlexisSyntaxis.webp PolitecnicoOpenWorld/app/src/main/assets/STREETFIGHTER/IMAGES/AlexisSyntaxis.webp
Copy-Item .codex-tmp/sf-preview/DATA/alexis_syntaxis.json PolitecnicoOpenWorld/app/src/main/assets/STREETFIGHTER/DATA/alexis_syntaxis.json
python PolitecnicoOpenWorld/tools/sf_fighter_pipeline.py check PolitecnicoOpenWorld/tools/sf_fighters/alexis_syntaxis.json
```

## 4. Usar GitHub Actions

Incluye receta, hojas fuente, herramientas y los dos assets finales en el mismo PR.
El workflow `.github/workflows/sf-fighter-assets.yml`:

1. Prueba el validador con casos de regresión.
2. Exige receta para JSON de peleador o atlas dedicado modificado en el PR.
3. Construye todas las recetas registradas en staging. Por ahora la registrada es Alexis;
   los peleadores históricos no se reprocesan salvo que se registren explícitamente.
4. Valida dimensiones, anclajes, altura erguida, transparencia, bordes, bustos y referencias.
5. Compara JSON y píxeles reconstruidos contra los archivos versionados.
6. Sube `sf-fighter-preview`, con QA y atlas, incluso si falla un paso posterior.

Abre **Actions → SF Fighter Assets → ejecución → Artifacts → sf-fighter-preview**.
Tras integrar el workflow en la rama por defecto, también se puede ejecutar con
**Run workflow**, eligiendo una rama. GitHub exige que `workflow_dispatch` exista en
la rama por defecto para ofrecer ese botón. No hacen falta secrets ni una API de imágenes:
Actions recorta arte ya versionado; la generación de imágenes ocurre antes.

Configura en Settings → Rules → Rulesets (o protección de `main`) el check
`fighter-assets` como requerido, junto con los gates de Kotlin/detekt. **Agregar el YAML
no configura por sí solo la protección del merge.** No se cambiaron ajustes remotos.

Referencia oficial: [sintaxis y eventos de workflows](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax).

## 5. CI del juego y publicación

Desde `PolitecnicoOpenWorld/`:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```

Prueba en Android e iOS: selector, idle/caminar, golpes, agachado, saltos, especiales de
tres fuerzas, súper, agarres, KO, hablar y ambos lados del combate. El tamaño decodificado
del atlas también importa: WebP pequeño en disco no reduce los píxeles en RAM.

Con el PR revisado y mergeado, el flujo Android existente arma y publica según sus
condiciones y secrets. Este trabajo no ejecuta un release, no firma y no mergea el PR.

## Caso Alexis / PR #158

El original tiene 258 cuadros y 58 animaciones. Se midieron 564 incumplimientos de
geometría; los erguidos iban de 89 a 132 px y los pies normalmente terminaban entre
y=225 y y=227. Diez cuadros tocaban un borde y cuatro `talk-*` eran bustos.

El dueño confirmó que no dispone de hojas completas y autorizó regenerar lo necesario.
Se generaron esas 14 poses con `imagegen` integrado, usando el personaje del PR como
referencia, y se mantuvo el resto del arte. Fuente, prompt y mapa reproducible están en
`newSFAssets/AlexisSyntaxis/` y la receta. La misma cantidad de cuadros/animaciones y
los tiempos/cajas de combate permanecen; cambian píxeles y el origen de `talk-*`,
que pasa de busto centrado `[128,128]` a cuerpo con pies `[128,224]`.
El nuevo informe queda sin errores geométricos; la prueba en dispositivo sigue siendo
necesaria para aprobar continuidad y jugabilidad.
