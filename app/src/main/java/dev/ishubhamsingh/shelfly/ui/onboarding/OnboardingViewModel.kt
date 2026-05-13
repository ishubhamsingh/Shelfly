package dev.ishubhamsingh.shelfly.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ishubhamsingh.shelfly.data.repo.SettingsRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepo.setOnboardingDone()
        }
    }
}
