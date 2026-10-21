package com.volta.app.domain.update

/**
 * Launch checks follow the auto-check toggle and 24h throttle; manual checks ignore both. Every
 * successful check, manual ones included, restarts the 24h throttle.
 */
enum class UpdateCheckMode { LAUNCH, MANUAL }
