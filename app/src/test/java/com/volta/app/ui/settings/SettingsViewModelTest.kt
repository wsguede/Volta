package com.volta.app.ui.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.settings.AppVersionProvider
import com.volta.app.domain.settings.SettingsRepository
import com.volta.app.domain.stitching.OutputResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private class FakeSettingsRepository(initial: OutputResolution = OutputResolution.STANDARD) :
    SettingsRepository {

    private val state = MutableStateFlow(initial)
    override val outputResolution = state

    var lastPersisted: OutputResolution? = null
        private set

    override suspend fun setOutputResolution(resolution: OutputResolution) {
        lastPersisted = resolution
        state.value = resolution
    }
}

private class FakeAppVersionProvider(override val versionName: String = "9.9.9") :
    AppVersionProvider

class SettingsViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState reflects the repository's persisted resolution`() = runTest {
        val repository = FakeSettingsRepository(initial = OutputResolution.MINIMUM)
        val viewModel = SettingsViewModel(repository, FakeAppVersionProvider())

        viewModel.uiState.test {
            assertThat(awaitItem().outputResolution).isEqualTo(OutputResolution.MINIMUM)
        }
    }

    @Test
    fun `setResolution persists the selection via the repository`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository, FakeAppVersionProvider())

        viewModel.setResolution(OutputResolution.MINIMUM)

        assertThat(repository.lastPersisted).isEqualTo(OutputResolution.MINIMUM)
    }

    @Test
    fun `uiState updates when the repository emits a new value`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository, FakeAppVersionProvider())

        viewModel.uiState.test {
            assertThat(awaitItem().outputResolution).isEqualTo(OutputResolution.STANDARD)
            viewModel.setResolution(OutputResolution.MINIMUM)
            assertThat(awaitItem().outputResolution).isEqualTo(OutputResolution.MINIMUM)
        }
    }

    @Test
    fun `uiState formats the app version from the version provider`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository, FakeAppVersionProvider(versionName = "9.9.9"))

        viewModel.uiState.test {
            assertThat(awaitItem().appVersion).isEqualTo("v9.9.9")
        }
    }
}
