package com.volta.app.domain.stitching

sealed interface StitchingResult {
    data class Success(val jpegData: ByteArray) : StitchingResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return jpegData.contentEquals(other.jpegData)
        }

        override fun hashCode(): Int = jpegData.contentHashCode()
    }

    data class Error(val message: String) : StitchingResult
}
