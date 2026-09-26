package app.sunflower.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import app.sunflower.MainActivity
import app.sunflower.R
import app.sunflower.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while a reply is being written, so leaving the app
 * or turning the screen off doesn't cut it short. Runs only for the length of
 * a reply. The notification never shows the conversation's content.
 */
class GenerationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val engine = appContainer.engine
        if (intent?.action == ACTION_STOP) {
            scope.launch { engine.stop() }
            return START_NOT_STICKY
        }
        val conversationId = intent?.getStringExtra(EXTRA_CONVERSATION_ID)
        startForeground(NOTIFICATION_ID, notification(conversationId), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (!started) {
            started = true
            wakeLock =
                getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sunflower:reply")
                    .apply { acquire(MAX_REPLY_MS) }
            scope.launch {
                // Stop as soon as no reply is being written.
                engine.generation.map { it != null }.distinctUntilChanged().first { !it }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(conversationId: String?): Notification {
        ensureChannel(this)
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_CONVERSATION_ID, conversationId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, GenerationService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Writing a reply…")
            .setContentText("Running on this phone")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversationId"
        private const val ACTION_STOP = "app.sunflower.STOP_REPLY"
        private const val CHANNEL_ID = "replies"
        private const val NOTIFICATION_ID = 1
        private const val MAX_REPLY_MS = 30 * 60 * 1000L

        /** Called when a reply starts. Failing to start just means no background protection. */
        fun start(
            context: Context,
            conversationId: String,
        ) {
            try {
                context.startForegroundService(
                    Intent(context, GenerationService::class.java).putExtra(EXTRA_CONVERSATION_ID, conversationId),
                )
            } catch (e: Exception) {
                Log.w("GenerationService", "Couldn't start background protection: ${e.message}")
            }
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Replies in progress", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Shown while Sunflower finishes a reply in the background."
                        setShowBadge(false)
                    },
                )
            }
        }
    }
}
