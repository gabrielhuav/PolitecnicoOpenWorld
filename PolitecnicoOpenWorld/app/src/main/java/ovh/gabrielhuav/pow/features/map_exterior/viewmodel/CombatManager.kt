package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// ─────────────────────────────────────────────────────────────────────────────
// ETAPA 3 (descomposición del god-object, ver PLAN_descomponer_WorldMapViewModel.md):
// TERCER manager (3/6). Posee el estado UI de VIDA + FX DE IMPACTO del jugador.
//
// ⚠️ DISTINTO de Designer/Collectibles: estos campos NO son de WorldMapState (NO van por
// la fachada `combine`). Son `mutableStateOf` de Compose que las Views leen DIRECTO como
// `viewModel.playerHealth` / `.showHealthBar` / `.damagePulseTrigger` / `.impactEffectTrigger`.
// Para NO tocar ninguna View, el backing store se muda AQUÍ y el VM conserva miembros
// DELEGANTES (get/set → este manager). Compose sigue recomponiendo: al leer el getter del VM
// dentro de un @Composable se registra la lectura del State de este manager.
//
// La LÓGICA de combate (WorldMapCombat.kt) y de vida (WorldMapHealth.kt) sigue como
// extensiones del VM (muy enredada con remoteEntities/policía/red/campaña) y solo ESCRIBE
// estos valores → delega aquí. El único trozo de lógica pura del grupo es el THROTTLE del 💥,
// que vive aquí con un reloj inyectable para ser testeable en JVM.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @param clockMs reloj inyectable (default = reloj real). Permite testear el throttle del 💥
 *   sin depender del tiempo de pared.
 */
class CombatManager(private val clockMs: () -> Long = { System.currentTimeMillis() }) {

    /** Vida actual del jugador (0..maxPlayerHealth). La escriben takeDamage/heal/wasted del VM. */
    var playerHealth by mutableStateOf(100f)
        internal set

    /** Tope de vida (constante de diseño). */
    val maxPlayerHealth = 100f

    /** Contador de 💥: cada incremento dispara un destello en pantalla (la View lo observa). */
    var impactEffectTrigger by mutableStateOf(0)
        internal set

    /** Visibilidad de la barra de vida (el temporizador de auto-ocultado vive en el VM). */
    var showHealthBar by mutableStateOf(false)
        internal set

    /** Pulso de daño: cada incremento parpadea el viñeteado rojo (la View lo observa). */
    var damagePulseTrigger by mutableStateOf(0)
        internal set

    // Throttle del 💥: con muchos zombis/NPCs golpeándote, la lógica de contacto llamaba a
    // fireImpactEffect cada mordida y el 💥 central se veía "a cada rato". Limitamos a uno cada
    // IMPACT_THROTTLE_MS para que siga marcando colisiones notables sin spamear.
    private var lastImpactEffectMs = 0L

    /** Dispara el 💥 respetando el throttle (no-op si aún no pasó IMPACT_THROTTLE_MS). */
    fun fireImpactEffect() {
        val now = clockMs()
        if (now - lastImpactEffectMs < IMPACT_THROTTLE_MS) return
        lastImpactEffectMs = now
        impactEffectTrigger++
    }

    companion object {
        const val IMPACT_THROTTLE_MS = 900L
    }
}
