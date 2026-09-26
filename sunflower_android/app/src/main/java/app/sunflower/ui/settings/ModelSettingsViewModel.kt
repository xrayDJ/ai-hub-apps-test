package app.sunflower.ui.settings

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sunflower.data.ModelLibrary
import app.sunflower.data.db.ModelEntity
import app.sunflower.engine.Backend
import app.sunflower.engine.GgufInfo
import app.sunflower.engine.InferenceEngine
import app.sunflower.engine.ModelSettings
import app.sunflower.engine.ResolvedLoad
import app.sunflower.engine.changesTo
import app.sunflower.engine.resolve
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Memory(val totalBytes: Long, val availableBytes: Long)

data class ModelSettingsState(
    val model: ModelEntity? = null,
    val settings: ModelSettings = ModelSettings(),
    val info: GgufInfo? = null,
    val engine: InferenceEngine.State = InferenceEngine.State.Idle,
    /** Other imported models, for picking a speculative-decoding draft. */
    val otherModels: List<ModelEntity> = emptyList(),
    val memory: Memory = Memory(0, 0),
    val cores: Int = Runtime.getRuntime().availableProcessors(),
) {
    val loaded: InferenceEngine.State.Ready?
        get() = (engine as? InferenceEngine.State.Ready)?.takeIf { it.model.id == model?.id }

    val busy: Boolean get() = engine is InferenceEngine.State.Loading

    /** The backend these settings would load on: the chosen one, or what Auto last settled on. */
    val targetBackend: Backend
        get() =
            Backend.fromUnit(settings.load.backend)
                ?: loaded?.backend
                ?: Backend.fromUnit(model?.lastBackend)
                ?: Backend.NPU

    val resolved: ResolvedLoad
        get() = settings.load.resolve(targetBackend, model?.contextLength, cores)

    /** Loading settings that differ from what the running model was loaded with. */
    val pendingChanges: List<String>
        get() = loaded?.applied?.changesTo(resolved).orEmpty()
}

class ModelSettingsViewModel(
    private val modelId: String,
    private val library: ModelLibrary,
    private val engine: InferenceEngine,
    context: Context,
) : ViewModel() {
    private val appContext = context.applicationContext

    // Edits show instantly; writes to the encrypted database are coalesced.
    private val edits = MutableStateFlow<ModelSettings?>(null)
    private val info = MutableStateFlow<GgufInfo?>(null)
    private val memory = MutableStateFlow(readMemory())
    private var saveJob: Job? = null

    /** Latest edit not yet written; viewModelScope is already cancelled when onCleared runs. */
    @Volatile private var unsaved: ModelSettings? = null

    private val models = library.observe().catch { emit(emptyList()) }

    val state: StateFlow<ModelSettingsState> =
        combine(models, edits, info, engine.state, memory) { all, edited, gguf, engineState, mem ->
            val model = all.firstOrNull { it.id == modelId }
            ModelSettingsState(
                model = model,
                settings = edited ?: model?.let { library.settingsOf(it) } ?: ModelSettings(),
                info = gguf,
                engine = engineState,
                otherModels = all.filter { it.id != modelId },
                memory = mem,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelSettingsState())

    init {
        viewModelScope.launch {
            val model = library.get(modelId) ?: return@launch
            info.value = library.info(model)
        }
    }

    fun update(transform: (ModelSettings) -> ModelSettings) {
        val next = transform(state.value.settings)
        edits.value = next
        unsaved = next
        saveJob?.cancel()
        saveJob =
            viewModelScope.launch {
                delay(300)
                library.updateSettings(modelId, next)
                if (unsaved === next) unsaved = null
            }
    }

    /** Saves immediately and (re)loads the model with the new settings. */
    fun reload() {
        viewModelScope.launch {
            saveJob?.cancel()
            edits.value?.let { library.updateSettings(modelId, it) }
            unsaved = null
            library.get(modelId)?.let { engine.load(it) }
        }
    }

    fun refreshMemory() {
        memory.value = readMemory()
    }

    private fun readMemory(): Memory {
        val am = appContext.getSystemService(ActivityManager::class.java) ?: return Memory(0, 0)
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return Memory(info.totalMem, info.availMem)
    }

    override fun onCleared() {
        // Don't lose the last edit if the screen closes inside the save delay.
        unsaved?.let { pending -> CoroutineScope(Dispatchers.IO).launch { library.updateSettings(modelId, pending) } }
    }
}
