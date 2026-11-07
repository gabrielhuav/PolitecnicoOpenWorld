package ovh.gabrielhuav.pow.domain.models.map

/**
 * Estado de contacto por par de vehículos: el EVENTO de impacto (efecto, miedo, log) se emite una
 * sola vez por contacto; la resolución física sigue aplicándose cada tick. Un contacto termina tras
 * [RELEASE_MS] sin tocarse (histéresis: el freno deja ticks sueltos sin intersección).
 */
class VehicleContactLatch {
    private val lastContactMs = HashMap<String, Long>()

    /** `true` solo si el par no estaba en contacto; siempre refresca el último instante de contacto. */
    fun registerContact(pairKey: String, nowMs: Long): Boolean {
        val previous = lastContactMs[pairKey]
        lastContactMs[pairKey] = nowMs
        return previous == null || nowMs - previous > RELEASE_MS
    }

    fun prune(nowMs: Long) {
        val iterator = lastContactMs.entries.iterator()
        while (iterator.hasNext()) {
            if (nowMs - iterator.next().value > RELEASE_MS) iterator.remove()
        }
    }

    fun clear() = lastContactMs.clear()

    companion object {
        const val RELEASE_MS = 400L
    }
}
