package com.m4sak1.ryousyoku.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m4sak1.ryousyoku.model.Menu
import com.m4sak1.ryousyoku.repository.MenuRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

sealed class MenuState {
    object Loading : MenuState()
    data class Success(
        val allMenus: List<Menu>,
        val activeMenu: Menu?
    ) : MenuState()
    data class Error(val message: String) : MenuState()
}

class MenuViewModel : ViewModel() {
    private val repository = MenuRepository()

    private val _uiState = MutableStateFlow<MenuState>(MenuState.Loading)
    val uiState: StateFlow<MenuState> = _uiState.asStateFlow()

    init {
        fetchMenus()
    }

    fun fetchMenus() {
        viewModelScope.launch {
            _uiState.value = MenuState.Loading
            try {
                val menus = repository.fetchMenus()
                if (menus.isEmpty()) {
                    _uiState.value = MenuState.Success(emptyList(), null)
                    return@launch
                }

                var activeMenu = menus.first()
                val now = Calendar.getInstance()

                // Regex: YYYYMMDD-YYYYMMDD.pdf
                val regex = Regex("^(\\d{4})(\\d{2})(\\d{2})-(\\d{4})(\\d{2})(\\d{2})\\.pdf$")

                for (menu in menus) {
                    val matchResult = regex.find(menu.filename)
                    if (matchResult != null) {
                        val sy = matchResult.groupValues[1].toInt()
                        val sm = matchResult.groupValues[2].toInt() - 1 // Calendar is 0-indexed month
                        val sd = matchResult.groupValues[3].toInt()
                        
                        val ey = matchResult.groupValues[4].toInt()
                        val em = matchResult.groupValues[5].toInt() - 1
                        val ed = matchResult.groupValues[6].toInt()

                        val startCal = Calendar.getInstance().apply {
                            set(sy, sm, sd, 0, 0, 0)
                        }
                        val endCal = Calendar.getInstance().apply {
                            set(ey, em, ed, 23, 59, 59)
                        }

                        if (now.timeInMillis in startCal.timeInMillis..endCal.timeInMillis) {
                            activeMenu = menu
                            break
                        }
                    }
                }

                _uiState.value = MenuState.Success(menus, activeMenu)
            } catch (e: Exception) {
                _uiState.value = MenuState.Error(e.message ?: "Unknown Error")
            }
        }
    }
}
