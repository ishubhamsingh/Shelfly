package dev.ishubhamsingh.shelfly.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ishubhamsingh.shelfly.data.repo.ItemRepository
import dev.ishubhamsingh.shelfly.data.repo.SettingsRepository
import dev.ishubhamsingh.shelfly.domain.model.Item
import dev.ishubhamsingh.shelfly.domain.model.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class DetailUiState(
    val item: Item?          = null,
    val settings: Settings   = Settings(),
    val isLoading: Boolean   = true,
    val isDeleted: Boolean   = false,
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val itemRepo: ItemRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {

    private val itemId: String = requireNotNull(savedStateHandle["itemId"])

    private val _isDeleted = MutableStateFlow(false)

    val uiState: StateFlow<DetailUiState> = combine(
        itemRepo.observeById(itemId),
        settingsRepo.settings,
        _isDeleted,
    ) { item, settings, isDeleted ->
        DetailUiState(item = item, settings = settings, isLoading = false, isDeleted = isDeleted)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState(),
    )

    fun markConsumed() {
        viewModelScope.launch {
            val item = itemRepo.getById(itemId) ?: return@launch
            itemRepo.update(item.copy(consumed = true, consumedAt = Instant.now()))
        }
    }

    fun delete() {
        viewModelScope.launch {
            itemRepo.deleteById(itemId)
            _isDeleted.update { true }
        }
    }
}
