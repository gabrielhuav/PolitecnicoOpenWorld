package ovh.gabrielhuav.pow.features.streetfighter.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState

class SfPostureScaleTest {
    @Test
    fun folded_air_and_knockdown_poses_keep_their_body_scale() {
        for (state in listOf(SfFighterState.AIR_PUNCH, SfFighterState.AIR_KICK,
            SfFighterState.THROWN, SfFighterState.GET_UP, SfFighterState.KO,
            SfFighterState.HURT_HEAD_HEAVY, SfFighterState.HURT_BODY_HEAVY,
            SfFighterState.CROUCH_PUNCH, SfFighterState.SWEEP)) {
            assertTrue(state.keepsNaturalHeight(), "$state must not be inflated to standing height")
        }
        assertFalse(SfFighterState.IDLE.keepsNaturalHeight())
    }
}
