package ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel

import androidx.annotation.StringRes
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoom
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

// NPCs AMBIENTALES de interiores (Modo Historia): estudiantes/docentes que deambulan por las
// salas de la ESCOM como escenografia viva. Son LOCALES (offline): se simulan en el cliente, no
// vienen del servidor (eso es `interiorNpcs`). Cada uno tiene su SKIN (sprite completo) y un
// objetivo de caminata; al llegar (o por timeout) elige otro. Sin combate ni colision con el jugador.
//
// 🆕 VIDA UNIVERSITARIA (máquina de MODOS por NPC, campos en AmbientNpc):
//   WANDER        → deambula solo (comportamiento clásico).
//   MEETING       → dos NPCs quedaron de verse: caminan a un PUNTO COMÚN; al juntarse → TALK.
//   TALK          → parados FRENTE A FRENTE platicando: burbujas de diálogo ALTERNADAS con frases
//                   de strings.xml (`amb_phrase_*`, traducibles ES/EN). Al terminar, 50% se van
//                   CAMINANDO JUNTOS y 50% se despiden ("Ahí nos vemos") y se separan.
//   WALK_TOGETHER → caminan JUNTOS (mismo rumbo con offset) hasta llegar → despedida y se separan.
//
// 🆕 ANTI-ATASCO: si un NPC no se mueve > STUCK_EPS durante STUCK_MS (chocó con pared/esquina),
//   CAMBIA DE DIRECCIÓN (nuevo objetivo aleatorio) y el detector se RE-ARMA: si se movió y se
//   vuelve a atorar, se corrige otra vez (el checkpoint stuckX/Y/SinceMs se resetea al moverse).
//   Si estaba emparejado (MEETING/WALK_TOGETHER), el atasco CANCELA la pareja (evita livelocks).
data class AmbientNpc(
    val id: String,
    val x: Float,
    val y: Float,
    val skin: PlayerSkin,
    val facingRight: Boolean = true,
    val action: PlayerAction = PlayerAction.WALK,
    val targetX: Float = x,
    val targetY: Float = y,
    val retargetAtMs: Long = 0L,
    // ── Vida universitaria ──
    val mode: AmbientMode = AmbientMode.WANDER,
    val partnerId: String? = null,       // con quién quedó de verse / platica / camina
    val modeUntilMs: Long = 0L,          // fin de la plática o tope del modo actual
    // Semilla COMPARTIDA de la pareja (la fija el emparejador, MISMO valor para ambos). Todas las
    // decisiones de pareja (GUION de la plática, ¿caminar juntos?, destino común) se derivan de
    // aquí de forma DETERMINISTA → cada NPC se configura A SÍ MISMO sin escribirle al otro (sin
    // carreras por orden de procesamiento en el tick).
    val pairSeed: Long = 0L,
    // 🆕 Inicio de la PLÁTICA actual (ms): con pairSeed elige el GUION (AMBIENT_CONVOS) y con
    // (now - talkStartMs) la LÍNEA que toca — conversaciones COHERENTES, no frases sueltas.
    val talkStartMs: Long = 0L,
    @StringRes val speechRes: Int? = null,   // burbuja de diálogo visible (null = sin burbuja)
    val speechUntilMs: Long = 0L,
    // ── Anti-atasco (checkpoint de movimiento) ──
    val stuckX: Float = x,
    val stuckY: Float = y,
    val stuckSinceMs: Long = 0L
)

enum class AmbientMode { WANDER, MEETING, TALK, WALK_TOGETHER }

// Salas donde aparecen NPCs ambientales: el lobby de la ESCOM y el SALÓN de la Misión 2 (los
// estudiantes "en clase" que salen con la lata apestosa). Ampliable: agrega ids de salas.
private val AMBIENT_ROOM_IDS: Set<String> =
    setOf(ZombieRoomCatalog.LOBBY_ID, ZombieRoomCatalog.ESCOM_SALON_M2_ID)

