package ovh.gabrielhuav.pow.features.interiores.zombies.ui

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.gabrielhuav.pow.domain.models.zombie.CombatMode
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.ZombieGameState
import ovh.gabrielhuav.pow.features.settings.models.ControlType

@RunWith(AndroidJUnit4::class)
class ZombieControlHintsTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(
        ZombieGameState(controlType = ControlType.DPAD, isLoading = false)
    )
    private var secondaryPresses = 0
    private var secondaryReleases = 0
    private var dismissals = 0
    private val selectedModes = mutableListOf<CombatMode>()

    @Test
    fun spanishHintsFollowCombatModeAndInventoryAtLargeFontScale() {
        verifyHints(
            language = "es",
            fontScale = 1.5f,
            meleeHint = "MODO: GOLPE · B: golpear",
            rangedHint = "MODO: ARMA · B: disparar",
            inventoryHint = "Mantén Y: inventario",
            meleeMode = "GOLPE",
            closeLabel = "Cerrar",
            obsoleteHints = listOf(
                "MODO: GOLPE  (mantén Y)",
                "MODO: GOLPE  (pulsa B para atacar)",
            ),
        )
    }

    @Test
    fun englishHintsFollowCombatModeAndInventory() {
        state = state.copy(controlsScale = 1.3f)
        verifyHints(
            language = "en",
            fontScale = 1f,
            meleeHint = "MODE: MELEE · B: punch",
            rangedHint = "MODE: WEAPON · B: shoot",
            inventoryHint = "Hold Y: inventory",
            meleeMode = "MELEE",
            closeLabel = "Close",
            obsoleteHints = listOf(
                "MODE: MELEE  (hold Y)",
                "MODE: MELEE  (press B to attack)",
            ),
        )
    }

    private fun verifyHints(
        language: String,
        fontScale: Float,
        meleeHint: String,
        rangedHint: String,
        inventoryHint: String,
        meleeMode: String,
        closeLabel: String,
        obsoleteHints: List<String>,
    ) {
        showHud(language, fontScale)
        assertVisibleWithoutOverflow(meleeHint)
        assertVisibleWithoutOverflow(inventoryHint)
        compose.onNodeWithText(rangedHint).assertDoesNotExist()
        obsoleteHints.forEach { compose.onNodeWithText(it).assertDoesNotExist() }

        compose.runOnIdle { state = state.copy(combatMode = CombatMode.RANGED) }
        assertVisibleWithoutOverflow(rangedHint)
        compose.onNodeWithText(meleeHint).assertDoesNotExist()

        // Se atraviesa el detector táctil real de Y; el estado falso sustituye solo al VM.
        compose.onNodeWithText("Y").performTouchInput { longClick() }
        compose.onNodeWithText(meleeHint).assertDoesNotExist()
        compose.onNodeWithText(rangedHint).assertDoesNotExist()
        compose.onNodeWithText(inventoryHint).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, secondaryPresses)
            assertEquals(1, secondaryReleases)
            assertTrue(state.showInventory)
        }

        compose.onNodeWithText(meleeMode).performClick()
        compose.onNodeWithContentDescription(closeLabel)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performSemanticsAction(SemanticsActions.OnClick) { it() }

        assertVisibleWithoutOverflow(meleeHint)
        assertVisibleWithoutOverflow(inventoryHint)
        compose.onNodeWithText(rangedHint).assertDoesNotExist()
        compose.onNodeWithContentDescription(closeLabel).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(CombatMode.MELEE), selectedModes)
            assertEquals(1, dismissals)
            assertFalse(state.showInventory)
        }
    }

    private fun assertVisibleWithoutOverflow(text: String) {
        val node = compose.onNodeWithText(text).assertIsDisplayed()
        assertNoTextOverflow(node)
    }

    private fun assertNoTextOverflow(node: SemanticsNodeInteraction) {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            assertTrue(action(results))
        }
        assertTrue("Debe existir un resultado de composición del texto", results.isNotEmpty())
        assertTrue(
            "El hint debe mostrarse completo: " + results.joinToString { result ->
                "text=${result.layoutInput.text.text}, size=${result.size}, " +
                    "constraints=${result.layoutInput.constraints}, " +
                    "paragraph=${result.multiParagraph.width}x${result.multiParagraph.height}"
            },
            // El párrafo puede conservar el ancho máximo aunque el nodo se ajuste al texto.
            // Comprobamos sus líneas visibles, admitiendo solo redondeo de un píxel físico.
            results.all { result ->
                !result.multiParagraph.didExceedMaxLines &&
                    result.multiParagraph.height <= result.size.height + 1f &&
                    (0 until result.lineCount).all { line ->
                        !result.isLineEllipsized(line) &&
                            result.getLineRight(line) <= result.size.width + 1f &&
                            result.getLineLeft(line) >= -1f
                    }
            }
        )
    }

    private fun showHud(language: String, fontScale: Float) {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(targetContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            this.fontScale = fontScale
        }
        val localizedContext = targetContext.createConfigurationContext(configuration)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density, fontScale),
            ) {
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
                        onSecondaryPressed = { secondaryPresses++ },
                        onSecondaryReleased = {
                            secondaryReleases++
                            state = state.copy(showInventory = true)
                        },
                        onSelectMode = { mode ->
                            selectedModes += mode
                            state = state.copy(combatMode = mode)
                        },
                        onDismissInventory = {
                            dismissals++
                            state = state.copy(showInventory = false)
                        },
                    )
                }
            }
        }
    }
}
