package app.sunflower

import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.sunflower.engine.GenerationService
import app.sunflower.ui.LockScreen
import app.sunflower.ui.navigation.SunflowerNavHost
import app.sunflower.ui.theme.Motion
import app.sunflower.ui.theme.SunflowerTheme

class MainActivity : ComponentActivity() {
    /** A chat to open, e.g. from the "writing a reply" notification. Consumed once handled. */
    private val pendingChat = mutableStateOf<String?>(null)

    /** App lock: true while content must stay hidden behind the unlock screen. */
    private val locked = mutableStateOf(false)
    private var backgroundedAt = 0L
    private var authenticating = false

    private val preferences get() = appContainer.preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            pendingChat.value = intent.chatId()
            locked.value = preferences.state.value.appLock
        }
        setContent {
            val prefs by preferences.state.collectAsStateWithLifecycle()
            val dark =
                when (prefs.theme) {
                    ThemeMode.Dark -> true
                    ThemeMode.Light -> false
                    ThemeMode.System -> isSystemInDarkTheme()
                }
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            LaunchedEffect(prefs.hideContent) {
                // Blank in recents, no screenshots or screen recording.
                if (prefs.hideContent) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            SunflowerTheme(dark = dark) {
                val isLocked by locked
                if (!isLocked) {
                    SunflowerNavHost(
                        openChat = pendingChat.value,
                        onOpenedChat = { pendingChat.value = null },
                    )
                }
                AnimatedVisibility(isLocked, enter = fadeIn(Motion.enter()), exit = fadeOut(Motion.exit())) {
                    LockScreen(onUnlock = ::authenticate)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val prefs = preferences.state.value
        if (prefs.appLock && backgroundedAt > 0 && SystemClock.elapsedRealtime() - backgroundedAt >= prefs.lockAfter.millis) {
            locked.value = true
        }
        if (locked.value) authenticate()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !authenticating) backgroundedAt = SystemClock.elapsedRealtime()
    }

    /** Fingerprint, face, or the phone's own PIN/pattern/password. */
    private fun authenticate() {
        if (authenticating) return
        // Without a screen lock there is nothing to check against; don't lock the user out.
        if (!getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            locked.value = false
            return
        }
        authenticating = true
        BiometricPrompt
            .Builder(this)
            .setTitle("Unlock Sunflower")
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
            .build()
            .authenticate(
                CancellationSignal(),
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        authenticating = false
                        locked.value = false
                        backgroundedAt = 0
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        authenticating = false
                    }
                },
            )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.chatId()?.let { pendingChat.value = it }
    }

    private fun Intent.chatId(): String? = getStringExtra(GenerationService.EXTRA_CONVERSATION_ID)
}
