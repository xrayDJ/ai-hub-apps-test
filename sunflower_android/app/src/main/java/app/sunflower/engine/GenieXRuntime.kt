package app.sunflower.engine

import android.content.Context
import android.util.Log
import com.geniex.sdk.GenieXSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Brings up the GenieX native runtime (llama.cpp, Hexagon NPU and OpenCL
 * backends) once per process. Model loading and generation build on this.
 */
class GenieXRuntime(
    context: Context,
    scope: CoroutineScope,
) {
    sealed interface State {
        data object Starting : State

        data object Ready : State

        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Starting)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            try {
                GenieXSdk.getInstance().init(
                    appContext,
                    object : GenieXSdk.InitCallback {
                        override fun onSuccess() {
                            _state.value = State.Ready
                        }

                        override fun onFailure(reason: String) {
                            Log.e(TAG, "GenieX init failed: $reason")
                            _state.value = State.Failed(reason)
                        }
                    },
                )
            } catch (t: Throwable) {
                // UnsatisfiedLinkError and friends: surface instead of crashing the app.
                Log.e(TAG, "GenieX init threw", t)
                _state.value = State.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    private companion object {
        const val TAG = "GenieXRuntime"
    }
}
