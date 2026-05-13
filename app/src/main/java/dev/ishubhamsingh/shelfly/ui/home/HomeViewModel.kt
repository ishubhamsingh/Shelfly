package dev.ishubhamsingh.shelfly.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ishubhamsingh.shelfly.data.repo.ItemRepository
import dev.ishubhamsingh.shelfly.data.repo.SettingsRepository
import dev.ishubhamsingh.shelfly.domain.model.Item
import dev.ishubhamsingh.shelfly.domain.model.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class HomeFilter { ALL, EXPIRING_SOON, EXPIRED, CONSUMED }

data class HomeUiState(
    val items: List<Item>    = emptyList(),
    val filter: HomeFilter   = HomeFilter.ALL,
    val settings: Settings   = Settings(),
    val isLoading: Boolean   = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val itemRepo: ItemRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(HomeFilter.ALL)
    val filter: StateFlow<HomeFilter> = _filter.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        _filter,
        settingsRepo.settings,
    ) { filter, settings ->
        filter to settings
    }.flatMapLatest { (filter, settings) ->
        val itemsFlow = when (filter) {
            HomeFilter.ALL           -> itemRepo.observeAll()
            HomeFilter.EXPIRING_SOON -> itemRepo.observeExpiringSoon(settings.defaultLeadTimeDays)
            HomeFilter.EXPIRED       -> itemRepo.observeExpired()
            HomeFilter.CONSUMED      -> itemRepo.observeConsumed()
        }
        combine(itemsFlow, kotlinx.coroutines.flow.flowOf(filter to settings)) { items, (f, s) ->
            HomeUiState(items = items, filter = f, settings = s, isLoading = false)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun setFilter(filter: HomeFilter) {
        _filter.value = filter
    }
}
