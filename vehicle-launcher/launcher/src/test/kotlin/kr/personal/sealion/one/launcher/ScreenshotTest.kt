package kr.personal.sealion.one.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kr.personal.sealion.one.launcher.data.LauncherSettings
import kr.personal.sealion.one.launcher.ui.ClimatePage
import kr.personal.sealion.one.launcher.ui.DashboardPage
import kr.personal.sealion.one.launcher.ui.HomeScreen
import kr.personal.sealion.one.launcher.ui.LauncherTheme
import kr.personal.sealion.one.launcher.vehicle.DemoVehicle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders key screens at the head unit's landscape size for visual review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w1280dp-h720dp-land-mdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val state = DemoVehicle().read()

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            LauncherTheme(LauncherSettings()) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun home() = shot("home") { HomeScreen() }
    @Test fun dashboard() = shot("dashboard") { Box(Modifier.padding(20.dp)) { DashboardPage(state.copy(speedKmh = 63.0), LauncherSettings()) } }
    @Test fun climate() = shot("climate") { Box(Modifier.padding(20.dp)) { ClimatePage(state.climate, state.insideTempC, state.outsideTempC) } }
}
