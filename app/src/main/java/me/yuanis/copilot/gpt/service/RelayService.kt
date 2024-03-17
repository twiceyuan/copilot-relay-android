package me.yuanis.copilot.gpt.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.ktor.server.engine.ApplicationEngine
import me.yuanis.copilot.gpt.service.data.RelayRepo
import me.yuanis.copilot.gpt.service.server.ServerStatus
import me.yuanis.copilot.gpt.service.server.startServer
import me.yuanis.copilot.gpt.service.ui.MainActivity

/**
 * Relay service
 *
 * This service is responsible for starting the Ktor server. It also binds a foreground notification
 */
class RelayService : Service() {

    // Ktor server engine
    private var engine: ApplicationEngine? = null

    override fun onCreate() {
        super.onCreate()
        engine = startServer()
        bindForegroundNotification()
        _isServerRunning.value = ServerStatus.Running(RelayRepo.getServerPort())
    }

    private fun bindForegroundNotification() {
        val channelId = "default"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Default Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setContentTitle(getString(R.string.foreground_notification_title))
            .setContentText(
                getString(
                    R.string.server_is_running_status,
                    RelayRepo.getServerPort().toString()
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
        startForegroundCompat(FORE_NOTIFICATION_ID, notificationBuilder.build())
    }

    // Bind a foreground service
    @Suppress("SameParameterValue")
    private fun startForegroundCompat(notificationId: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServerRunning.value = ServerStatus.Stopped
        engine?.stop()
    }

    companion object {
        // Notification ID
        private const val FORE_NOTIFICATION_ID = 1001

        // Server status
        private val _isServerRunning = MutableLiveData<ServerStatus>(ServerStatus.Stopped)

        // Expose the server status as LiveData
        val isServerRunning: LiveData<ServerStatus>
            get() = _isServerRunning

        // Start the server failed fallback to stopped status
        fun postStartFailed() {
            _isServerRunning.value = ServerStatus.Stopped
        }
    }
}
