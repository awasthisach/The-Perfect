package com.vvf.smartmanager

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ExampleRobolectricTest {
    @Test
    fun applicationContextLoads() {
        val app = ApplicationProvider.getApplicationContext<VVFApplication>()
        assertNotNull(app)
    }
}
