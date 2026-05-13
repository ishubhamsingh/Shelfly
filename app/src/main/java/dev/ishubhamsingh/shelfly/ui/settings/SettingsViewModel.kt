package dev.ishubhamsingh.shelfly.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ishubhamsingh.shelfly.data.repo.SettingsRepository
import dev.ishubhamsingh.shelfly.domain.model.Settings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(
        scope     = viewModelScope,
        started   = SharingStarted.WhileSubscribed(5_000),
        initialValue = Settings(),
    )

    fun setLeadTimeDays(days: Int) {
        viewModelScope.launch { settingsRepo.setLeadTimeDays(days) }
    }
}
