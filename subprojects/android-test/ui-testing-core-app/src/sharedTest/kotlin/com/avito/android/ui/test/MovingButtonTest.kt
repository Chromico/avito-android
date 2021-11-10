package com.avito.android.ui.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avito.android.test.app.core.screenRule
import com.avito.android.ui.MovingButtonActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovingButtonTest {

    @get:Rule
    val rule = screenRule<MovingButtonActivity>(launchActivity = true)

    @Test
    fun movingButton_clicked() {
        Screen.movingButton.movedButton.click()
        Screen.movingButton.movedButtonClickIndicatorView.checks.isDisplayed()
    }
}