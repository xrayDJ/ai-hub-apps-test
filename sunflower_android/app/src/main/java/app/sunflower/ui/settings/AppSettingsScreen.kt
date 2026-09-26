package app.sunflower.ui.settings

import android.app.KeyguardManager
import androidx.annotation.RawRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import app.sunflower.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.sunflower.AppPrefs
import app.sunflower.BuildConfig
import app.sunflower.LockAfter
import app.sunflower.ThemeMode
import app.sunflower.ui.components.ScreenHeader
import app.sunflower.ui.components.SunButton
import app.sunflower.ui.components.SunButtonStyle
import app.sunflower.ui.theme.CodeStyle

@Composable
fun AppSettingsScreen(
    prefs: AppPrefs,
    onUpdate: ((AppPrefs) -> AppPrefs) -> Unit,
    onDeleteAll: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val canLock = remember { context.getSystemService(KeyguardManager::class.java).isDeviceSecure }
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        ScreenHeader("Settings", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            SettingSection("Appearance") {
                ChoiceSetting(
                    "Theme",
                    "Sunflower is designed dark first. System follows your phone's light or dark setting.",
                    options = ThemeMode.entries,
                    selected = prefs.theme,
                    default = ThemeMode.Dark,
                    label = { it.label },
                    onChange = { v -> onUpdate { it.copy(theme = v) } },
                )
            }

            SettingSection("Privacy") {
                ToggleSetting(
                    "App lock",
                    "Asks for your fingerprint, face or screen lock when Sunflower opens. " +
                        "Replies being written keep going while locked." +
                        if (canLock) "" else " Set a screen lock in your phone's settings to use this.",
                    prefs.appLock && canLock,
                    false,
                    { v -> onUpdate { it.copy(appLock = v) } },
                    enabled = canLock,
                )
                AnimatedVisibility(prefs.appLock && canLock) {
                    ChoiceSetting(
                        "Lock again",
                        "How long Sunflower can be in the background before it asks again.",
                        options = LockAfter.entries,
                        selected = prefs.lockAfter,
                        default = LockAfter.Immediately,
                        label = { it.label },
                        onChange = { v -> onUpdate { it.copy(lockAfter = v) } },
                    )
                }
                ToggleSetting(
                    "Hide content",
                    "Shows a blank card in recent apps and blocks screenshots and screen recording inside Sunflower.",
                    prefs.hideContent,
                    false,
                    { v -> onUpdate { it.copy(hideContent = v) } },
                )
            }

            SettingSection("Your data") {
                Text(
                    "Everything stays on this phone. Sunflower has no internet permission, so chats, prompts and models can't leave it. " +
                        "Saved chats are encrypted with a key held in the phone's security hardware, and are never included in backups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                DeleteAll(onDeleteAll)
            }

            SettingSection("About") {
                Text(
                    "Sunflower ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "Runs GGUF language models on-device with the GenieX SDK and llama.cpp.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                Licenses()
            }
        }
    }
}

@Composable
private fun DeleteAll(onDeleteAll: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var confirming by remember { mutableStateOf(false) }
    Column(Modifier.padding(bottom = 8.dp)) {
        if (confirming) {
            Text(
                "Erase all chats, prompts, model settings and copies of models made inside Sunflower? Model files elsewhere on your phone stay. Sunflower restarts afterwards.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SunButton("Cancel", { confirming = false }, style = SunButtonStyle.Ghost)
                SunButton("Erase everything", onDeleteAll, style = SunButtonStyle.Tonal)
            }
        } else {
            SunButton("Delete all data", { confirming = true }, style = SunButtonStyle.Ghost)
        }
    }
}

/** Open-source notices, folded until asked for. Each entry opens its full license text. */
@Composable
private fun Licenses() {
    var open by remember { mutableStateOf(false) }
    SunButton(if (open) "Hide open-source licenses" else "Open-source licenses", { open = !open }, style = SunButtonStyle.Ghost)
    AnimatedVisibility(open) {
        Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LICENSES.forEach { LicenseEntry(it) }
        }
    }
}

@Composable
private fun LicenseEntry(notice: Notice) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val text by produceState<String?>(null, expanded) {
        if (expanded && value == null) {
            value = withContext(Dispatchers.IO) { context.resources.openRawResource(notice.text).bufferedReader().use { it.readText() } }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp),
    ) {
        Text(notice.name, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
        Text(notice.summary, style = CodeStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize), color = colors.onSurfaceVariant)
        AnimatedVisibility(expanded && text != null) {
            Text(
                text.orEmpty().trim(),
                style = CodeStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                color = colors.onSurfaceVariant,
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceContainer)
                        .padding(12.dp),
            )
        }
    }
}

private class Notice(
    val name: String,
    val summary: String,
    @param:RawRes val text: Int,
)

private val LICENSES =
    listOf(
        Notice("GenieX SDK", "BSD 3-Clause License · Copyright (c) 2024-2026 Qualcomm Technologies, Inc.", R.raw.license_geniex),
        Notice("llama.cpp / ggml", "MIT License · Copyright (c) 2023-2026 The ggml authors", R.raw.license_ggml),
        Notice("LLVM OpenMP runtime", "Apache License 2.0 with LLVM Exceptions · The LLVM Project", R.raw.license_openmp),
        Notice("SQLCipher for Android", "BSD 3-Clause License · Copyright (c) 2008-2023 Zetetic LLC. Includes SQLite (public domain).", R.raw.license_sqlcipher),
        Notice(
            "AndroidX, Jetpack Compose, Material 3, Room, Kotlin, kotlinx.coroutines, kotlinx.serialization",
            "Apache License 2.0 · The Android Open Source Project, JetBrains s.r.o.",
            R.raw.license_apache2,
        ),
        Notice("Bricolage Grotesque", "SIL Open Font License 1.1 · Copyright 2022 The Bricolage Grotesque Project Authors", R.raw.license_ofl_bricolage),
        Notice("Inter", "SIL Open Font License 1.1 · Copyright 2020 The Inter Project Authors", R.raw.license_ofl_inter),
        Notice("JetBrains Mono", "SIL Open Font License 1.1 · Copyright 2020 The JetBrains Mono Project Authors", R.raw.license_ofl_jetbrains_mono),
    )
