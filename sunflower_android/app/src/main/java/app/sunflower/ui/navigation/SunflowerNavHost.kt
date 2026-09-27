package app.sunflower.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import app.sunflower.appContainer
import app.sunflower.ui.chat.ChatScreen
import app.sunflower.ui.chat.ChatViewModel
import app.sunflower.ui.components.LocalScreenAnimation
import app.sunflower.ui.components.LocalSharedTransition
import app.sunflower.ui.home.HomeScreen
import app.sunflower.ui.home.HomeViewModel
import app.sunflower.ui.models.ModelsScreen
import app.sunflower.ui.models.ModelsViewModel
import app.sunflower.ui.settings.AppSettingsScreen
import app.sunflower.ui.settings.ModelSettingsScreen
import app.sunflower.ui.settings.ModelSettingsViewModel
import app.sunflower.ui.theme.Motion
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object ModelsRoute

@Serializable
object AppSettingsRoute

/** Settings for one model; [conversationId] lets the screen show that chat's system prompt budget. */
@Serializable
data class ModelSettingsRoute(val modelId: String, val conversationId: String? = null)

/** A null id opens a fresh chat that is saved on its first message. */
@Serializable
data class ChatRoute(
    val conversationId: String? = null,
    /** A message to bring into view on opening, e.g. from search. */
    val messageId: String? = null,
)

private fun NavBackStackEntry.isHome() = destination.hasRoute<HomeRoute>()

private fun NavBackStackEntry.isChat() = destination.hasRoute<ChatRoute>()

