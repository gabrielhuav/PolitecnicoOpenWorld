package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig
import ovh.gabrielhuav.pow.domain.models.map.EscomBoundingBox
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// VIDA DE CAMPUS (ESCOM, mapa global) — extensiones del VM, SIN gemelo miembro.
//
// El campus se sentía "apagado": solo el jugador y los actores de misión. Este tick mantiene
// ~10 ESTUDIANTES ambientales (prefijo CAMPUS_) dentro del bbox de la ESCOM: la mayoría
// DEAMBULA (beeline, zona libre) y, por ventanas de tiempo, un GRUPO DE 3 se junta a PLATICAR
// (parados en triángulo, burbujas 💬 alternadas). Todo es DETERMINISTA por cubetas de tiempo
// (sin estado extra por NPC): mismo patrón que la patrulla de la vieja fase 1 de la Misión 2.
//
// Actores en campusNpcs: lista propia fusionada en uiState.npcs por updateNpcsState — NO son
// atacables, NO tocan la IA/red, buildSaveData los EXCLUYE (prefijo CAMPUS_) y NO se persisten.
// Corre en mundo libre Y durante las misiones 2/3 (escenografía de fondo); se pausa en las
// escenas de la Misión 1 (escolta/persecución ya traen su propia multitud) y en zombi global.
// Coste: trigonometría simple para ≤10 NPCs por tick — OK gama baja.
// ─────────────────────────────────────────────────────────────────────────────

private const val CAMPUS_NPC_COUNT = 10
private const val CAMPUS_WALK_SPEED = 0.0000030      // pasean tranquilos (grados/tick)
// Cubeta de DEAMBULEO: cada ~14 s cada NPC elige (determinista) un nuevo destino del campus.
private const val CAMPUS_WANDER_BUCKET_MS = 14_000L
// Ventana de GRUPO: cada ~45 s se elige (determinista) un TRÍO distinto que se junta a platicar.
private const val CAMPUS_GROUP_BUCKET_MS = 45_000L
private const val CAMPUS_TALK_GAP_DEG = 0.000045     // ~5 m entre los del grupo
private const val CAMPUS_MARGIN = 0.00018            // margen al borde del bbox (~20 m)

private fun campusDist(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val dLat = aLat - bLat
    val dLon = aLon - bLon
    return sqrt(dLat * dLat + dLon * dLon)
}

// Punto pseudoaleatorio DETERMINISTA dentro del campus (semilla → mismo punto).
private fun campusPoint(seed: Long): GeoPoint {
    val rnd = java.util.Random(seed)
    val half = EscomBoundingBox.HALF_OFFSET - CAMPUS_MARGIN
    return GeoPoint(
        EscomBoundingBox.CENTER_LAT + (rnd.nextDouble() * 2.0 - 1.0) * half,
        EscomBoundingBox.CENTER_LON + (rnd.nextDouble() * 2.0 - 1.0) * half
    )
}

