# QA de La Planchada — PR #143

Actualización: 2026-11-06. Sustituye el material de prueba del personaje anterior.
Las capturas y los resultados de septiembre no constituyen evidencia de esta versión.

## Contrato e integración

- Identidad visual: La Planchada es una enfermera espectral del IMSS, con uniforme blanco, detalles verdes y apariencia fantasmal coherente en todas las poses.
- Peleador `LA_PLANCHADA`, atlas único `STREETFIGHTER/IMAGES/LaPlanchada.webp` y datos `STREETFIGHTER/DATA/laplanchada.json`.
- Cada celda SF es RGBA 256×256, centro x=128 y pies y=224; cuerpo erguido de 100 px. Agachados, poses aéreas y KO conservan su escala anatómica. Los proyectiles tienen origen `[128,128]`.
- Mundo libre: `PlayerSkin.LA_PLANCHADA`, 25 recortes derivados del mismo arte bajo `SPRITES/NPC/LaPlanchada/`, prefijo `lpl_`: 6 Idle, 6 Walk, 8 Run y 5 Special. Lienzos 512×512, cuerpo erguido de 360 px y pies y=456.
- Arcade: rival del grupo de leyendas, escalones 11–13 en orden aleatorio, antes de Tzitzimime, Presidenta y Yoalli. La campaña tiene 16 combates. La Planchada no pertenece a los peleadores iniciales: la victoria en Medio o Difícil aplica el desbloqueo existente.
- Escenario propio: Escuela Superior de Medicina IPN, `fondo_esm_anim.webp` y sus variantes `_noche_1_anim.webp` y `_noche_2_anim.webp`.
- Audio: comparte el pack de La Llorona (`special_llorona_attack`, `hurt`, `power`, `win`) y su archivo de poder como fallback. No se copian archivos de audio.
- La selección guardada inválida mantiene el fallback a Lázaro; Alexis Syntaxis permanece disponible.

## Verificación automatizada

Desde `PolitecnicoOpenWorld/`:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :shared:testAndroidHostTest
```

Las pruebas de integración cubren registro, rutas, densidad de mundo libre, reutilización del pack y fallback de audio, aparición de La Planchada antes de los tres jefes, mapas ESM según dificultad y ausencia de desbloqueo gratuito. La auditoría de campaña verifica que existen atlas, datos, escenarios y audios para todos los rivales. El gate `SF Fighter Assets` reconstruye el atlas con la receta versionada y comprueba geometría y reproducibilidad.

Los resultados ejecutados deben registrarse con su comando y SHA en el PR. Este plan no afirma que se haya ejecutado QA en dispositivo.

## Recorrido manual pendiente

| Caso | Pasos | Resultado esperado |
|---|---|---|
| Mundo libre | Elegir La Planchada; alternar idle, caminar, correr y especial; cambiar de dirección e ingresar a un interior. | Identidad coherente, pies alineados, cuerpo completo, tamaño estable y recortes sin halo. |
| Selector y persistencia | Elegir la skin, reiniciar la app y volver al mapa. | Se restaura La Planchada; una selección inválida vuelve a Lázaro. |
| Progresión | Comenzar una campaña nueva y alcanzar el grupo de leyendas. | La Planchada aparece una vez antes de los tres jefes; no está desbloqueada inicialmente. |
| Desbloqueo | Vencerla en Medio o Difícil. | Se desbloquean el peleador y la iluminación correspondiente de ESM. En Difícil también se aplica el coleccionable existente. |
| Combate completo | Probar golpes, agachado, daño, saltos, ataques aéreos, bloqueo, parry, agarre, derribo, recuperación, súper y KO a ambos lados. | Poses completas y específicas, sin crecimiento corporal ni cuadros de otro personaje. |
| Audio | Conectar ataques, recibir daño, usar poder y ganar. | Suenan los cuatro audios reutilizados de La Llorona; el poder evita el hadouken genérico. |
| ESM | Abrir día, noche y apocalipsis; combatir con ambos personajes. | Fondo a escala del resto de escenarios, suelo continuo y sprites legibles. |
| Regresión | Elegir estudiantes y Alexis; continuar una campaña antigua guardada. | El roster anterior y el recorrido guardado siguen disponibles. |
