package dev.ishubhamsingh.shelfly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ishubhamsingh.shelfly.data.repo.SettingsRepository
import dev.ishubhamsingh.shelfly.ui.navigation.Screen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepo: SettingsRepository,
) : ViewModel() {

    val dynamicColor: StateFlow<Boolean> = settingsRepo.settings
        .map { it.dynamicColor }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Null while DataStore hasn't emitted yet (prevents NavHost flicker). */
    val startDestination: StateFlow<String?> = settingsRepo.onboardingDone
        .map { done -> if (done) Screen.Home.route else Screen.Onboarding.route }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
