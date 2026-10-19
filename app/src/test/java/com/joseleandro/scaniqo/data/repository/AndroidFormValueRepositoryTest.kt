package com.joseleandro.scaniqo.data.repository

import android.app.Application
import android.content.Context
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class AndroidFormValueRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun clear() {
        context.getSharedPreferences("saved_form_values", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun explicitSaveSurvivesNewRepositoryAndDeleteRemovesStoredValue() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = AndroidFormValueRepository(context, dispatcher)
        assertTrue(first.list().isEmpty())
        val saved = first.save("Nome", "José Leandro").single()
        val reopened = AndroidFormValueRepository(context, dispatcher)
        assertEquals(listOf(saved), reopened.list())
        assertTrue(reopened.delete(saved.id).isEmpty())
        assertTrue(first.list().isEmpty())
    }

    @Test fun sameLabelUpdatesExistingEntryWithoutCreatingDuplicates() = runTest {
        val repository = AndroidFormValueRepository(context, StandardTestDispatcher(testScheduler))
        val old = repository.save("Empresa", "Primeira").single()
        val updated = repository.save("empresa", "Nova").single()
        assertEquals(old.id, updated.id)
        assertEquals("Nova", updated.value)
        assertEquals(1, repository.list().size)
    }

    @Test fun invalidTextDoesNotReplacePreviouslySavedValues() = runTest {
        val repository = AndroidFormValueRepository(context, StandardTestDispatcher(testScheduler))
        val original = repository.save("Nome", "José")
        try {
            repository.save("Nome", " ")
            fail("Blank values must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(original, repository.list())
    }
}
