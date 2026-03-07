package com.tinyoscillator.domain.model

sealed class EtfDataProgress {
    data class Loading(val message: String, val progress: Float = 0f) : EtfDataProgress()
    data class Success(val etfCount: Int, val holdingCount: Int) : EtfDataProgress()
    data class Error(val message: String) : EtfDataProgress()
}

sealed class EtfUiState {
    data object Idle : EtfUiState()
    data class Loading(val message: String, val progress: Float = 0f) : EtfUiState()
    data class Success(val etfCount: Int) : EtfUiState()
    data class Error(val message: String) : EtfUiState()
}