// Skins usadas como MODELO de NPC. Los exclusivos de ESCOM (IPN_1..6) solo salen dentro de la ESCOM.
private fun getAmbientSkinsPool(room: ZombieRoom): List<PlayerSkin> {
    val isEscom = room.id == ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog.LOBBY_ID ||
                  room.id.startsWith("za_") ||
                  room.id == ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog.ESCOM_SALON_M2_ID

    val globalAndGenericSkins = listOf(
        PlayerSkin.RND_1, PlayerSkin.DOC_1, PlayerSkin.EST_H1, PlayerSkin.EST_M1,
        PlayerSkin.escomboy, PlayerSkin.escomgirl
    )

    return if (isEscom) {
        val escomExclusive = listOf(
            PlayerSkin.IPN_1, PlayerSkin.IPN_2, PlayerSkin.IPN_3,
            PlayerSkin.IPN_4, PlayerSkin.IPN_5, PlayerSkin.IPN_6
        )
        escomExclusive + globalAndGenericSkins
    } else {
        globalAndGenericSkins
    }
}

// 🆕 GUIONES de plática (vida universitaria): cada guion es una CONVERSACIÓN COHERENTE de líneas
// ALTERNADAS (línea 0, 2, 4… las dice el NPC de id MENOR; 1, 3, 5… el otro). La pareja elige su
// guion DETERMINISTA desde pairSeed y avanza línea por línea con (now - talkStartMs) — ya no son
// frases sueltas al azar sin hilo. @StringRes → traducibles (ES+EN, paridad obligatoria).
private val AMBIENT_CONVOS: List<List<Int>> = listOf(
    listOf(R.string.amb_convo1_1, R.string.amb_convo1_2, R.string.amb_convo1_3, R.string.amb_convo1_4),
    listOf(R.string.amb_convo2_1, R.string.amb_convo2_2, R.string.amb_convo2_3, R.string.amb_convo2_4),
    listOf(R.string.amb_convo3_1, R.string.amb_convo3_2, R.string.amb_convo3_3, R.string.amb_convo3_4),
    listOf(R.string.amb_convo4_1, R.string.amb_convo4_2, R.string.amb_convo4_3, R.string.amb_convo4_4),
    listOf(R.string.amb_convo5_1, R.string.amb_convo5_2, R.string.amb_convo5_3, R.string.amb_convo5_4),
    listOf(R.string.amb_convo6_1, R.string.amb_convo6_2, R.string.amb_convo6_3, R.string.amb_convo6_4),
    // Guiones cortos (2 líneas) armados con las frases clásicas que SÍ hilan pregunta→respuesta.
    listOf(R.string.amb_phrase_1, R.string.amb_phrase_7),
    listOf(R.string.amb_phrase_5, R.string.amb_phrase_10),
    listOf(R.string.amb_phrase_8, R.string.amb_phrase_4)
)
// Despedida al separarse (como el "Ahí nos vemos" de Prankedy al final de la Misión 1).
private val AMBIENT_BYE: Int = R.string.amb_phrase_bye

// ── MISIÓN 2 · FASE 1 (lobby): policías de búsqueda como NPCs ambientales ──
// Viven DENTRO de ambientNpcs (mismo render/burbuja) con este prefijo; el emparejador los IGNORA
// (un policía no se pone a platicar con un alumno) y su paso es stepMission2HideCops, no el normal.
internal const val M2COP_PREFIX = "m2cop_"
private val M2COP_SEARCH_PHRASES: List<Int> =
    listOf(R.string.amb_cop_search_1, R.string.amb_cop_search_2)

// NPCs por sala: el LOBBY es el corazón de la vida universitaria (más denso); el salón M2 es una
// clase (menos). Otras salas ambientales futuras caen al default.
private fun ambientCountFor(room: ZombieRoom): Int = when (room.id) {
    ZombieRoomCatalog.LOBBY_ID -> 25
    ZombieRoomCatalog.ESCOM_SALON_M2_ID -> 8
    else -> 7
}
private const val AMBIENT_SPEED = 3.0f          // px por tick (caminar tranquilo)
private const val AMBIENT_RADIUS = 28f          // margen a paredes/bordes
private const val AMBIENT_ARRIVE = 18f          // distancia para considerar "llego" al objetivo
private const val AMBIENT_WAIT_MS = 600L       // pausa (idle) al llegar antes de re-elegir

// ── Anti-atasco ──
private const val STUCK_EPS = 6f                // si se movió menos que esto…
private const val STUCK_MS = 1600L              // …durante este tiempo, está ATORADO → cambia dirección

