package com.volta.app.domain.coverage

data class CoverageGrid(val columns: Int, val rows: Int, val cells: List<List<Boolean>>)
