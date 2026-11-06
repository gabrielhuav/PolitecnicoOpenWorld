package ovh.gabrielhuav.pow.features.interiores.zombies.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.zombie.KeyDrop
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.ZombieGameState
import ovh.gabrielhuav.pow.features.settings.models.ControlType

@RunWith(AndroidJUnit4::class)
class ZombieInventorySelectionTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(
        ZombieGameState(
            showInventory = true,
            inventoryUnlockedSlots = 2,
            controlType = ControlType.DPAD,
            isLoading = false,
        )
    )
    private val selectedSlots = mutableListOf<Int>()
    private val discardedEntries = mutableListOf<String>()

    @Test
    fun semanticClickSelectsSlotAndMovesSelection() {
        showInventory()

        slot(0).assertIsEnabled().assertHasClickAction().assertIsNotSelected()
        slot(1).assertIsNotSelected()
        slot(0).performSemanticsAction(SemanticsActions.OnClick) { it() }

        slot(0).assertIsSelected()
        slot(1).assertIsNotSelected()
        slot(1).performSemanticsAction(SemanticsActions.OnClick) { it() }

        slot(0).assertIsNotSelected()
        slot(1).assertIsSelected()
        compose.runOnIdle {
            assertEquals(listOf(0, 1), selectedSlots)
            assertEquals(1, state.selectedInventorySlot)
            assertEquals(emptyList<String>(), discardedEntries)
        }
    }

    @Test
    fun lockedSlotIsDisabledAndTouchDoesNotChangeSelection() {
        showInventory()
        slot(0).performSemanticsAction(SemanticsActions.OnClick) { it() }

        slot(2).assertIsNotEnabled().assertIsNotSelected()
        slot(2).performTouchInput { click() }

        slot(0).assertIsSelected()
        slot(2).assertIsNotSelected()
        compose.runOnIdle {
            assertEquals(listOf(0), selectedSlots)
            assertEquals(0, state.selectedInventorySlot)
            assertEquals(emptyList<String>(), discardedEntries)
        }
    }

    @Test
    fun longPressSelectsOccupiedSlotAndDiscardsItsEntryOnce() {
        val entry = KeyDrop.inventoryEntry(KeyDrop.MISSION_1, KeyDrop.LAB1_KEY_ASSETS.first())
        state = state.copy(inventoryKeys = listOf(entry))
        showInventory()

        slot(0).assertIsEnabled().assertIsNotSelected()
        slot(0).performTouchInput { longClick() }

        slot(0).assertIsSelected()
        compose.runOnIdle {
            assertEquals(listOf(0), selectedSlots)
            assertEquals(listOf(entry), discardedEntries)
            assertEquals(emptyList<String>(), state.inventoryKeys)
        }
    }

    private fun slot(index: Int) = compose.onNodeWithContentDescription(
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.zhud_inv_slot, index + 1)
    )

    private fun showInventory() {
        compose.setContent {
            MaterialTheme {
                ZombieHud(
                    state = state,
                    roomName = "Test room",
                    isBuilding = true,
                    onMoveDir = {},
                    onMoveAngle = {},
                    onRun = {},
                    onInteract = {},
                    onSpecial = {},
                    onSecondaryPressed = {},
                    onSecondaryReleased = {},
                    onSelectMode = {},
                    onDismissInventory = {},
                    onSelectSlot = { index ->
                        selectedSlots += index
                        state = state.copy(selectedInventorySlot = index)
                    },
                    onDiscardKey = { entry ->
                        discardedEntries += entry
                        state = state.copy(inventoryKeys = state.inventoryKeys.filterNot { it == entry })
                    },
                )
            }
        }
    }
}
