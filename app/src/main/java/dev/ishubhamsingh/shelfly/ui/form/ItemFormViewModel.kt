package dev.ishubhamsingh.shelfly.ui.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ishubhamsingh.shelfly.data.repo.ItemRepository
import dev.ishubhamsingh.shelfly.domain.model.Category
import dev.ishubhamsingh.shelfly.domain.model.Item
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class FormUiState(
    val name: String             = "",
    val category: Category?      = null,
    val expiryDate: LocalDate?   = null,
    val qty: String              = "",
    val unit: String?            = null,
    val notes: String            = "",
    val isLoading: Boolean       = true,
    val isSaved: Boolean         = false,
    val existingItem: Item?      = null,
) {
    val isEditMode: Boolean get() = existingItem != null
    val canSave: Boolean get() = name.isNotBlank() && category != null && expiryDate != null
}

@HiltViewModel
class ItemFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val itemRepo: ItemRepository,
) : ViewModel() {

    private val itemId: String? = savedStateHandle["itemId"]

    private val _state = MutableStateFlow(FormUiState())
    val state: StateFlow<FormUiState> = _state.asStateFlow()

    init {
        if (itemId != null) {
            viewModelScope.launch {
                val item = itemRepo.getById(itemId)
                if (item != null) {
                    _state.update {
                        it.copy(
                            name         = item.name,
                            category     = item.category,
                            expiryDate   = item.expiryDate,
                            qty          = item.qty?.toString() ?: "",
                            unit         = item.unit,
                            notes        = item.notes ?: "",
                            isLoading    = false,
                            existingItem = item,
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false) }
                }
            }
        } else {
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun setName(value: String)           = _state.update { it.copy(name = value) }
    fun setCategory(value: Category)     = _state.update { it.copy(category = value) }
    fun setExpiryDate(value: LocalDate)  = _state.update { it.copy(expiryDate = value) }
    fun setQty(value: String)            = _state.update { it.copy(qty = value) }
    fun setUnit(value: String?)          = _state.update { it.copy(unit = value) }
    fun setNotes(value: String)          = _state.update { it.copy(notes = value.take(200)) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val qty = s.qty.toDoubleOrNull()
            if (s.isEditMode) {
                val updated = s.existingItem!!.copy(
                    name       = s.name.trim(),
                    category   = s.category!!,
                    expiryDate = s.expiryDate!!,
                    qty        = qty,
                    unit       = s.unit?.takeIf { it.isNotBlank() },
                    notes      = s.notes.trim().takeIf { it.isNotBlank() },
                )
                itemRepo.update(updated)
            } else {
                val item = Item(
                    id         = UUID.randomUUID().toString(),
                    name       = s.name.trim(),
                    category   = s.category!!,
                    expiryDate = s.expiryDate!!,
                    qty        = qty,
                    unit       = s.unit?.takeIf { it.isNotBlank() },
                    notes      = s.notes.trim().takeIf { it.isNotBlank() },
                    createdAt  = Instant.now(),
                )
                itemRepo.insert(item)
            }
            _state.update { it.copy(isSaved = true) }
        }
    }
}
