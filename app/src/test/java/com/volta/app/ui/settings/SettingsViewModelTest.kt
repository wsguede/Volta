package com.volta.app.ui.settings

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelTest {

    private val viewModel = SettingsViewModel()

    @Test
    fun `default resolution is STANDARD`() = runTest {
        assertEquals(OutputResolution.STANDARD, viewModel.uiState.value.outputResolution)
    }

    @Test
    fun `setOutputResolution updates state`() = runTest {
        viewModel.setOutputResolution(OutputResolution.MINIMUM)
        assertEquals(OutputResolution.MINIMUM, viewModel.uiState.value.outputResolution)
    }
}
