package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.viewmodel.NoticeBoardViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GCE Notice Board", appName)
    }

    @Test
    fun `NoticeBoardViewModel initializes without NullPointerException`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = NoticeBoardViewModel(application)

        assertNotNull(viewModel.collegeSettings)
        assertNotNull(viewModel.filteredNotices)
        assertTrue(viewModel.isPollingActive.value)
        assertEquals("ALL", viewModel.selectedDepartmentFilter.value)
        assertEquals("ALL", viewModel.selectedCategoryFilter.value)
    }
}
