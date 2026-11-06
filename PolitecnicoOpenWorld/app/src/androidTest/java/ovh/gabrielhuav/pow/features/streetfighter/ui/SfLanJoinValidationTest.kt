package ovh.gabrielhuav.pow.features.streetfighter.ui

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import ovh.gabrielhuav.pow.MainActivity
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.features.streetfighter.data.SfLanGame

@RunWith(AndroidJUnit4::class)
class SfLanJoinValidationTest {
    @get:Rule(order = 0)
    val permissions = object : ExternalResource() {
        override fun before() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val packageName = instrumentation.targetContext.packageName
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).forEach {
                val descriptor = instrumentation.uiAutomation.executeShellCommand("pm grant $packageName $it")
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream -> stream.readBytes() }
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val lanJoins = mutableListOf<String>()
    private val roomJoins = mutableListOf<String>()
    private var cancelCount = 0
    private lateinit var errorText: String
    private lateinit var codeLabel: String
    private lateinit var joinLabel: String

    @Before
    fun prepareWindow() {
        compose.runOnUiThread {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        hideIme()
    }

    @After
    fun clearImeState() = hideIme()

    private fun hideIme() {
        compose.runOnUiThread {
            val activity = compose.activity
            activity.currentFocus?.clearFocus()
            activity.getSystemService(InputMethodManager::class.java)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) != true
        }
    }

    @Test
    fun joinRevalidatesTheAddressWhenAnOlderEnabledClickActionIsInvoked() {
        showMenu()
        field().performScrollTo().performTextReplacement("192.168.1.5")
        join().assertIsEnabled()
        val enabledClick = requireNotNull(join().fetchSemanticsNode().config[SemanticsActions.OnClick].action)

        field().performTextReplacement("999.999.999.999")
        join().assertIsNotEnabled()
        compose.runOnIdle {
            enabledClick()
            assertTrue("A stale enabled action must not connect an invalid address", lanJoins.isEmpty())
        }
    }

    @Test
    fun incompleteInputDoesNotShowAnErrorAndFilteredValidInputJoins() {
        showMenu()
        field().performScrollTo().performTextReplacement("192.168.1.")
        join().assertIsNotEnabled()
        compose.onNodeWithText(errorText).assertDoesNotExist()

        field().performTextReplacement("ip: 10.0.2.2 ")
        join().performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("10.0.2.2"), lanJoins) }
    }

    @Test
    fun englishErrorDoesNotClipCancelInLandscapeWithLargeText() {
        showMenu(fontScale = 1.3f)
        field().performScrollTo().performTextReplacement("192.168.01.5")
        compose.onNodeWithText(errorText).performScrollTo().assertIsDisplayed()
        join().assertIsNotEnabled()
        val cancel = compose.onNodeWithTag("sf-online-cancel").assertIsDisplayed()
        val viewport = compose.onNodeWithTag("lan-test-viewport").fetchSemanticsNode().boundsInRoot
        val bounds = cancel.fetchSemanticsNode().boundsInRoot
        assertTrue("Cancel must remain fully inside the viewport", viewport.contains(bounds.topLeft) &&
            bounds.right <= viewport.right && bounds.bottom <= viewport.bottom)
        cancel.performClick()
        compose.runOnIdle { assertEquals(1, cancelCount) }
    }

    @Test
    fun completeInvalidInputShowsSpanishFeedback() {
        showMenu(language = "es")
        field().performScrollTo().performTextReplacement("999.999.999.999")
        assertEquals("IP no válida. Ejemplo: 192.168.1.5", errorText)
        compose.onNodeWithText(errorText).performScrollTo().assertIsDisplayed()
        join().assertIsNotEnabled()
    }

    @Test
    fun roomCodesAndDiscoveredGamesKeepTheirSeparateJoinPaths() {
        showMenu(discovered = listOf(SfLanGame("192.168.43.1", "Test LAN host")))
        compose.onNodeWithText(codeLabel).performScrollTo().performTextReplacement("ab12")
        compose.onAllNodesWithText(joinLabel)[0].performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithText("▶  Test LAN host").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf("AB12"), roomJoins)
            assertEquals(listOf("192.168.43.1"), lanJoins)
        }
    }

    private fun field() = compose.onNodeWithTag("sf-lan-ip")
    private fun join() = compose.onNodeWithTag("sf-lan-join")

    private fun showMenu(
        language: String = "en",
        fontScale: Float = 1f,
        discovered: List<SfLanGame> = emptyList(),
    ) {
        val context = compose.activity
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        val localized = context.createConfigurationContext(configuration)
        errorText = localized.getString(R.string.sf_lan_ip_invalid)
        codeLabel = localized.getString(R.string.sf_mp_code_label)
        joinLabel = localized.getString(R.string.sf_mp_join)
        compose.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current
                val localeConfiguration = Configuration(LocalConfiguration.current).apply {
                    setLocale(Locale.forLanguageTag(language))
                }
                val resources = LocalContext.current.createConfigurationContext(localeConfiguration).resources
                CompositionLocalProvider(
                    LocalResources provides resources,
                    LocalConfiguration provides localeConfiguration,
                    LocalDensity provides Density(density.density, fontScale),
                ) {
                    MaterialTheme {
                        Box(Modifier.fillMaxSize().testTag("lan-test-viewport")) {
                            OnlineMenuOverlay(
                                onCreate = {},
                                onJoin = { roomJoins += it },
                                onQuickMatch = {},
                                onBtHost = {},
                                onBtScan = {},
                                onLanHost = {},
                                onLanJoin = { lanJoins += it },
                                lanDiscovered = discovered,
                                onLanScanStart = {},
                                onLanScanStop = {},
                                onDismiss = { cancelCount++ },
                            )
                        }
                    }
                }
            }
            compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
    }
}
