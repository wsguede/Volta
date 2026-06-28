package com.volta.app.ui.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptureViewModelTest {

    @Test
    fun `initial state has session inactive`() {
        val viewModel = CaptureViewModel()
        assertThat(viewModel.uiState.value.isSessionActive).isFalse()
    }

    @Test
    fun `startSession sets session active`() {
        val viewModel = CaptureViewModel()
        viewModel.startSession()
        assertThat(viewModel.uiState.value.isSessionActive).isTrue()
    }

    @Test
    fun `stopSession sets session inactive`() {
        val viewModel = CaptureViewModel()
        viewModel.startSession()
        viewModel.stopSession()
        assertThat(viewModel.uiState.value.isSessionActive).isFalse()
    }
}
