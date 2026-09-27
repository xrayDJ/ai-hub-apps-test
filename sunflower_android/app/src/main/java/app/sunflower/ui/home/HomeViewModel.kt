package app.sunflower.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sunflower.data.ConversationRepository
import app.sunflower.data.SearchHit
import app.sunflower.data.db.ConversationEntity
import app.sunflower.engine.GenieXRuntime
import app.sunflower.engine.InferenceEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flatMapLatest
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
    val engine: InferenceEngine.State = InferenceEngine.State.Idle,
    /** Search text while the search field is open; null when closed. */
    val query: String? = null,
    val results: List<SearchHit> = emptyList(),
) {
    val searching: Boolean get() = !query.isNullOrBlank()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(
    private val repository: ConversationRepository,
    runtime: GenieXRuntime,
    private val engine: InferenceEngine,
) : ViewModel() {
    private val conversations =
        repository
            .observeConversations()
            .map { Result.success(it) }
            .catch { emit(Result.failure(it)) }

    private val query = MutableStateFlow<String?>(null)

    // Re-run the search when chats change, so renames and deletions show at once.
    private val results =
        query
            .debounce { if (it.isNullOrBlank()) 0L else 150L }
            .flatMapLatest { q ->
                if (q.isNullOrBlank()) {
                    flowOf(emptyList())
                } else {
                    repository.observeConversations().mapLatest { repository.search(q) }
                }
            }.catch { emit(emptyList()) }

    val state: StateFlow<HomeState> =
        combine(conversations, runtime.state, engine.state, query, results) { result, runtimeState, engineState, q, hits ->
            HomeState(
                query = q,
                results = if (q.isNullOrBlank()) emptyList() else hits,
                conversations = result.getOrDefault(emptyList()),
                loading = false,
                storageError = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName },
                runtime = runtimeState,
                engine = engineState,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** Opens (empty string) or closes (null) search, or updates its text. */
    fun setQuery(text: String?) {
        query.value = text
    }

    fun rename(
        id: String,
        title: String,
    ) {
        viewModelScope.launch { repository.rename(id, title) }
    }

    fun setPinned(
        id: String,
        pinned: Boolean,
    ) {
        viewModelScope.launch { repository.setPinned(id, pinned) }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            // A reply still being written into this chat would try to save into a deleted conversation.
            if (engine.generation.value?.conversationId == id) engine.stop()
            repository.deleteConversation(id)
        }
    }
}
