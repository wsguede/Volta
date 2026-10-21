package com.volta.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.stitching.OutputResolution
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

    @Test
    fun `outputResolution falls back to STANDARD for an unrecognized stored value`() = runTest {
        val file = tempFolder.newFile("settings.preferences_pb")
        val setupScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val setupStore = PreferenceDataStoreFactory.create(scope = setupScope, produceFile = {
            file
        })
        setupStore.edit { it[stringPreferencesKey("output_resolution")] = "LEGACY_UNKNOWN_VALUE" }
        setupScope.cancel()

        val repository = DataStoreSettingsRepository(dataStore(file))

        repository.outputResolution.test {
            assertThat(awaitItem()).isEqualTo(OutputResolution.STANDARD)
        }
    }

    @Test
    fun `outputResolution falls back to STANDARD when the read throws IOException`() = runTest {
        val corruptingDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("corrupted file") }

            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences
            ): Preferences = error("not needed for this test")
        }
        val repository = DataStoreSettingsRepository(corruptingDataStore)

        repository.outputResolution.test {
            assertThat(awaitItem()).isEqualTo(OutputResolution.STANDARD)
            awaitComplete()
        }
    }

    @Test
    fun `autoCheckUpdates defaults to true when nothing persisted`() = runTest {
        val repository =
            DataStoreSettingsRepository(dataStore(tempFolder.newFile("settings.preferences_pb")))

        repository.autoCheckUpdates.test {
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `setAutoCheckUpdates updates autoCheckUpdates`() = runTest {
        val repository =
            DataStoreSettingsRepository(dataStore(tempFolder.newFile("settings.preferences_pb")))

        repository.setAutoCheckUpdates(false)

        repository.autoCheckUpdates.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `lastUpdateCheckMillis is null when nothing persisted`() = runTest {
        val repository =
            DataStoreSettingsRepository(dataStore(tempFolder.newFile("settings.preferences_pb")))

        repository.lastUpdateCheckMillis.test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `setLastUpdateCheckMillis updates lastUpdateCheckMillis`() = runTest {
        val repository =
            DataStoreSettingsRepository(dataStore(tempFolder.newFile("settings.preferences_pb")))

        repository.setLastUpdateCheckMillis(1_800_000_000_000L)

        repository.lastUpdateCheckMillis.test {
            assertThat(awaitItem()).isEqualTo(1_800_000_000_000L)
        }
    }

    @Test
    fun `update settings fall back to defaults when the read throws IOException`() = runTest {
        val corruptingDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("corrupted file") }

            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences
            ): Preferences = error("not needed for this test")
        }
        val repository = DataStoreSettingsRepository(corruptingDataStore)

        repository.autoCheckUpdates.test {
            assertThat(awaitItem()).isTrue()
            awaitComplete()
        }
        repository.lastUpdateCheckMillis.test {
            assertThat(awaitItem()).isNull()
            awaitComplete()
        }
    }

    @Test
    fun `update setters do not throw when the write fails with IOException`() = runTest {
        val failingDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { error("not needed for this test") }

            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences
            ): Preferences = throw IOException("disk full")
        }
        val repository = DataStoreSettingsRepository(failingDataStore)

        repository.setAutoCheckUpdates(false)
        repository.setLastUpdateCheckMillis(1L)
    }

    @Test
    fun `setLastUpdateCheckMillis does not throw when the write fails with any exception`() =
        runTest {
            val failingDataStore = object : DataStore<Preferences> {
                override val data: Flow<Preferences> = flow { error("not needed for this test") }

                override suspend fun updateData(
                    transform: suspend (t: Preferences) -> Preferences
                ): Preferences = throw IllegalStateException("store broken")
            }
            val repository = DataStoreSettingsRepository(failingDataStore)

            repository.setLastUpdateCheckMillis(1L)
        }

    @Test
    fun `setLastUpdateCheckMillis rethrows cancellation`() = runTest {
        val cancellingDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { error("not needed for this test") }

            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences
            ): Preferences = throw CancellationException("cancelled")
        }
        val repository = DataStoreSettingsRepository(cancellingDataStore)

        val thrown = runCatching { repository.setLastUpdateCheckMillis(1L) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun `update settings fall back to defaults when the read throws any exception`() = runTest {
        val brokenDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IllegalStateException("broken") }

            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences
            ): Preferences = error("not needed for this test")
        }
        val repository = DataStoreSettingsRepository(brokenDataStore)

        repository.autoCheckUpdates.test {
            assertThat(awaitItem()).isTrue()
            awaitComplete()
        }
        repository.lastUpdateCheckMillis.test {
            assertThat(awaitItem()).isNull()
            awaitComplete()
        }
    }

    @Test
    fun `setOutputResolution does not throw when the write fails with IOException`() = runTest {
        val failingDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { error("not needed for this test") }

            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences
            ): Preferences = throw IOException("disk full")
        }
        val repository = DataStoreSettingsRepository(failingDataStore)

        repository.setOutputResolution(OutputResolution.MINIMUM)
    }
}
