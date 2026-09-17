package com.example.chimeclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BootReceiver — 开机自启恢复服务
 *
 * 执行纪律 #7: 国产 ROM 查杀为最大风险，开机自启恢复
 *
 * 监听 BOOT_COMPLETED + QUICKBOOT_POWERON 广播
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "收到广播: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // 检查主开关是否开启
            val prefs = context.getSharedPreferences("chime_prefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("chime_enabled", false)

            if (isEnabled) {
                Log.i(TAG, "主开关已开启，恢复报时服务")
                val serviceIntent = Intent(context, ChimeService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                // Toast 提示（通过 Service 不行，用 Handler.post）
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.boot_toast),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Log.i(TAG, "主开关未开启，不恢复服务")
            }
        }
    }
}
