package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminDashboardStats
import com.example.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminDashboardUiState(
    val isLoading: Boolean = false,
    val stats: AdminDashboardStats = AdminDashboardStats(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AdminViewModel(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminDashboardUiState())
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            adminRepository.loadAdminDashboard()
                .onSuccess { stats ->
                    _uiState.update { it.copy(isLoading = false, stats = stats) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Could not load platform analytics."
                        )
                    }
                }
        }
    }

    fun toggleSchoolVisibility(schoolCode: String, currentlyPublic: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            adminRepository.setSchoolDirectoryVisibility(schoolCode, !currentlyPublic)
                .onSuccess {
                    val label = if (!currentlyPublic) "visible in public directory" else "hidden from public directory"
                    _uiState.update { it.copy(successMessage = "School ($schoolCode) is now $label.") }
                    refreshDashboard()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Failed to update visibility."
                        )
                    }
                }
        }
    }
}