// ── Vida universitaria (encuentros/pláticas) ──
private const val PAIR_CHANCE_PER_TICK = 0.009f // ~1 encuentro cada ~3.7 s de tick (30 Hz) si hay libres
private const val TALK_START_DIST = 78f         // al quedar así de cerca del punto común → platican
private const val TALK_GAP = 26f                // separación al pararse frente a frente
private const val TALK_MIN_MS = 8000L
private const val TALK_MAX_MS = 14000L
private const val SPEECH_TURN_MS = 2900L        // cada cuánto ALTERNA el que habla
private const val SPEECH_SHOW_MS = 2500L        // cuánto dura visible cada burbuja
private const val BYE_SHOW_MS = 2200L
private const val WALK_TOGETHER_OFFSET = 22f    // caminan lado a lado (offset del objetivo común)
private const val WALK_TOGETHER_CAP_MS = 20000L // tope del paseo juntos (por si el objetivo es lejano)

/** Es caminable el pixel (x,y) en la sala (dentro de bordes y fuera de la matriz de colision)? */
private fun walkable(room: ZombieRoom, x: Float, y: Float): Boolean {
    if (x < AMBIENT_RADIUS || y < AMBIENT_RADIUS ||
        x > room.worldWidth - AMBIENT_RADIUS || y > room.worldHeight - AMBIENT_RADIUS) return false
    return !room.isBlockedPixel(x, y)
}

/** Punto aleatorio caminable de la sala (o null si no encuentra en N intentos). */
private fun randomWalkable(room: ZombieRoom): Pair<Float, Float>? = randomWalkable(room, Random)

/** Variante SEMBRADA: con la misma semilla devuelve el MISMO punto (decisiones de pareja). */
private fun randomWalkable(room: ZombieRoom, rnd: Random): Pair<Float, Float>? {
    repeat(40) {
        val x = AMBIENT_RADIUS + rnd.nextFloat() * (room.worldWidth - 2f * AMBIENT_RADIUS)
        val y = AMBIENT_RADIUS + rnd.nextFloat() * (room.worldHeight - 2f * AMBIENT_RADIUS)
        if (walkable(room, x, y)) return x to y
    }
    return null
}

/** Crea los NPCs ambientales de la sala (vacio si no aplica o si es multijugador). */
internal fun ZombieInteriorViewModel.spawnAmbientNpcs(room: ZombieRoom): List<AmbientNpc> {
    val pool = getAmbientSkinsPool(room)
    if (isMultiplayer || room.id !in AMBIENT_ROOM_IDS || pool.isEmpty()) return emptyList()
    val count = ambientCountFor(room)
    val now = System.currentTimeMillis()
    val list = ArrayList<AmbientNpc>(count)
    repeat(count) { i ->
        val p = randomWalkable(room) ?: return@repeat
        val t = randomWalkable(room) ?: p
        list.add(
            AmbientNpc(
                id = "amb_$i",
                x = p.first, y = p.second,
                skin = pool[Random.nextInt(pool.size)],
                facingRight = Random.nextBoolean(),
                action = PlayerAction.WALK,
                targetX = t.first, targetY = t.second,
                retargetAtMs = 0L
            )
        )
    }
    // 🆕 GRUPOS INICIALES (solo el lobby): 2 parejas nacen YA platicando frente a frente para que
    // la sala se vea viva desde el primer segundo (no hay que esperar al emparejador aleatorio).
    if (room.id == ZombieRoomCatalog.LOBBY_ID && list.size >= 4) {
        for (g in 0 until 2) {
            val ia = g * 2       // amb_0+amb_1 y amb_2+amb_3
            val ib = g * 2 + 1
            val spot = randomWalkable(room) ?: continue
            val seed = now + g   // semilla COMPARTIDA de la pareja (guion determinista)
            val talkMs = TALK_MIN_MS + (Random(seed).nextLong(TALK_MAX_MS - TALK_MIN_MS))
            list[ia] = list[ia].copy(
                x = spot.first - TALK_GAP / 2f, y = spot.second,
                mode = AmbientMode.TALK, partnerId = list[ib].id, pairSeed = seed,
                talkStartMs = now, modeUntilMs = now + talkMs,
                action = PlayerAction.IDLE, facingRight = true
            )
            list[ib] = list[ib].copy(
                x = spot.first + TALK_GAP / 2f, y = spot.second,
                mode = AmbientMode.TALK, partnerId = list[ia].id, pairSeed = seed,
                talkStartMs = now, modeUntilMs = now + talkMs,
                action = PlayerAction.IDLE, facingRight = false
            )
        }
    }
    return list
}

// ── MISIÓN 2 · FASE 1 · POLICÍAS DEL LOBBY ───────────────────────────────────

