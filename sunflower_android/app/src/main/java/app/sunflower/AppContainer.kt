package app.sunflower

import android.content.Context
import app.sunflower.data.ConversationRepository
import app.sunflower.data.db.SunflowerDatabase
import app.sunflower.engine.GenieXRuntime
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

    val runtime = GenieXRuntime(context, appScope)
}
