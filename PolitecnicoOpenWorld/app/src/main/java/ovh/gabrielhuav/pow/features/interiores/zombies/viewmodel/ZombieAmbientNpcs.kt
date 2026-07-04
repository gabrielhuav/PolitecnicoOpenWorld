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
    // decisiones de pareja (duración de la plática, ¿caminar juntos?, destino común) se derivan de
    // aquí de forma DETERMINISTA → cada NPC se configura A SÍ MISMO sin escribirle al otro (sin
    // carreras por orden de procesamiento en el tick).
    val pairSeed: Long = 0L,
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

// Skins usadas como MODELO de NPC (FASE 1 = estudiantes existentes). Cada NPC toma una al azar.
private val AMBIENT_SKINS: List<PlayerSkin> =
    // Pool del interior de la ESCOM: estudiantes IPN + 1 docente + NPC generico.
    listOf(
        PlayerSkin.IPN_1, PlayerSkin.IPN_2, PlayerSkin.IPN_3, PlayerSkin.IPN_4, PlayerSkin.IPN_5, PlayerSkin.IPN_6,
        PlayerSkin.RND_1, PlayerSkin.DOC_1, PlayerSkin.EST_H1, PlayerSkin.EST_M1
    )

// FRASES de la vida universitaria (burbujas de las pláticas). @StringRes → traducibles (ES+EN).
private val AMBIENT_PHRASES: List<Int> = listOf(
    R.string.amb_phrase_1, R.string.amb_phrase_2, R.string.amb_phrase_3, R.string.amb_phrase_4,
    R.string.amb_phrase_5, R.string.amb_phrase_6, R.string.amb_phrase_7, R.string.amb_phrase_8,
    R.string.amb_phrase_9, R.string.amb_phrase_10
)
// Despedida al separarse (como el "Ahí nos vemos" de Prankedy al final de la Misión 1).
private val AMBIENT_BYE: Int = R.string.amb_phrase_bye

private const val AMBIENT_COUNT = 7
private const val AMBIENT_SPEED = 3.0f          // px por tick (caminar tranquilo)
private const val AMBIENT_RADIUS = 28f          // margen a paredes/bordes
private const val AMBIENT_ARRIVE = 18f          // distancia para considerar "llego" al objetivo
private const val AMBIENT_WAIT_MS = 600L       // pausa (idle) al llegar antes de re-elegir

// ── Anti-atasco ──
private const val STUCK_EPS = 6f                // si se movió menos que esto…
private const val STUCK_MS = 1600L              // …durante este tiempo, está ATORADO → cambia dirección

// ── Vida universitaria (encuentros/pláticas) ──
private const val PAIR_CHANCE_PER_TICK = 0.004f // ~1 encuentro cada ~8 s de tick (30 Hz) si hay libres
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
    if (isMultiplayer || room.id !in AMBIENT_ROOM_IDS || AMBIENT_SKINS.isEmpty()) return emptyList()
    val list = ArrayList<AmbientNpc>(AMBIENT_COUNT)
    repeat(AMBIENT_COUNT) { i ->
        val p = randomWalkable(room) ?: return@repeat
        val t = randomWalkable(room) ?: p
        list.add(
            AmbientNpc(
                id = "amb_$i",
                x = p.first, y = p.second,
                skin = AMBIENT_SKINS[Random.nextInt(AMBIENT_SKINS.size)],
                facingRight = Random.nextBoolean(),
                action = PlayerAction.WALK,
                targetX = t.first, targetY = t.second,
                retargetAtMs = 0L
            )
        )
    }
    return list
}

// ── Helpers de la vida universitaria ─────────────────────────────────────────

// Frase pseudoaleatoria DETERMINISTA por (npc, turno): no hay que guardar el índice de línea.
private fun phraseFor(id: String, bucket: Long): Int =
    AMBIENT_PHRASES[Random(id.hashCode() * 31L + bucket).nextInt(AMBIENT_PHRASES.size)]

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

    // ── 1. EMPAREJADOR: de vez en cuando, dos NPCs libres QUEDAN DE VERSE en un punto común. ──
    if (Random.nextFloat() < PAIR_CHANCE_PER_TICK) {
        val free = byId.values.filter { it.mode == AmbientMode.WANDER && it.partnerId == null }
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
            // ── PLÁTICA: parados frente a frente, burbujas alternadas. ──
            AmbientMode.TALK -> {
                val partner = npc.partnerId?.let { byId[it] }
                if (partner == null) {
                    npc = npc.unpair(room, now, sayBye = false)
                } else if (now >= npc.modeUntilMs) {
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
                    // Frente a frente (idle) + TURNOS de habla: bucket par habla el de id menor.
                    val myTurn = ((now / SPEECH_TURN_MS) % 2L == 0L) == (npc.id < (npc.partnerId ?: ""))
                    val bucket = now / SPEECH_TURN_MS
                    npc = npc.copy(
                        action = PlayerAction.IDLE,
                        facingRight = partner.x >= npc.x,
                        speechRes = if (myTurn) phraseFor(npc.id, bucket) else npc.speechRes,
                        speechUntilMs = if (myTurn) ((bucket + 1) * SPEECH_TURN_MS).coerceAtMost(now + SPEECH_SHOW_MS)
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
                            modeUntilMs = now + talkMs, facingRight = partner.x >= npc.x
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