/** Crea los policías de búsqueda de la fase ESCONDERSE: entran por la puerta del lobby. */
internal fun ZombieInteriorViewModel.spawnMission2HideCops(room: ZombieRoom): List<AmbientNpc> {
    // Buscar la puerta de entrada principal (salida al mapa) para spawnearlos ahí.
    val door = room.doors.firstOrNull { it.targetRoomId == ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog.EXIT_TO_WORLD }
        ?: room.doors.firstOrNull()
    val dx = door?.let { (it.hitboxFrac.left + it.hitboxFrac.right) * 0.5f * room.worldWidth }
        ?: (room.worldWidth * 0.5f)
    val dy = door?.let { (it.hitboxFrac.top + it.hitboxFrac.bottom) * 0.5f * room.worldHeight }
        ?: (room.worldHeight * 0.90f)
    val m2 = ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2
    return (0 until m2.HIDE_COP_COUNT).map { i ->
        // Aparecen ESCALONADOS junto a la puerta y se reparten a patrullar la sala.
        val sx = (dx + (i - 1.5f) * 34f).coerceIn(AMBIENT_RADIUS, room.worldWidth - AMBIENT_RADIUS)
        val t = randomWalkable(room) ?: (sx to dy)
        
        // Buscar un pixel caminable cercano a (sx, dy) para evitar que nazcan dentro de la colisión de la puerta/pared.
        var spawnX = sx
        var spawnY = dy
        if (!walkable(room, spawnX, spawnY)) {
            var found = false
            for (offsetY in listOf(0f, 20f, -20f, 40f, -40f)) {
                for (offsetX in listOf(0f, 20f, -20f, 40f, -40f)) {
                    val nx = (sx + offsetX).coerceIn(AMBIENT_RADIUS, room.worldWidth - AMBIENT_RADIUS)
                    val ny = (dy + offsetY).coerceIn(AMBIENT_RADIUS, room.worldHeight - AMBIENT_RADIUS)
                    if (walkable(room, nx, ny)) {
                        spawnX = nx
                        spawnY = ny
                        found = true
                        break
                    }
                }
                if (found) break
            }
            if (!found) {
                spawnX = t.first
                spawnY = t.second
            }
        }

        AmbientNpc(
            id = "$M2COP_PREFIX$i",
            x = spawnX, y = spawnY,
            skin = PlayerSkin.POLICIA_CDMX,
            action = PlayerAction.WALK,
            targetX = t.first, targetY = t.second
        )
    }
}

/**
 * Paso de los policías de búsqueda: patrullan la sala (objetivos aleatorios caminables) y, cada
 * HIDE_SWEEP_EVERY_MS, UNO (rotando) barre HACIA la posición actual del jugador — la búsqueda se
 * siente dirigida sin ser injusta. Si te tienen a < HIDE_DETECT_PX te "ven" (burbuja de alerta);
 * la CUENTA del reconocimiento (fallo) la lleva el tick del VM. Sin pláticas ni parejas.
 */
internal fun ZombieInteriorViewModel.stepMission2HideCops(
    cops: List<AmbientNpc>, room: ZombieRoom, playerX: Float, playerY: Float, now: Long
): List<AmbientNpc> {
    if (cops.isEmpty()) return cops
    val m2 = ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2
    // ¿A quién le toca el BARRIDO hacia el jugador en esta ventana? (rota de forma determinista)
    val sweeperIdx = ((now / m2.HIDE_SWEEP_EVERY_MS) % cops.size).toInt()
    return cops.mapIndexed { idx, cop0 ->
        var cop = cop0
        val dPlayer = hypot(cop.x - playerX, cop.y - playerY)
        // Burbuja: alerta si te está viendo; si no, de vez en cuando una frase de búsqueda.
        cop = when {
            dPlayer < m2.HIDE_DETECT_PX ->
                cop.copy(speechRes = R.string.amb_cop_alert, speechUntilMs = now + 900L)
            cop.speechRes != null && now >= cop.speechUntilMs -> cop.copy(speechRes = null)
            cop.speechRes == null && Random.nextFloat() < 0.002f ->
                cop.copy(
                    speechRes = M2COP_SEARCH_PHRASES[Random.nextInt(M2COP_SEARCH_PHRASES.size)],
                    speechUntilMs = now + SPEECH_SHOW_MS
                )
            else -> cop
        }
        // Objetivo: el barredor de turno va hacia TI; el resto patrulla puntos aleatorios.
        val arrived = hypot(cop.targetX - cop.x, cop.targetY - cop.y) <= AMBIENT_ARRIVE
        if (idx == sweeperIdx) {
            cop = cop.copy(targetX = playerX, targetY = playerY)
        } else if (arrived || cop.retargetAtMs <= now) {
            val t = randomWalkable(room)
            if (t != null) cop = cop.copy(targetX = t.first, targetY = t.second,
                retargetAtMs = now + 4000L + Random.nextLong(4000L))
        }
        val (rx, ry) = stepTowards(room, cop.x, cop.y, cop.targetX, cop.targetY, m2.HIDE_COP_SPEED_PX)
        cop.copy(
            x = rx, y = ry,
            facingRight = if (abs(cop.targetX - cop.x) > 0.5f) cop.targetX >= cop.x else cop.facingRight,
            action = PlayerAction.WALK
        )
    }
}

