package com.example.chimeclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * AlarmReceiver — 收闹钟广播 → 算响数 → 播放 → 续订下一轮
 *
 * 执行纪律 #1: 闹钟必须一次性 + 续订，禁用 setRepeating
 *
 * 响数判定：when(minute) { 0 -> 3; 30 -> 2; 15, 45 -> 1; else -> 0 }
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val ACTION_CHIME = "com.example.chimeclock.ACTION_CHIME"
        private const val EXTRA_PREVIEW_COUNT = "preview_count"
        private const val EXTRA_IS_PREVIEW = "is_preview"
        private const val ALARM_REQUEST_CODE = 10001

        /**
         * 计算响数
         * @param minute 分钟数
         * @return 响数：3=整点, 2=半点, 1=一刻/三刻, 0=其他(不触发)
         */
        fun getChimeCount(minute: Int): Int {
            return when (minute) {
                0 -> 3
                30 -> 2
                15, 45 -> 1
                else -> 0
            }
        }

        /**
         * 计算下一个刻钟的 Calendar
         */
        fun getNextQuarterCalendar(): Calendar {
            return Calendar.getInstance().apply {
                val currentMinute = get(Calendar.MINUTE)
                val minutesToAdd = 15 - (currentMinute % 15)
                if (minutesToAdd == 0) {
                    // 正好在刻钟点上，加 15 分钟到下一个
                    add(Calendar.MINUTE, 15)
                } else {
                    add(Calendar.MINUTE, minutesToAdd)
                }
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }

        /**
         * 注册下一个刻钟的一次性闹钟
         * 执行纪律 #1: 使用 setExactAndAllowWhileIdle，禁用 setRepeating
         */
        fun scheduleNextChime(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val nextQuarter = getNextQuarterCalendar()
            val triggerAtMillis = nextQuarter.timeInMillis

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_CHIME
                putExtra(EXTRA_IS_PREVIEW, false)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 执行纪律 #1: 一次性闹钟 + 续订
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+ 检查精确闹钟权限
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.i(TAG, "精确闹钟已注册: ${nextQuarter.time}")
                } else {
                    // 执行纪律 #3: 权限兜底降级——降级为非精确触发，不静默失效
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.w(TAG, "精确闹钟权限缺失，降级为非精确闹钟: ${nextQuarter.time}")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.i(TAG, "闹钟已注册(API<31): ${nextQuarter.time}")
            }
        }

        /**
         * 取消已注册的闹钟
         */
        fun cancelNextChime(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_CHIME
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.i(TAG, "闹钟已取消")
        }

        /**
         * 发送试听广播（应用内试听）
         */
        fun sendPreview(context: Context, count: Int) {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_CHIME
                putExtra(EXTRA_IS_PREVIEW, true)
                putExtra(EXTRA_PREVIEW_COUNT, count)
            }
            context.sendBroadcast(intent)
        }

        /**
         * 获取下一个刻钟的时间描述（供 UI 显示）
         */
        fun getNextChimeDescription(): String {
            val cal = getNextQuarterCalendar()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val count = getChimeCount(minute)
            return String.format("%02d:%02d (响%d下)", hour, minute, count)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "收到广播: action=${intent.action}")

        if (intent.action != ACTION_CHIME &&
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // 尝试处理其他 action
            if (intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
                Log.i(TAG, "精确闹钟权限状态变更，重新注册")
                scheduleNextChime(context)
                return
            }
            return
        }

        // 开机自启
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i(TAG, "开机自启：恢复报时服务")
            val serviceIntent = Intent(context, ChimeService::class.java)
            context.startForegroundService(serviceIntent)
            return
        }

        val isPreview = intent.getBooleanExtra(EXTRA_IS_PREVIEW, false)
        val previewCount = intent.getIntExtra(EXTRA_PREVIEW_COUNT, 0)

        if (isPreview) {
            // 试听模式
            Log.i(TAG, "试听播放: $previewCount 下")
            playChimes(context, previewCount)
        } else {
            // 正常报时
            val now = Calendar.getInstance()
            val minute = now.get(Calendar.MINUTE)
            val hour = now.get(Calendar.HOUR_OF_DAY)
            val chimeCount = getChimeCount(minute)

            Log.i(TAG, "报时触发: ${String.format("%02d:%02d", hour, minute)} → 响 $chimeCount 下")

            if (chimeCount > 0) {
                playChimes(context, chimeCount)
            }

            // 续订下一个刻钟
            scheduleNextChime(context)
        }
    }

    /**
     * 播放钟声（通过启动 Service 播放，确保在后台也能播放）
     */
    private fun playChimes(context: Context, count: Int) {
        val serviceIntent = Intent(context, ChimeService::class.java).apply {
            action = ChimeService.ACTION_PLAY_CHIME
            putExtra(ChimeService.EXTRA_CHIME_COUNT, count)
        }
        context.startService(serviceIntent)
    }
}
