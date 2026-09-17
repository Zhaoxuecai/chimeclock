package com.example.chimeclock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * ChimeService — 前台服务，注册并续订下一个刻钟闹钟
 *
 * 职责：
 * 1. 前台服务常驻通知
 * 2. 注册下一个刻钟的一次性闹钟
 * 3. 接收播放指令，调用 ChimePlayer 播放
 *
 * 执行纪律 #1: 闹钟必须一次性 + 续订
 * 执行纪律 #5: 每任务完成即自测留痕
 */
class ChimeService : Service() {

    companion object {
        private const val TAG = "ChimeService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "chime_service_channel"
        const val ACTION_PLAY_CHIME = "com.example.chimeclock.PLAY_CHIME"
        const val EXTRA_CHIME_COUNT = "chime_count"
        private const val EXTRA_CHIME_COUNT_PREVIEW = "chime_count_preview"
    }

    private var chimePlayer: ChimePlayer? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ChimeService onCreate")

        chimePlayer = ChimePlayer(this)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "ChimeService onStartCommand: action=${intent?.action}")

        // 启动前台服务
        startForegroundCompat()

        when (intent?.action) {
            ACTION_PLAY_CHIME -> {
                val count = intent.getIntExtra(EXTRA_CHIME_COUNT, 0)
                if (count > 0) {
                    chimePlayer?.playChimes(count)
                }
            }
            null -> {
                // 服务被系统重启，重新注册闹钟
                Log.i(TAG, "服务重启，重新注册闹钟")
            }
        }

        // 注册下一个刻钟闹钟
        AlarmReceiver.scheduleNextChime(this)

        // 如果服务被杀，系统会尝试重启（START_STICKY）
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "ChimeService onDestroy")
        chimePlayer?.release()
        chimePlayer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 启动前台服务通知
     */
    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+ 需要指定 foregroundServiceType
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
