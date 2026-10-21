package com.volta.app.domain.update

sealed interface InstallResult {
    data object Success : InstallResult

    data class Failed(val message: String?) : InstallResult
}
