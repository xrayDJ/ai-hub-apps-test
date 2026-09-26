package app.sunflower.ui.home

import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sunflower.data.db.ConversationEntity
import app.sunflower.engine.GenieXRuntime
import app.sunflower.ui.components.SunButton
import app.sunflower.ui.components.SunButtonStyle
import app.sunflower.ui.components.SunIconButton
import app.sunflower.ui.components.SunIcons
import app.sunflower.ui.components.SunflowerMark
import app.sunflower.ui.components.pressScale
import app.sunflower.ui.theme.Motion
import app.sunflower.ui.theme.SunflowerTheme

@Composable
fun HomeScreen(
    state: HomeState,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenModels: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // A warm glow bleeding in from the top corner: the one flourish on an otherwise quiet screen.
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.radialGradient(
                        listOf(SunflowerTheme.extras.glow, Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(900f, -120f),
                        radius = 1100f,
                    ),
                ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Header(onOpenModels, Modifier.statusBarsPadding()) }
            item { ModelCard(state.runtime, onOpenModels, Modifier.padding(top = 24.dp)) }
            if (state.storageError != null) {
                item { Notice("Couldn't open saved chats: ${state.storageError}") }
            } else if (state.conversations.isNotEmpty()) {
                item {
                    Text(
                        "Recent",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onBackground,
                        modifier = Modifier.padding(top = 26.dp, bottom = 4.dp),
                    )
                }
                items(state.conversations, key = { it.id }) { conversation ->
                    ConversationRow(conversation, { onOpenChat(conversation.id) }, Modifier.animateItem())
                }
            }
        }

        SunButton(
            text = "New chat",
            icon = Icons.Filled.Add,
            onClick = onNewChat,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
        )
    }
}

@Composable
private fun Header(
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SunflowerMark(size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Text("sunflower", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.weight(1f))
        SunIconButton(SunIcons.Layers, "Models", onOpenModels)
    }
}

@Composable
private fun ModelCard(
    runtime: GenieXRuntime.State,
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val status =
        when (runtime) {
            GenieXRuntime.State.Starting -> "Starting"
            GenieXRuntime.State.Ready -> "No model loaded"
            is GenieXRuntime.State.Failed -> "Runtime unavailable"
        }
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.extraLarge)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(ok = runtime == GenieXRuntime.State.Ready, busy = runtime == GenieXRuntime.State.Starting)
            Spacer(Modifier.width(10.dp))
            Text(status, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
            SunButton("Models", onOpenModels, style = SunButtonStyle.Ghost)
        }
        if (runtime is GenieXRuntime.State.Failed) {
            Text(
                runtime.reason,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun StatusDot(
    ok: Boolean,
    busy: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val alpha by animateFloatAsState(if (busy) 0.4f else 1f, Motion.snappy(), label = "dot")
    Box(
        Modifier
            .size(10.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(if (ok) colors.tertiary else colors.primary),
    )
}

@Composable
private fun ConversationRow(
    conversation: ConversationEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(source, 0.98f)
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .clickable(source, ripple(), onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                conversation.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                conversation.modelName ?: "No model yet",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            DateUtils.getRelativeTimeSpanString(conversation.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun Notice(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SunIcons.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