// ── TICK PRINCIPAL (lo llama el game loop MIEMBRO tras runDynamicEventsTick) ──
internal fun WorldMapViewModel.runCampusLifeTick(playerLoc: GeoPoint) {
    if (currentInteriorRoomId != null) return
    // Sin público o sin sentido: fuera del campus, apocalipsis global o escenas de la Misión 1.
    val active = !_uiState.value.globalZombieMode &&
        isInsideEscom(playerLoc.latitude, playerLoc.longitude) &&
        !isCampaignEscortActive() && !isMission1ChaseActive()
    if (!active) {
        if (campusNpcs.isNotEmpty()) { campusNpcs.clear(); updateNpcsState() }
        return
    }
    val now = System.currentTimeMillis()

    // GRUPO de la ventana actual: 3 índices deterministas + su punto de reunión.
    val groupBucket = now / CAMPUS_GROUP_BUCKET_MS
    val gRnd = java.util.Random(groupBucket * 131L + 17L)
    val g0 = gRnd.nextInt(CAMPUS_NPC_COUNT)
    val g1 = (g0 + 1 + gRnd.nextInt(CAMPUS_NPC_COUNT - 2)) % CAMPUS_NPC_COUNT
    val g2 = (g1 + 1 + gRnd.nextInt(CAMPUS_NPC_COUNT - 2)) % CAMPUS_NPC_COUNT
    val meet = campusPoint(groupBucket * 977L + 5L)
    val groupIdx = setOf(g0, g1, g2)

    for (i in 0 until CAMPUS_NPC_COUNT) {
        val id = "CAMPUS_$i"
        // Spawn perezoso e idempotente (también tras teleport/limpieza): nace en su punto propio.
        val npc = campusNpcs[id] ?: Npc(
            id = id,
            type = NpcType.PERSON,
            location = campusPoint(i * 7919L + 3L),
            speed = CAMPUS_WALK_SPEED,
            isRemote = false,
            isMoving = true,
            visualConfig = campusRandomStudentVisual(i)
        ).also { campusNpcs[id] = it }

        val inGroup = i in groupIdx && g0 != g1 && g1 != g2 && g0 != g2
        // Destino: los del grupo van a su lugar del triángulo; el resto deambula por cubetas.
        val target = if (inGroup) {
            val slot = groupIdx.campusSlotOf(i)   // 0..2 estable dentro de la ventana
            val ang = 2.0 * Math.PI * slot / 3.0
            GeoPoint(
                meet.latitude + sin(ang) * CAMPUS_TALK_GAP_DEG,
                meet.longitude + cos(ang) * CAMPUS_TALK_GAP_DEG
            )
        } else {
            campusPoint(i * 104729L + (now / CAMPUS_WANDER_BUCKET_MS) * 31L)
        }

        val d = campusDist(npc.location.latitude, npc.location.longitude,
            target.latitude, target.longitude)
        if (d > 0.00004) {
            // Camina hacia el destino (beeline: el campus es zona libre).
            val a = atan2(target.latitude - npc.location.latitude,
                target.longitude - npc.location.longitude)
            campusNpcs[id] = npc.copy(
                location = GeoPoint(
                    npc.location.latitude + sin(a) * CAMPUS_WALK_SPEED,
                    npc.location.longitude + cos(a) * CAMPUS_WALK_SPEED
                ),
                isMoving = true,
                facingRight = cos(a) >= 0
            )
        } else if (inGroup) {
            // Llegó al corrillo: parado PLATICANDO — burbuja 💬 alternada entre los 3.
            val talkerSlot = ((now / 3000L) % 3L).toInt()
            val talking = groupIdx.campusSlotOf(i) == talkerSlot
            campusNpcs[id] = npc.copy(
                isMoving = false,
                talkingUntil = if (talking && npc.talkingUntil < now + 400) now + 900 else npc.talkingUntil
            )
        } else if (npc.isMoving) {
            campusNpcs[id] = npc.copy(isMoving = false)   // pausa idle hasta la próxima cubeta
        }
    }
    updateNpcsState()
}

// Orden estable del índice dentro del grupo (Set no garantiza orden → lo fijamos ordenando).
// ⚠️ NO llamarla indexOf: taparía la extensión estándar Iterable.indexOf (gotcha de resolución).
private fun Set<Int>.campusSlotOf(i: Int): Int = this.sorted().indexOf(i)

// Limpieza (teleport lejos / salir de la zona la hace el propio tick al ver !active).
internal fun WorldMapViewModel.clearCampusLife() {
    if (campusNpcs.isNotEmpty()) {
        campusNpcs.clear()
        updateNpcsState()
    }
}

// Look de ESTUDIANTE determinista por índice (paleta juvenil; mismo patrón civil de la M2).
private fun campusRandomStudentVisual(i: Int): CharacterVisualConfig {
    val rnd = java.util.Random(i * 31L + 11L)
    val hairColors = listOf(
        androidx.compose.ui.graphics.Color.Black,
        androidx.compose.ui.graphics.Color.DarkGray,
        androidx.compose.ui.graphics.Color(0xFF8B4513),
        androidx.compose.ui.graphics.Color(0xFFDAA520)
    )
    val shirtColors = listOf(
        androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color.Red,
        androidx.compose.ui.graphics.Color.Blue, androidx.compose.ui.graphics.Color.Green,
        androidx.compose.ui.graphics.Color(0xFF9C27B0), androidx.compose.ui.graphics.Color(0xFFFF9800),
        androidx.compose.ui.graphics.Color(0xFF7B1FA2), androidx.compose.ui.graphics.Color(0xFF00695C)
    )
    return CharacterVisualConfig(
        bodyFolder = "npc_walk_1",
        bodyPrefix = "npc_walk_1_",
        hairId = 1 + rnd.nextInt(5),
        hairColor = hairColors[rnd.nextInt(hairColors.size)],
        shirtColor = shirtColors[rnd.nextInt(shirtColors.size)],
        pantsColor = androidx.compose.ui.graphics.Color(0xFF37474F)
    )
}
