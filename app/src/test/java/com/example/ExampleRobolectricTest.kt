package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.UserRole
import com.example.data.repository.NoticeBoardRepository
import com.example.ui.viewmodel.NoticeBoardViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    @Test
    fun `AppDatabase seeds 7 departments and role-only Principal, HOD, Faculty, and Student accounts`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        AppDatabase.ensureSeeded(db)
        val repo = NoticeBoardRepository(db)

        val depts = repo.getAllDepartments().first()
        val deptCodes = depts.map { it.code }.toSet()
        assertEquals(setOf("CIVIL", "MECH", "ECE", "EEE", "CSE", "METT", "AIML"), deptCodes)

        val principal = repo.authenticate("principal", "principal123")
        assertNotNull(principal)
        assertEquals("Admin Principal", principal?.fullName)
        assertEquals(UserRole.ADMIN.name, principal?.role)

        val hodCse = repo.authenticate("hod_cse", "hod123")
        assertNotNull(hodCse)
        assertEquals("HOD - CSE", hodCse?.fullName)
        assertEquals(UserRole.HOD.name, hodCse?.role)

        val facMett3 = repo.authenticate("faculty_mett3", "faculty123")
        assertNotNull(facMett3)
        assertEquals("Faculty 3 - METT", facMett3?.fullName)
        assertEquals(UserRole.FACULTY.name, facMett3?.role)

        val stuAiml10 = repo.authenticate("student_aiml10", "student123")
        assertNotNull(stuAiml10)
        assertEquals("Student 10 - AIML", stuAiml10?.fullName)
        assertEquals(UserRole.STUDENT.name, stuAiml10?.role)

        db.close()
    }
}
