package com.hidble.phonekeyboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 大模型输出期间的前台保活服务：
 * - 前台通知“大模型输出中…”，避免切后台/锁屏时进程被系统回收；
 * - 持有一把 PARTIAL_WAKE_LOCK，锁屏后 CPU 仍继续跑，流式输出能一直跑到结束。
 *
 * 生命周期：请求开始(start) → 请求正常结束/被“停止输出”/页面销毁(stop)。
 * 网络本身仍跑在主界面的协程里，本服务只负责“保活+保持CPU”，不做网络收发。
 */
class LlmRunService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "大模型输出",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "模型正在回复时显示，避免切后台被系统中断"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("大模型输出中")
            .setContentText("正在接收模型回复，切换后台也会继续，可随时回来查看")
            .setOngoing(true)
            .setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "phonekeyboard:llmrun")
            lock.setReferenceCounted(false)
            lock.acquire(15 * 60 * 1000L) // 兜底：最长持锁 15 分钟，避免异常时永远锁屏耗电
            wakeLock = lock
        } catch (e: Exception) {
            // 拿不到唤醒锁就只靠前台服务保活
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
        }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "llm_run"
        private const val NOTIFICATION_ID = 1002
        @Volatile
        private var wakeLock: PowerManager.WakeLock? = null

        /** 启动保活服务（从 Activity 前台调用，切后台后仍继续跑） */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, LlmRunService::class.java))
            } catch (e: Exception) {
                // 个别 ROM/系统限制时忽略，不影响正常聊天
            }
        }

        /** 结束保活服务：请求完成/停止/页面销毁时调用 */
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, LlmRunService::class.java))
            } catch (e: Exception) {
            }
        }
    }
}