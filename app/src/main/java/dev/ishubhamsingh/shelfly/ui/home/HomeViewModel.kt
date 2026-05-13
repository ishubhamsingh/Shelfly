package dev.ishubhamsingh.shelfly.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ishubhamsingh.shelfly.data.repo.ItemRepository
import dev.ishubhamsingh.shelfly.data.repo.SettingsRepository
import dev.ishubhamsingh.shelfly.domain.model.Category
import dev.ishubhamsingh.shelfly.domain.model.Item
import dev.ishubhamsingh.shelfly.domain.model.Settings
import dev.ishubhamsingh.shelfly.domain.util.daysUntilExpiry
import dev.ishubhamsingh.shelfly.domain.util.statusFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class HomeFilter { ALL, EXPIRING_SOON, EXPIRED, CONSUMED }
enum class HomeSort   { EXPIRY_DATE, CATEGORY, NAME, DATE_ADDED, QUANTITY }
enum class SortOrder  { ASCENDING, DESCENDING }

data class HomeUiState(
    val items          : List<Item> = emptyList(),
    val filter         : HomeFilter = HomeFilter.ALL,
    val sort           : HomeSort   = HomeSort.EXPIRY_DATE,
    val sortOrder      : SortOrder  = SortOrder.ASCENDING,
    val categoryFilter : Category?  = null,
    val settings       : Settings   = Settings(),
    val isLoading      : Boolean    = true,
)

private data class HomeParams(
    val filter   : HomeFilter,
    val sort     : HomeSort,
    val order    : SortOrder,
    val category : Category?,
    val settings : Settings,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val itemRepo: ItemRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {

    private val _filter   = MutableStateFlow(HomeFilter.ALL)
    private val _sort     = MutableStateFlow(HomeSort.EXPIRY_DATE)
    private val _order    = MutableStateFlow(SortOrder.ASCENDING)
    private val _category = MutableStateFlow<Category?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        _filter, _sort, _order, _category, settingsRepo.settings,
    ) { filter, sort, order, category, settings ->
        HomeParams(filter, sort, order, category, settings)
    }.flatMapLatest { p ->
        val itemsFlow = when (p.filter) {
            HomeFilter.ALL           -> itemRepo.observeNonConsumed()
            HomeFilter.EXPIRING_SOON -> itemRepo.observeExpiringSoon(p.settings.defaultLeadTimeDays)
            HomeFilter.EXPIRED       -> itemRepo.observeExpired()
            HomeFilter.CONSUMED      -> itemRepo.observeConsumed()
        }
        itemsFlow.map { items ->
            HomeUiState(
                items          = items.applySortAndFilter(p),
                filter         = p.filter,
                sort           = p.sort,
                sortOrder      = p.order,
                categoryFilter = p.category,
                settings       = p.settings,
                isLoading      = false,
            )
        }
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun setFilter(filter: HomeFilter) { _filter.value = filter }

    fun setSortAndFilter(sort: HomeSort, order: SortOrder, category: Category?) {
        _sort.value     = sort
        _order.value    = order
        _category.value = category
    }

    fun resetSortAndFilter() {
        _sort.value     = HomeSort.EXPIRY_DATE
        _order.value    = SortOrder.ASCENDING
        _category.value = null
    }
}

private fun List<Item>.applySortAndFilter(p: HomeParams): List<Item> {
    val filtered = if (p.category != null) filter { it.category == p.category } else this
    val catOrder = listOf(Category.FOOD, Category.MEDICINE, Category.COSMETIC, Category.OTHER)
    val comparator: Comparator<Item> = when (p.sort) {
        HomeSort.EXPIRY_DATE -> compareBy { it.expiryDate }
        // Group by type (Food → Medicine → Cosmetic → Other), then soonest expiry within each
        HomeSort.CATEGORY    -> Comparator { a, b ->
            val cc = catOrder.indexOf(a.category).compareTo(catOrder.indexOf(b.category))
            if (cc != 0) cc else a.expiryDate.compareTo(b.expiryDate)
        }
        HomeSort.NAME        -> compareBy { it.name.lowercase() }
        HomeSort.DATE_ADDED  -> compareBy { it.createdAt }
        HomeSort.QUANTITY    -> compareBy { it.qty ?: 0.0 }
    }
    return if (p.order == SortOrder.ASCENDING) filtered.sortedWith(comparator)
           else filtered.sortedWith(comparator.reversed())
}
