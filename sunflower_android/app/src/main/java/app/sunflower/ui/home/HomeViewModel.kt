package app.sunflower.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sunflower.data.ConversationRepository
import app.sunflower.data.db.ConversationEntity
import app.sunflower.engine.GenieXRuntime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val conversations: List<ConversationEntity> = emptyList(),
    val loading: Boolean = true,
    val storageError: String? = null,
    val runtime: GenieXRuntime.State = GenieXRuntime.State.Starting,
)

class HomeViewModel(
    private val repository: ConversationRepository,
    runtime: GenieXRuntime,
) : ViewModel() {
    private val conversations =
        repository
            .observeConversations()
            .map { Result.success(it) }
            .catch { emit(Result.failure(it)) }

    val state: StateFlow<HomeState> =
        combine(conversations, runtime.state) { result, runtimeState ->
            HomeState(
                conversations = result.getOrDefault(emptyList()),
                loading = false,
                storageError = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName },
                runtime = runtimeState,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteConversation(id) }
    }
}
