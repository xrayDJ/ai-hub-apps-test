package app.sunflower.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sunflower.ui.components.SunButton
import app.sunflower.ui.components.SunIcons
import app.sunflower.ui.components.SunflowerMark

/** Covers everything while the app is locked. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SunflowerMark(size = 84.dp)
            Text("Sunflower is locked", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            SunButton("Unlock", onUnlock, icon = SunIcons.Lock)
        }
    }
}
