# PROMPTS para nuevas sesiones de IA (actualizado 2026-07-12)

> Histórico: la sección BT y el multijugador online YA están implementados y probados.
> Último trabajo de assets: escala POR ANIMACIÓN en los peleadores compartidos (los sets
> PLAYER traen lienzos distintos por acción; se normaliza cada animación a la misma altura).
> Los COMPARTIDOS usan `sf_template.json` + rejilla runtime (`SfSharedSheets`) porque
> ryu.json/ken.json quedaron SOLO en el source set debug (copyright).

## A. PROMPT DEL PROYECTO (instrucciones permanentes — sin cambios)

Estás ayudándome con "Politécnico Open World" (POW), un juego Android 2D top-down
sobre mapas reales (Kotlin + Jetpack Compose + MVVM estricto por feature).

RUTAS EN ESTA PC:
- Repo raíz: C:\Users\gabri\AndroidStudioProjects\PolitecnicoOpenWorld
- Proyecto Android: ...\PolitecnicoOpenWorld\PolitecnicoOpenWorld
- Contexto para IAs: ...\PolitecnicoOpenWorld\PolitecnicoOpenWorld\README for IAS

CONTEXTO: "README for IAS" (00–09 + docs de trabajo) reemplaza al código; léela antes de
proponer cambios. Modo pelea "HUELUM VS. GOYA": ver 07 + AUDIT_SF_MULTIPLAYER.md. Si
necesitas un .kt concreto, pídemelo; no inventes contenido.

REGLAS: MVVM y convenciones/gotchas del 09 (incluido protocolo de docs). Estado inmutable
(_state.update { it.copy(...) }); Views solo collectAsState() + intenciones. Comentarios y
strings en español; strings de UI en res/values(-en) con PARIDAD ES+EN. Gotcha
miembro-vs-extensión: gana el MIEMBRO. CRLF en .kt existentes; balance de llaves.
Servidores Node: node --check; Render FREE (warmup GET /status). No puedes compilar:
entrega listo para Rebuild Project. Al terminar: protocolo de docs del 09 (00–09 +
README público bilingüe).

## B. SIGUIENTE TAREA: cablear los cómics IntroPOW16–30 (arte en camino vía ChatGPT)

Estado: el arte de los 15 paneles (IntroPOW16.webp … IntroPOW30.webp, 1920×1080, franja
blanca inferior para el diálogo que el código dibuja a ~79% de la altura) se está generando
aparte y se irá dejando en `app/src/main/assets/STORY/INTRO/`. Los diálogos y escenas por
panel están en la tabla de abajo (§C). Cuando el arte exista:

1. `StoryComicCatalog`: registrar las secuencias nuevas por `sequenceId`:
   - M2_BACKPACK_INTRO = IntroPOW16..18 (antes de la fase MOCHILA de la Misión 2).
   - M3_INTRO = IntroPOW19..22 (al SEGUIR la Misión 3) y M3_OUTRO = IntroPOW23..24
     (tras recoger la evidencia, gancho a M4).
   - M4_INTRO = IntroPOW25..28 y M4_OUTRO = IntroPOW29..30 (la M4 "Código Rojo en
     Zacatenco" AÚN NO está cableada como misión; dejar las secuencias listas).
2. Disparadores: reusar el patrón de `mission1ChaseIntro` (cómic vía `StoryIntroScreen`
   por sequenceId; ver 07 y CAMPAIGN/02–03) en las fases correspondientes de M2/M3.
3. Textos: los diálogos van como strings ES+EN (el panel lleva la franja blanca vacía).
4. Protocolo de docs: CAMPAIGN/02_MISSION_2.md, 03_MISSION_3.md (secciones de cómics),
   crear CAMPAIGN/05_MISSION_4.md borrador con la tabla de §C, 07, README público.

## C. TABLA DE PANELES (diálogo + escena) — fuente para arte y para el cableado

| Archivo | Diálogo (ES) | Escena |
|---|---|---|
| IntroPOW16 | "Toma, es una lata de surströmming. Apesta a muerto, wey." | Prankedy extiende una lata de pescado fermentado al jugador |
| IntroPOW17 | "El salón está a reventar de alumnos. Con esto sale hasta el maestro." | Puerta/vista de un salón lleno |
| IntroPOW18 | "A la cuenta de tres la avientas y agarras la mochila. ¡Órale!" | Lata destapada + vapor, alumnos huyendo |
| IntroPOW19 | "La broma se salió de control… y todo apunta a la ENCB." | Noticiero/titular del brote |
| IntroPOW20 | "La tienen acordonada con granaderos. Hay que colarse sin que nos vean." | ENCB con vallas y granaderos, paparazzi |
| IntroPOW21 | "Yo te acompaño… pero esos infectados me dan mala espina." | Prankedy nervioso junto al jugador |
| IntroPOW22 | "Si nos alcanza uno… ni modo, corremos. Vamos por esa evidencia." | Los dos frente a la entrada, decididos |
| IntroPOW23 | "Esto lo prueba todo." | Primer plano de la evidencia (frasco verde) |
| IntroPOW24 | "¿Y ahora a QUIÉN se lo llevamos?" | Los dos mirándose, dudando |
| IntroPOW25 | "Trai un frasco con moco verde y dice que hay zombis… Ajá. Siguiente." | Funcionario ignorándolos |
| IntroPOW26 | "…múltiples heridos por MORDEDURA en Av. IPN. Solicito apoyo, ¡el que sea!" | Radio de ambulancia / paramédica |
| IntroPOW27 | "¡¡CORRAN!!" | Primera horda irrumpe en Av. IPN |
| IntroPOW28 | "¿Traes pruebas y un fierro? Perfecto. Ayúdame a evacuar y te consigo a alguien que SÍ te escuche." | La paramédica jefa los recluta |
| IntroPOW29 | "Salimos todos. Buen trabajo, wey." | Evacuados a salvo, alivio |
| IntroPOW30 | "Doctora… este viene MORDIDO." | Un evacuado mordido (gancho M5) |
