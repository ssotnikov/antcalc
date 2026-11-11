package local.antenna.calculator.update

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import local.antenna.calculator.BuildConfig
import local.antenna.calculator.state.AppLanguage
import local.antenna.calculator.state.CalculatorDraft
import local.antenna.calculator.state.CalculatorUiState
import local.antenna.calculator.ui.CalculatorApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class UpdateMenuTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val language = mutableStateOf(AppLanguage.RUSSIAN)
    private val installedUpdate = UpdateRelease("1.7.3", 63, 26, 512, "a".repeat(64))

    @Test fun menuChecksManuallyInBothLanguagesWhileCalculationsStayAvailable() {
        val checks = AtomicInteger()
        val result = CompletableDeferred<UpdateCheck>()
        val model = model { manual ->
            checks.incrementAndGet()
            if (manual) result.await() else UpdateCheck.Unavailable
        }
        show(model)
        compose.waitUntil(5000) { checks.get() == 1 }
        openMenu()
        compose.onNodeWithText("Проверить обновления").assertIsDisplayed()
        compose.onNodeWithText("Релизы на GitHub").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("check-for-updates").performClick()
        compose.onNodeWithText("Проверка обновлений").assertIsDisplayed()
        compose.onNodeWithTag("nav-TRIMMING").performClick()
        compose.runOnIdle { language.value = AppLanguage.ENGLISH }
        result.complete(UpdateCheck.Current)
        val text = compose.onNodeWithText("No updates available", useUnmergedTree = true)
        text.assertIsDisplayed()
        val panel = compose.onNodeWithTag("compact-update-notice", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val label = text.getUnclippedBoundsInRoot()
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        assertEquals((screen.left + screen.right).value, (panel.left + panel.right).value, 2f)
        assertEquals((label.left - panel.left).value, (panel.right - label.right).value, 1f)
        assertTrue(panel.right - panel.left < screen.right - screen.left)
        openMenu()
        compose.onNodeWithText("Check for updates").assertIsDisplayed()
        compose.onNodeWithText("GitHub releases").assertIsDisplayed().assertHasClickAction()
        assertEquals(2, checks.get())
    }

    @Test fun longUpdateNoticeKeepsTheStandardSnackbar() {
        show(model { UpdateCheck.RateLimited(60_000) }, fontScale = 2f)
        openMenu()
        compose.onNodeWithTag("check-for-updates").performClick()
        compose.onNodeWithText("GitHub ограничил запросы — повторите проверку позже").assertIsDisplayed()
        compose.onNodeWithTag("compact-update-notice").assertDoesNotExist()
    }

    @Test fun cancellationPersistsAndManualCheckOffersTheSameReleaseAgain() {
        val prefs = newPreferences()
        val checks = AtomicInteger()
        val first = model(prefs) { checks.incrementAndGet(); UpdateCheck.Available(installedUpdate) }
        val active = mutableStateOf(first)
        show(first, active = active, fontScale = 2f)
        val dialog = compose.onNodeWithTag("update-offer")
        waitForOffer()
        dialog.assertIsDisplayed()
        compose.onNodeWithText("Установленная версия ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        compose.onNodeWithText("Доступная версия ${installedUpdate.versionName}").assertIsDisplayed()
        compose.onNodeWithTag("update-notes").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("update-download").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("update-cancel").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("update-cancel").performClick()
        compose.waitUntil(5000) { first.state.value == UpdateUiState.Idle }
        assertFalse(UpdateDismissals(prefs).allows(installedUpdate.versionCode))

        val reopened = model(prefs) { checks.incrementAndGet(); UpdateCheck.Available(installedUpdate) }
        compose.runOnIdle { active.value = reopened }
        compose.waitUntil(5000) { checks.get() >= 2 }
        dialog.assertDoesNotExist()
        openMenu()
        compose.onNodeWithTag("check-for-updates").performClick()
        waitForOffer()
        dialog.assertIsDisplayed()
        compose.onNodeWithTag("update-cancel").performClick()
        compose.waitUntil(5000) { reopened.state.value == UpdateUiState.Idle }

        val newer = installedUpdate.copy(versionName = "1.7.4", versionCode = 64)
        val newerModel = model(prefs) { UpdateCheck.Available(newer) }
        compose.runOnIdle { active.value = newerModel }
        waitForOffer()
        dialog.assertIsDisplayed()
        compose.onNodeWithText("Доступная версия ${newer.versionName}").assertIsDisplayed()
        assertFalse(UpdateDismissals(prefs).allows(60))
    }

    @Test fun permissionPromptCanBeCancelled() {
        assumeFalse(compose.activity.packageManager.canRequestPackageInstalls())
        val prefs = newPreferences()
        val model = model(prefs) { UpdateCheck.Available(installedUpdate) }
        show(model)
        waitForOffer()
        compose.onNodeWithTag("update-download").performClick()
        compose.onNodeWithText("Разрешите установку APK").assertIsDisplayed()
        compose.onNodeWithText("Открыть настройки Android").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()
        compose.waitUntil(5000) { model.state.value == UpdateUiState.Idle }
        assertFalse(UpdateDismissals(prefs).allows(installedUpdate.versionCode))
    }

    @Test fun returningFromAndroidSettingsWithoutPermissionDismissesTheRelease() {
        assumeFalse(compose.activity.packageManager.canRequestPackageInstalls())
        val prefs = newPreferences()
        val model = model(prefs) { UpdateCheck.Available(installedUpdate) }
        show(model)
        visitInstallSettings { }

        compose.waitUntil(5000) { model.state.value == UpdateUiState.Idle }
        assertFalse(compose.activity.packageManager.canRequestPackageInstalls())
        assertFalse(UpdateDismissals(prefs).allows(installedUpdate.versionCode))
        compose.onNodeWithTag("update-offer").assertDoesNotExist()
    }

    @Test fun grantingPermissionInAndroidSettingsResumesTheSelectedRelease() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Run on a dedicated test device and restore the app-op after instrumentation",
            InstrumentationRegistry.getArguments().getString("grantUpdatePermission") == "true")
        assertFalse(compose.activity.packageManager.canRequestPackageInstalls())
        val request = AtomicReference<String>()
        val calls = AtomicInteger()
        val prefs = newPreferences()
        val model = DirectUpdateViewModel(
            compose.activity.applicationContext, UpdateDismissals(prefs),
            testCheck = { UpdateCheck.Available(installedUpdate) },
            transport = UpdateTransport { url, _, _ ->
                request.set(url)
                calls.incrementAndGet()
                UpdateHttpResponse(404)
            },
        )
        show(model)
        visitInstallSettings {
            val result = readShell("appops set ${instrumentation.targetContext.packageName} REQUEST_INSTALL_PACKAGES allow")
            assertTrue(result, result.isBlank())
            assertTrue(compose.activity.packageManager.canRequestPackageInstalls())
        }

        compose.waitUntil(5000) { model.state.value is UpdateUiState.InstallFailed }
        assertEquals("https://api.github.com/repos/ssotnikov/antcalc/releases/tags/${installedUpdate.tag}", request.get())
        assertEquals(1, calls.get())
        assertTrue(UpdateDismissals(prefs).allows(installedUpdate.versionCode))
    }

    private fun visitInstallSettings(onSettings: () -> Unit) {
        waitForOffer()
        compose.onNodeWithTag("update-download").performClick()
        compose.onNodeWithText("Открыть настройки Android").performClick()

        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun settingsFocused() = readShell("dumpsys window").lineSequence().any {
            "mCurrentFocus=" in it && "com.android.settings/" in it
        }
        fun settingsReady() = settingsFocused() && automation.rootInActiveWindow?.packageName == "com.android.settings"
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (!settingsReady() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        assertTrue("The Android Settings page must be ready to receive input", settingsReady())
        onSettings()
        repeat(3) {
            if (settingsFocused()) {
                readShell("input keyevent 4")
                SystemClock.sleep(500)
            }
        }
        assertFalse("Back must leave Android Settings", settingsFocused())
    }

    private fun readShell(command: String): String {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        return ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }
    }

    @Test fun startingTheSameViewModelAgainDoesNotRepeatAnAutomaticCheck() {
        val calls = AtomicInteger()
        val result = CompletableDeferred<UpdateCheck>()
        val model = model { calls.incrementAndGet(); result.await() }
        show(model)
        compose.waitUntil(5000) { calls.get() == 1 }
        compose.runOnIdle { model.start(); model.start() }
        result.complete(UpdateCheck.Available(installedUpdate))
        waitForOffer()
        compose.onNodeWithTag("update-offer").assertIsDisplayed()
        assertEquals(1, calls.get())
    }

    private fun waitForOffer() = compose.waitUntil(5000) {
        compose.onAllNodesWithTag("update-offer").fetchSemanticsNodes().isNotEmpty()
    }

    private fun openMenu() = compose.onNodeWithTag("menu").performClick()

    private fun newPreferences() =
        compose.activity.getSharedPreferences("update-test-${UUID.randomUUID()}", Context.MODE_PRIVATE)

    private fun model(prefs: android.content.SharedPreferences = newPreferences(), check: suspend (Boolean) -> UpdateCheck) =
        DirectUpdateViewModel(compose.activity.applicationContext, UpdateDismissals(prefs), check)

    private fun show(
        model: DirectUpdateViewModel,
        active: androidx.compose.runtime.State<DirectUpdateViewModel> = mutableStateOf(model),
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val draft = CalculatorDraft.defaults().let { it.copy(settings = it.settings.copy(language = language.value)) }
            val state = CalculatorUiState(draft = draft, loading = false)
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalActivity provides compose.activity,
                LocalActivityResultRegistryOwner provides compose.activity,
                LocalDirectUpdateViewModel provides active.value,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                CalculatorApp(state, {}) { state }
            }
        }
    }
}
