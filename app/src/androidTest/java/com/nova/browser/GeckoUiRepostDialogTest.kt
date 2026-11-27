package com.nova.browser

import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.AllowOrDeny
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class GeckoUiRepostDialogTest {
    private fun run(button: Int): AllowOrDeny? {
        var result: AllowOrDeny? = null
        val latch = CountDownLatch(1)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { act ->
                val ui = GeckoUi(Handlers(act, {}, { _, _, _, _, _, _ -> }), { "https://www.example.com/form" })
                ui.showRepostDialog {
                    result = it
                    latch.countDown()
                }
            }
            onView(withText(containsString("example.com"))).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(S(button))).inRoot(isDialog()).perform(click())
            latch.await(5, TimeUnit.SECONDS)
        }
        return result
    }

    @Test
    fun resendAllows() {
        assertEquals(AllowOrDeny.ALLOW, run(R.string.gk_repost_yes))
    }

    @Test
    fun cancelDenies() {
        assertEquals(AllowOrDeny.DENY, run(R.string.gk_cancel))
    }
}
