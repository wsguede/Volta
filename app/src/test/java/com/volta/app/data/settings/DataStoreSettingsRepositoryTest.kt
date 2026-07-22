package com.volta.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.stitching.OutputResolution
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun TestScope.dataStore(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { file }
        )

    @Test
    fun `outputResolution defaults to STANDARD when nothing persisted`() = runTest {
        val repository =
            DataStoreSettingsRepository(dataStore(tempFolder.newFile("settings.preferences_pb")))

        repository.outputResolution.test {
            assertThat(awaitItem()).isEqualTo(OutputResolution.STANDARD)
        }
    }

    @Test
    fun `setOutputResolution updates outputResolution`() = runTest {
        val repository =
            DataStoreSettingsRepository(dataStore(tempFolder.newFile("settings.preferences_pb")))

        repository.setOutputResolution(OutputResolution.MINIMUM)

        repository.outputResolution.test {
            assertThat(awaitItem()).isEqualTo(OutputResolution.MINIMUM)
        }
    }

    @Test
    fun `selection persists across repository instances backed by the same file`() = runTest {
        val file = tempFolder.newFile("settings.preferences_pb")
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val firstInstance = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file })
        )
        firstInstance.setOutputResolution(OutputResolution.MINIMUM)
        // Simulates the app process (and its single DataStore instance) restarting.
        firstScope.cancel()

        val secondInstance = DataStoreSettingsRepository(dataStore(file))

        secondInstance.outputResolution.test {
            assertThat(awaitItem()).isEqualTo(OutputResolution.MINIMUM)
        }
    }
}
