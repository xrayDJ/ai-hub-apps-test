package app.sunflower

import android.content.Context
import app.sunflower.data.ConversationRepository
import app.sunflower.data.ModelLibrary
import app.sunflower.data.PromptLibrary
import app.sunflower.data.db.SunflowerDatabase
import app.sunflower.engine.GenieXRuntime
import app.sunflower.engine.InferenceEngine
import app.sunflower.security.KeyVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/** Process-wide singletons, wired by hand to keep the dependency graph obvious. */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val keyVault = KeyVault(context)

    // Unwrapping the key touches the hardware keystore, so it never runs on the main thread.
    private val database = appScope.async(Dispatchers.IO) { SunflowerDatabase.open(context, keyVault) }

    val conversations = ConversationRepository { database.await() }

    val models = ModelLibrary(context) { database.await() }

    val prompts = PromptLibrary { database.await() }

    val runtime = GenieXRuntime(context, appScope)

    val engine = InferenceEngine(context, runtime, models, conversations, appScope)

    private val appContext = context.applicationContext

    /**
     * Last resort when the encrypted database can't be opened (e.g. its key was
     * lost): delete the database, its key and imported-model records, then
     * restart. Model files the user keeps elsewhere are untouched.
     */
    fun wipeAndRestart() {
        runCatching { engine.unload() }
        keyVault.destroy()
        appContext.deleteDatabase("sunflower.db")
        java.io.File(appContext.filesDir, "models").deleteRecursively()
        appContext.getSharedPreferences("engine", Context.MODE_PRIVATE).edit().clear().commit()
        appContext.getSharedPreferences("crash_reports", Context.MODE_PRIVATE).edit().clear().commit()
        val launch =
            appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
                ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        launch?.let { appContext.startActivity(it) }
        Runtime.getRuntime().exit(0)
    }
}