// ── Helpers de la vida universitaria ─────────────────────────────────────────

// Rompe la pareja de un NPC (vuelve a WANDER con la despedida opcional).
private fun AmbientNpc.unpair(room: ZombieRoom, now: Long, sayBye: Boolean): AmbientNpc {
    val t = randomWalkable(room) ?: (x to y)
    return copy(
        mode = AmbientMode.WANDER,
        partnerId = null,
        modeUntilMs = 0L,
        action = PlayerAction.WALK,
        targetX = t.first, targetY = t.second,
        speechRes = if (sayBye) AMBIENT_BYE else speechRes,
        speechUntilMs = if (sayBye) now + BYE_SHOW_MS else speechUntilMs,
        stuckX = x, stuckY = y, stuckSinceMs = now
    )
}

// Un paso de caminata hacia (tx,ty) con deslizamiento por eje. Devuelve la posición nueva.
private fun stepTowards(room: ZombieRoom, x: Float, y: Float, tx: Float, ty: Float, speed: Float): Pair<Float, Float> {
    val dx = tx - x
    val dy = ty - y
    val d = hypot(dx, dy)
    if (d < 0.01f) return x to y
    val nx = dx / d
    val ny = dy / d
    val cx = x + nx * speed
    val cy = y + ny * speed
    return when {
        walkable(room, cx, cy) -> cx to cy
        walkable(room, cx, y) -> cx to y
        walkable(room, x, cy) -> x to cy
        else -> x to y
    }
}

/**
 * Un paso de simulacion de los NPCs ambientales: deambular + VIDA UNIVERSITARIA (encuentros,
 * pláticas con burbujas, caminar juntos, despedidas) + ANTI-ATASCO. Devuelve la nueva lista.
 */
