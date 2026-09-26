package app.sunflower

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.sunflower.engine.GenerationService
import app.sunflower.ui.navigation.SunflowerNavHost
import app.sunflower.ui.theme.SunflowerTheme

class MainActivity : ComponentActivity() {
    /** A chat to open, e.g. from the "writing a reply" notification. Consumed once handled. */
    private val pendingChat = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) pendingChat.value = intent.chatId()
        setContent {
            SunflowerTheme {
                SunflowerNavHost(
                    openChat = pendingChat.value,
                    onOpenedChat = { pendingChat.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.chatId()?.let { pendingChat.value = it }
    }

    private fun Intent.chatId(): String? = getStringExtra(GenerationService.EXTRA_CONVERSATION_ID)
}
