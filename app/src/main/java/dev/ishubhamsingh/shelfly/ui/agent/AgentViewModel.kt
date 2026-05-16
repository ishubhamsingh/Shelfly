package dev.ishubhamsingh.shelfly.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ishubhamsingh.shelfly.agent.AgentTurn
import dev.ishubhamsingh.shelfly.agent.GemmaAgent
import dev.ishubhamsingh.shelfly.agent.GemmaModelManager
import dev.ishubhamsingh.shelfly.agent.ModelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AgentUiState {
    data object Idle    : AgentUiState
    data object Loading : AgentUiState
    data class  Result(val reply: String) : AgentUiState
    data class  Error(val reason: String) : AgentUiState
}

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val agent: GemmaAgent,
    modelManager: GemmaModelManager,
) : ViewModel() {

    val modelState: StateFlow<ModelState> = modelManager.state.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = ModelState.NotDownloaded,
    )

    private val _uiState = MutableStateFlow<AgentUiState>(AgentUiState.Idle)

    init {
        // Pre-load the engine as soon as the model file is ready so the first
        // inference doesn't pay the cold-start cost while the sheet is visible.
        viewModelScope.launch {
            modelState
                .filter { it is ModelState.Ready }
                .first()
            agent.warmUp()
        }
    }
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    fun run(input: String, imageBytes: ByteArray? = null) {
        if (input.isBlank() && imageBytes == null) return
        _uiState.value = AgentUiState.Loading
        viewModelScope.launch {
            val turn = agent.run(input, imageBytes)
            _uiState.value = when (turn) {
                is AgentTurn.ToolCall -> AgentUiState.Result(turn.reply)
                is AgentTurn.Reply    -> AgentUiState.Result(turn.text)
                is AgentTurn.Failure  -> AgentUiState.Error(turn.reason)
            }
        }
    }

    fun reset() { _uiState.value = AgentUiState.Idle }
}