internal fun ZombieInteriorViewModel.stepAmbientNpcs(
    npcs: List<AmbientNpc>, room: ZombieRoom, now: Long
): List<AmbientNpc> {
    if (npcs.isEmpty()) return npcs
    val byId = HashMap<String, AmbientNpc>(npcs.size)
    npcs.forEach { byId[it.id] = it }

    // ── 0. CONSISTENCIA de parejas: si mi pareja ya no existe o no me apunta, quedo libre. ──
    for (npc in npcs) {
        val pid = npc.partnerId ?: continue
        val partner = byId[pid]
        if (partner == null || partner.partnerId != npc.id) {
            byId[npc.id] = npc.unpair(room, now, sayBye = false)
        }
    }

    // ── 1. EMPAREJADOR: de vez en cuando, dos NPCs libres QUEDAN DE VERSE en un punto común.
    //       (Los policías m2cop_* de la Misión 2 NO platican: se excluyen del emparejador.) ──
    if (Random.nextFloat() < PAIR_CHANCE_PER_TICK) {
        val free = byId.values.filter {
            it.mode == AmbientMode.WANDER && it.partnerId == null && !it.id.startsWith(M2COP_PREFIX)
        }
        if (free.size >= 2) {
            val a = free[Random.nextInt(free.size)]
            var b = free[Random.nextInt(free.size)]
            if (b.id == a.id) b = free.first { it.id != a.id }
            // Punto de encuentro caminable ~a medio camino entre ambos (con fallback aleatorio).
            val mid = ((a.x + b.x) / 2f) to ((a.y + b.y) / 2f)
            val meet = if (walkable(room, mid.first, mid.second)) mid else randomWalkable(room)
            if (meet != null) {
                // La semilla de pareja = tick del encuentro (idéntica para ambos; único escritor).
                byId[a.id] = a.copy(
                    mode = AmbientMode.MEETING, partnerId = b.id, action = PlayerAction.WALK,
                    targetX = meet.first - TALK_GAP / 2f, targetY = meet.second,
                    modeUntilMs = now + WALK_TOGETHER_CAP_MS, pairSeed = now,
                    stuckX = a.x, stuckY = a.y, stuckSinceMs = now
                )
                byId[b.id] = b.copy(
                    mode = AmbientMode.MEETING, partnerId = a.id, action = PlayerAction.WALK,
                    targetX = meet.first + TALK_GAP / 2f, targetY = meet.second,
                    modeUntilMs = now + WALK_TOGETHER_CAP_MS, pairSeed = now,
                    stuckX = b.x, stuckY = b.y, stuckSinceMs = now
                )
            }
        }
    }

    // ── 2. SIMULACIÓN por NPC. ──
    val result = ArrayList<AmbientNpc>(npcs.size)
    for (id0 in npcs.map { it.id }) {
        var npc = byId[id0] ?: continue
        // Limpia la burbuja expirada (la despedida sobrevive a la separación hasta expirar).
        if (npc.speechRes != null && now >= npc.speechUntilMs) {
            npc = npc.copy(speechRes = null)
        }

        when (npc.mode) {
            // ── PLÁTICA: parados frente a frente, GUION coherente con líneas alternadas. ──
            AmbientMode.TALK -> {
                val partner = npc.partnerId?.let { byId[it] }
                // Guion DETERMINISTA de la pareja (misma semilla → mismo guion en ambos) y línea
                // que toca según el tiempo transcurrido desde talkStartMs.
                val script = AMBIENT_CONVOS[
                    (Random(npc.pairSeed).nextInt(AMBIENT_CONVOS.size))
                ]
                val talkStart = if (npc.talkStartMs > 0L) npc.talkStartMs else now
                val lineIdx = ((now - talkStart) / SPEECH_TURN_MS).toInt()
                if (partner == null) {
                    npc = npc.unpair(room, now, sayBye = false)
                } else if (now >= npc.modeUntilMs || lineIdx >= script.size) {
                    // Fin de la plática: 50% se van CAMINANDO JUNTOS, 50% despedida y separación.
                    // Decisión y destino común DETERMINISTAS desde pairSeed (idéntica en ambos):
                    // salen iguales para los dos, así CADA UNO se configura a sí mismo (sin
                    // escribirle al otro → sin carreras por orden de procesamiento).
                    val rnd = Random(npc.pairSeed * 31L + 7L)
                    val together = rnd.nextBoolean()
                    val t = if (together) randomWalkable(room, rnd) else null
                    npc = if (together && t != null) {
                        val offset = if (npc.id < partner.id) -WALK_TOGETHER_OFFSET else WALK_TOGETHER_OFFSET
                        npc.copy(
                            mode = AmbientMode.WALK_TOGETHER, action = PlayerAction.WALK,
                            targetX = t.first + offset, targetY = t.second,
                            modeUntilMs = now + WALK_TOGETHER_CAP_MS,
                            stuckX = npc.x, stuckY = npc.y, stuckSinceMs = now
                        )
                    } else {
                        npc.unpair(room, now, sayBye = true)
                    }
                } else {
                    // Frente a frente (idle) + GUION por turnos: las líneas PARES (0,2,4…) las
                    // dice el NPC de id MENOR y las impares el otro — ambos derivan lo mismo.
                    val myLine = (lineIdx % 2 == 0) == (npc.id < (npc.partnerId ?: ""))
                    val lineEndMs = talkStart + (lineIdx + 1L) * SPEECH_TURN_MS
                    npc = npc.copy(
                        action = PlayerAction.IDLE,
                        facingRight = partner.x >= npc.x,
                        speechRes = if (myLine) script[lineIdx] else npc.speechRes,
                        speechUntilMs = if (myLine) lineEndMs.coerceAtMost(now + SPEECH_SHOW_MS)
                                        else npc.speechUntilMs
                    )
                }
            }

            // ── YENDO AL ENCUENTRO o CAMINANDO JUNTOS: caminar a mi objetivo (con anti-atasco). ──
            AmbientMode.MEETING, AmbientMode.WALK_TOGETHER -> {
                val partner = npc.partnerId?.let { byId[it] }
                when {
                    partner == null || now >= npc.modeUntilMs ->
                        npc = npc.unpair(room, now, sayBye = npc.mode == AmbientMode.WALK_TOGETHER)
                    npc.mode == AmbientMode.MEETING &&
                        hypot(partner.x - npc.x, partner.y - npc.y) <= TALK_START_DIST -> {
                        // ¡Se encontraron! → plática. Duración DETERMINISTA desde pairSeed: aunque
                        // cada quien transicione en un tick distinto (~33 ms), ambos platican ~igual
                        // y la decisión de después (pairSeed) es la MISMA para los dos.
                        val talkMs = TALK_MIN_MS +
                            (Random(npc.pairSeed).nextLong(TALK_MAX_MS - TALK_MIN_MS))
                        npc = npc.copy(
                            mode = AmbientMode.TALK, action = PlayerAction.IDLE,
                            modeUntilMs = now + talkMs, facingRight = partner.x >= npc.x,
                            // Inicio del GUION: la línea que toca se deriva de aquí. Cada NPC
                            // transiciona en un tick distinto (~33 ms de diferencia): la línea
                            // derivada es la MISMA en la práctica (SPEECH_TURN_MS ≫ un tick).
                            talkStartMs = now
                        )
                    }
                    else -> {
                        val (rx, ry) = stepTowards(room, npc.x, npc.y, npc.targetX, npc.targetY, AMBIENT_SPEED)
                        val arrived = hypot(npc.targetX - rx, npc.targetY - ry) <= AMBIENT_ARRIVE
                        npc = npc.copy(
                            x = rx, y = ry,
                            facingRight = if (abs(npc.targetX - npc.x) > 0.5f) npc.targetX >= npc.x else npc.facingRight,
                            action = if (arrived) PlayerAction.IDLE else PlayerAction.WALK
                        )
                        // Paseo juntos: al LLEGAR al destino común, despedida y separación.
                        if (arrived && npc.mode == AmbientMode.WALK_TOGETHER) {
                            npc = npc.unpair(room, now, sayBye = true)
                        }
                    }
                }
            }

            // ── DEAMBULAR (clásico). ──
            AmbientMode.WANDER -> {
                if (npc.action == PlayerAction.IDLE && now < npc.retargetAtMs) {
                    result.add(npc); byId[npc.id] = npc; continue
                }
                val distT = hypot(npc.targetX - npc.x, npc.targetY - npc.y)
                if (npc.action == PlayerAction.IDLE || distT <= AMBIENT_ARRIVE) {
                    val t = randomWalkable(room)
                    npc = if (t == null) npc.copy(action = PlayerAction.IDLE, retargetAtMs = now + AMBIENT_WAIT_MS)
                        else npc.copy(action = PlayerAction.WALK, targetX = t.first, targetY = t.second,
                                      stuckX = npc.x, stuckY = npc.y, stuckSinceMs = now)
                } else {
                    val (rx, ry) = stepTowards(room, npc.x, npc.y, npc.targetX, npc.targetY, AMBIENT_SPEED)
                    val arrived = hypot(npc.targetX - rx, npc.targetY - ry) <= AMBIENT_ARRIVE
                    npc = npc.copy(
                        x = rx, y = ry,
                        facingRight = if (abs(npc.targetX - npc.x) > 0.5f) npc.targetX >= npc.x else npc.facingRight,
                        action = if (arrived) PlayerAction.IDLE else PlayerAction.WALK,
                        retargetAtMs = if (arrived) now + AMBIENT_WAIT_MS else npc.retargetAtMs
                    )
                }
            }
        }

        // ── 2b. EVITAR AMONTONAMIENTO / ATASCOS DE CERCANÍA:
        // Si el NPC está en modo WANDER, comprobamos si está demasiado cerca del jugador
        // o de otro NPC (que no sea su pareja activa). Si es así, forzamos cambio de rumbo.
        if (npc.mode == AmbientMode.WANDER) {
            val px = _state.value.playerX
            val py = _state.value.playerY
            val distToPlayer = hypot(npc.x - px, npc.y - py)
            var tooClose = distToPlayer < 35f

            if (!tooClose) {
                for (other in npcs) {
                    if (other.id != npc.id) {
                        val distToOther = hypot(npc.x - other.x, npc.y - other.y)
                        val arePartners = npc.partnerId == other.id
                        if (distToOther < 35f && !arePartners) {
                            tooClose = true
                            break
                        }
                    }
                }
            }

            if (tooClose) {
                val t = randomWalkable(room)
                if (t != null) {
                    npc = npc.copy(
                        action = PlayerAction.WALK,
                        targetX = t.first, targetY = t.second,
                        stuckX = npc.x, stuckY = npc.y, stuckSinceMs = now
                    )
                }
            }
        }

        // ── 3. ANTI-ATASCO (solo modos que CAMINAN): ¿lleva demasiado sin moverse? ──
        if (npc.action == PlayerAction.WALK) {
            if (hypot(npc.x - npc.stuckX, npc.y - npc.stuckY) > STUCK_EPS) {
                // Sí se movió: RE-ARMA el checkpoint (si se vuelve a atorar, se corrige otra vez).
                npc = npc.copy(stuckX = npc.x, stuckY = npc.y, stuckSinceMs = now)
            } else if (npc.stuckSinceMs > 0L && now - npc.stuckSinceMs > STUCK_MS) {
                // ATORADO contra una pared/esquina: CAMBIA DE DIRECCIÓN (nuevo objetivo aleatorio).
                // Si iba emparejado, cancela la pareja (el punto de encuentro era inalcanzable).
                val t = randomWalkable(room)
                npc = if (npc.partnerId != null) npc.unpair(room, now, sayBye = false)
                else npc.copy(
                    targetX = t?.first ?: npc.x, targetY = t?.second ?: npc.y,
                    action = PlayerAction.WALK,
                    stuckX = npc.x, stuckY = npc.y, stuckSinceMs = now
                )
            } else if (npc.stuckSinceMs == 0L) {
                npc = npc.copy(stuckX = npc.x, stuckY = npc.y, stuckSinceMs = now)
            }
        }

        byId[npc.id] = npc
        result.add(npc)
    }
    return result
}

