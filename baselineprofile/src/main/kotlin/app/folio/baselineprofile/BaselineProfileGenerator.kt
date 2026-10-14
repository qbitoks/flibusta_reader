package app.folio.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup and reading paths compiled ahead of time: launch to «Главная», open the first book,
 * turn pages, open the reader menu, close the book, switch to «Библиотека». Uses the user's own
 * library on the device (it needs at least one book on «Главная»).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "app.folio", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.textContains("%")), 5_000)

        val w = device.displayWidth
        val h = device.displayHeight
        // First «Продолжить» card.
        device.click(w * 3 / 10, h * 6 / 25)
        device.waitForIdle()
        Thread.sleep(2_500)
        repeat(4) {
            device.click(w * 95 / 100, h / 2)
            Thread.sleep(500)
        }
        device.swipe(w * 9 / 10, h / 2, w / 10, h / 2, 20)
        Thread.sleep(500)
        device.click(w / 2, h / 2)
        Thread.sleep(500)
        device.pressBack()
        Thread.sleep(1_500)
        device.findObject(By.text("Библиотека"))?.click()
        Thread.sleep(1_000)
    }
}
