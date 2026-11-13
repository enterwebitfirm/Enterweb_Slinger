// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/UiState.kt ===
package com.enterwebslinger.network.domain.model

/**
 * Standardized immutable UI state sealed hierarchy across ViewModels and Screens.
 */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val throwable: Throwable? = null) : UiState<Nothing>
}