/**
 * MISIÓN 2 · LATA APESTOSA (salón de la mochila): los NPCs ambientales CORREN hacia la puerta de
 * salida de la sala y DESAPARECEN al llegar (salieron del salón por el olor). Sustituye a
 * stepAmbientNpcs mientras dura la evacuación; cuando la lista queda vacía, el VM hace aparecer
 * la mochila. Respeta la matriz de colisión con deslizamiento por eje (igual que el deambular).
 */
internal fun ZombieInteriorViewModel.evacuateAmbientNpcs(
    npcs: List<AmbientNpc>, room: ZombieRoom
): List<AmbientNpc> {
    if (npcs.isEmpty()) return npcs
    // Punto de salida = centro de la PRIMERA puerta de la sala (el salón solo tiene una).
    val exit = room.doors.firstOrNull()
    val ex = exit?.let { (it.hitboxFrac.left + it.hitboxFrac.right) * 0.5f * room.worldWidth }
        ?: (room.worldWidth * 0.5f)
    val ey = exit?.let { (it.hitboxFrac.top + it.hitboxFrac.bottom) * 0.5f * room.worldHeight }
        ?: (room.worldHeight * 0.92f)
    val speed = AMBIENT_SPEED * 2.4f   // corren: el olor es insoportable
    return npcs.mapNotNull { npc ->
        val d = hypot(ex - npc.x, ey - npc.y)
        if (d <= 46f) return@mapNotNull null   // llegó a la puerta → sale del salón
        val (rx, ry) = stepTowards(room, npc.x, npc.y, ex, ey, speed)
        npc.copy(
            x = rx, y = ry,
            facingRight = if (abs(ex - npc.x) > 0.5f) ex >= npc.x else npc.facingRight,
            action = PlayerAction.RUN,
            // Huyendo nadie platica: se cancela pareja y burbuja.
            mode = AmbientMode.WANDER, partnerId = null, speechRes = null
        )
    }
}

/** Crea los dos estudiantes del rumor de la Misión 2 ubicados frente a frente en el Lobby. */
internal fun ZombieInteriorViewModel.spawnMission2RumorStudents(room: ZombieRoom): List<AmbientNpc> {
    val cx = room.worldWidth * 0.5f
    val cy = room.worldHeight * 0.5f
    return listOf(
        AmbientNpc(
            id = "m2rumor_a",
            x = cx - 35f, y = cy,
            skin = PlayerSkin.escomboy,
            action = PlayerAction.IDLE,
            facingRight = true,
            targetX = cx - 35f, targetY = cy
        ),
        AmbientNpc(
            id = "m2rumor_b",
            x = cx + 35f, y = cy,
            skin = PlayerSkin.escomgirl,
            action = PlayerAction.IDLE,
            facingRight = false,
            targetX = cx + 35f, targetY = cy
        )
    )
}
