package com.volta.app.domain.stitching

enum class OutputResolution(val width: Int, val height: Int) {
    MINIMUM(4096, 2048),
    STANDARD(8192, 4096)
}