/** Home and a chat hand over through the chat's shared bounds: the home screen only recedes. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.homeAndChat() =
    (initialState.isHome() && targetState.isChat()) || (initialState.isChat() && targetState.isHome())

/** Gives a screen access to its own enter/exit animation, for shared bounds. */
@Composable
private fun AnimatedContentScope.Screen(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalScreenAnimation provides this, content = content)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SunflowerNavHost(
    openChat: String? = null,
    onOpenedChat: () -> Unit = {},
) {
    val nav = rememberNavController()
    val container = LocalContext.current.appContainer

    LaunchedEffect(openChat) {
        if (openChat != null) {
            val entry = nav.currentBackStackEntry
            val showing = entry?.destination?.hasRoute<ChatRoute>() == true && entry.toRoute<ChatRoute>().conversationId == openChat
            if (!showing) nav.navigate(ChatRoute(openChat)) { launchSingleTop = true }
            onOpenedChat()
        }
    }

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransition provides this) {
            NavHost(
                navController = nav,
                startDestination = HomeRoute,
                enterTransition = {
                    when {
                        homeAndChat() -> EnterTransition.None
                        else -> slideIntoContainer(SlideDirection.Start, Motion.slide) { it / 5 } + fadeIn(Motion.enter())
                    }
                },
                exitTransition = {
                    when {
                        homeAndChat() -> fadeOut(tween(durationMillis = 260, delayMillis = 40))
                        else -> slideOutOfContainer(SlideDirection.Start, Motion.slide) { it / 10 } + fadeOut(Motion.exit())
                    }
                },
                popEnterTransition = {
                    when {
                        homeAndChat() -> fadeIn(tween(durationMillis = 300, delayMillis = 60))
                        else -> slideIntoContainer(SlideDirection.End, Motion.slide) { it / 10 } + fadeIn(Motion.enter())
                    }
                },
                popExitTransition = {
                    when {
                        homeAndChat() -> ExitTransition.None
                        else -> slideOutOfContainer(SlideDirection.End, Motion.slide) { it / 5 } + fadeOut(Motion.exit())
                    }
                },
            ) {
                composable<HomeRoute> { Screen {
                    val vm = viewModel { HomeViewModel(container.conversations, container.runtime, container.engine) }
                    val state by vm.state.collectAsStateWithLifecycle()
                    HomeScreen(
                        state = state,
                        onNewChat = { nav.navigate(ChatRoute()) },
                        onOpenChat = { id, messageId -> nav.navigate(ChatRoute(id, messageId)) },
                        onOpenModels = { nav.navigate(ModelsRoute) },
                        onDelete = vm::delete,
                        onRename = vm::rename,
                        onSetPinned = vm::setPinned,
                        onQueryChange = vm::setQuery,
                        onOpenSettings = { nav.navigate(AppSettingsRoute) },
                        onResetStorage = container::wipeAndRestart,
                    )
                } }
                composable<AppSettingsRoute> {
                    val prefs by container.preferences.state.collectAsStateWithLifecycle()
                    AppSettingsScreen(
                        prefs = prefs,
                        onUpdate = container.preferences::update,
                        onDeleteAll = container::wipeAndRestart,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable<ModelsRoute> {
                    val vm = viewModel { ModelsViewModel(container.models, container.engine) }
                    val state by vm.state.collectAsStateWithLifecycle()
                    ModelsScreen(
                        state = state,
                        onBack = { nav.popBackStack() },
                        onImport = vm::import,
                        onLoad = vm::load,
                        onUnload = vm::unload,
                        onRemove = vm::remove,
                        onCopyIntoApp = vm::copyIntoApp,
                        onRequestFileAccess = vm::requestedFileAccess,
                        onSetBackend = vm::setBackend,
                        onOpenSettings = { nav.navigate(ModelSettingsRoute(it.id)) },
                        onResume = vm::onResume,
                        onDismissCrash = vm::dismissCrash,
                        onRetryCrashedBackends = vm::retryCrashedBackends,
                        onLoadAnyway = vm::loadAnyway,
                        onRelink = vm::relink,
                        onCancelCopy = vm::cancelCopy,
                    )
                }
                composable<ChatRoute> { entry -> Screen {
                    val route = entry.toRoute<ChatRoute>()
                    val prefs by container.preferences.state.collectAsStateWithLifecycle()
                    val vm = viewModel { ChatViewModel(route.conversationId, container.conversations, container.engine, container.prompts, container.models) }
                    val state by vm.state.collectAsStateWithLifecycle()
                    ChatScreen(
                        state = state,
                        onSend = vm::send,
                        onStop = vm::stop,
                        onRetry = vm::retry,
                        onRegenerate = vm::regenerate,
                        onEdit = vm::sendEdit,
                        onLoadChatModel = vm::loadChatModel,
                        onKeepCurrentModel = vm::keepCurrentModel,
                        onToggleThinking = vm::toggleThinking,
                        onRename = vm::rename,
                        onVersion = vm::showVersion,
                        focusMessageId = route.messageId,
                        onSystemPromptChange = vm::setSystemPrompt,
                        onBack = { nav.popBackStack() },
                        onOpenModels = { nav.navigate(ModelsRoute) },
                        onOpenSettings = { modelId -> nav.navigate(ModelSettingsRoute(modelId, vm.savedConversationId)) },
                        promptActions = vm.promptActions,
                        // A chat started from "New chat" shrinks back into its new row once saved.
                        boundsKey = vm.savedConversationId ?: route.conversationId ?: "new",
                        messageScale = prefs.chatScale / 100f,
                    )
                } }
                composable<ModelSettingsRoute> { entry ->
                    val route = entry.toRoute<ModelSettingsRoute>()
                    val context = LocalContext.current
                    val vm = viewModel { ModelSettingsViewModel(route.modelId, container.models, container.engine, context) }
                    val state by vm.state.collectAsStateWithLifecycle()
                    val systemPrompt by produceState<String?>(null, route.conversationId) {
                        value = route.conversationId?.let { container.conversations.conversation(it)?.systemPrompt }
                    }
                    ModelSettingsScreen(
                        state = state,
                        systemPrompt = systemPrompt,
                        onUpdate = vm::update,
                        onReload = vm::reload,
                        onRefreshMemory = vm::refreshMemory,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}
