package app.sunflower.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.sunflower.appContainer
import app.sunflower.ui.chat.ChatScreen
import app.sunflower.ui.chat.ChatViewModel
import app.sunflower.ui.home.HomeScreen
import app.sunflower.ui.home.HomeViewModel
import app.sunflower.ui.models.ModelsScreen
import app.sunflower.ui.models.ModelsViewModel
import app.sunflower.ui.theme.Motion
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object ModelsRoute

/** A null id opens a fresh chat that is saved on its first message. */
@Serializable
data class ChatRoute(val conversationId: String? = null)

@Composable
fun SunflowerNavHost() {
    val nav = rememberNavController()
    val container = LocalContext.current.appContainer

    NavHost(
        navController = nav,
        startDestination = HomeRoute,
        enterTransition = { slideIntoContainer(SlideDirection.Start, Motion.slide) { it / 5 } + fadeIn(Motion.enter()) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start, Motion.slide) { it / 10 } + fadeOut(Motion.exit()) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End, Motion.slide) { it / 10 } + fadeIn(Motion.enter()) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, Motion.slide) { it / 5 } + fadeOut(Motion.exit()) },
    ) {
        composable<HomeRoute> {
            val vm = viewModel { HomeViewModel(container.conversations, container.runtime, container.engine) }
            val state by vm.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onNewChat = { nav.navigate(ChatRoute()) },
                onOpenChat = { nav.navigate(ChatRoute(it)) },
                onOpenModels = { nav.navigate(ModelsRoute) },
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
            )
        }
        composable<ChatRoute> { entry ->
            val route = entry.toRoute<ChatRoute>()
            val vm = viewModel { ChatViewModel(route.conversationId, container.conversations, container.engine) }
            val state by vm.state.collectAsStateWithLifecycle()
            ChatScreen(
                state = state,
                onSend = vm::send,
                onStop = vm::stop,
                onRetry = vm::retry,
                onSystemPromptChange = vm::setSystemPrompt,
                onBack = { nav.popBackStack() },
                onOpenModels = { nav.navigate(ModelsRoute) },
            )
        }
    }
}
