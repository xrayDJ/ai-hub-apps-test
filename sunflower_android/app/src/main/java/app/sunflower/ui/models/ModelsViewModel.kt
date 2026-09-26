package app.sunflower.ui.models

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sunflower.data.ModelLibrary
import app.sunflower.data.db.ModelEntity
import app.sunflower.engine.BackendChoice
import app.sunflower.engine.InferenceEngine
import app.sunflower.engine.NotGgufException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModelsState(
    val models: List<ModelEntity> = emptyList(),
    val engine: InferenceEngine.State = InferenceEngine.State.Idle,
    val importing: Boolean = false,
    val importError: String? = null,
    /** Copy progress (0..1) by model id. */
    val copying: Map<String, Float> = emptyMap(),
)

class ModelsViewModel(
    private val library: ModelLibrary,
    private val engine: InferenceEngine,
) : ViewModel() {
    private val importing = MutableStateFlow(false)
    private val importError = MutableStateFlow<String?>(null)
    private val copying = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Model to retry once the user returns from granting "All files access". */
    private var awaitingAccessFor: String? = null

    val state: StateFlow<ModelsState> =
        combine(
            library.observe().catch { emit(emptyList()) },
            engine.state,
            importing,
            importError,
            copying,
        ) { models, engineState, isImporting, error, copies ->
            ModelsState(models, engineState, isImporting, error, copies)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelsState())

    fun import(uri: Uri) {
        viewModelScope.launch {
            importing.value = true
            importError.value = null
            try {
                library.import(uri)
            } catch (e: NotGgufException) {
                importError.value = "That file isn't a GGUF model."
            } catch (e: SecurityException) {
                importError.value = "Sunflower wasn't given lasting access to that file. Try picking it again."
            } catch (e: Exception) {
                importError.value = "Couldn't read that file: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                importing.value = false
            }
        }
    }

    fun requestedFileAccess(model: ModelEntity) {
        awaitingAccessFor = model.id
    }

    /** Called when the screen resumes, e.g. back from the system settings page. */
    fun onResume() {
        val id = awaitingAccessFor ?: return
        if (!library.hasAllFilesAccess()) return
        awaitingAccessFor = null
        viewModelScope.launch { library.get(id)?.let { engine.load(it, BackendChoice.Auto) } }
    }

    fun load(
        model: ModelEntity,
        choice: BackendChoice,
    ) = engine.load(model, choice)

    fun unload() = engine.unload()

    fun remove(model: ModelEntity) {
        viewModelScope.launch {
            if (engine.state.value.modelId == model.id) engine.unload()
            library.remove(model.id)
        }
    }

    /** Copies the file into app storage, then loads it from there. */
    fun copyIntoApp(model: ModelEntity) {
        if (model.id in copying.value) return
        viewModelScope.launch {
            copying.update { it + (model.id to 0f) }
            try {
                var lastStep = -1
                library.copyIntoApp(model.id) { fraction ->
                    val step = (fraction * 100).toInt()
                    if (step != lastStep) {
                        lastStep = step
                        copying.update { it + (model.id to fraction) }
                    }
                }
                library.get(model.id)?.let { engine.load(it, BackendChoice.Auto) }
            } catch (e: Exception) {
                importError.value = "Copy failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                copying.update { it - model.id }
            }
        }
    }
}

/** The model an engine state refers to, if any. */
val InferenceEngine.State.modelId: String?
    get() =
        when (this) {
            InferenceEngine.State.Idle -> null
            is InferenceEngine.State.Loading -> model.id
            is InferenceEngine.State.Ready -> model.id
            is InferenceEngine.State.Failed -> model.id
        }
