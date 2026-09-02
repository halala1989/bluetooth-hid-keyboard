package com.hidble.phonekeyboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * 前台服务：键盘注册期间保持 App 处于“前台”地位。
 *
 * Android 官方文档规定：BluetoothHidDevice 注册的 App 如果不是前台状态，
 * 系统会自动注销注册并断开连接（切子页面/切后台都会触发）。
 * 前台服务能让进程/UID 保持前台，从而维持蓝牙键盘连接不断。
 */
class HidKeyboardService : Service() {

    companion object {
        private const val CHANNEL_ID = "hid_keyboard"
        private const val NOTIF_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("手机蓝牙键盘运行中")
            .setContentText("正在为电脑提供蓝牙键盘输入，后台连接保持中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
        // 进程被杀后不自动复活：避免出现“通知在但键盘未注册”的假象
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "蓝牙键盘连接", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}
