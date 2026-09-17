package com.example.chimeclock

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

/**
 * MainActivity — 权限申请、引导页、开关控制、试听入口
 *
 * 职责：
 * 1. 主开关：一键暂停/恢复报时（F5）
 * 2. 试听入口：1/2/3 下（F7）
 * 3. 权限引导：精确闹钟 + 电池优化白名单 + 自启动（F6）
 * 4. 状态显示：当前开关状态 + 下次报时时间
 *
 * 执行纪律 #3: 所有权限申请路径必须带"被拒后的降级/提示"分支
 * 执行纪律 #4: 零依赖原则——无网络请求、无广告、无第三方统计
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "chime_prefs"
        private const val KEY_ENABLED = "chime_enabled"
    }

    private lateinit var btnToggle: MaterialButton
    private lateinit var btnPreview1: MaterialButton
    private lateinit var btnPreview2: MaterialButton
    private lateinit var btnPreview3: MaterialButton
    private lateinit var btnExactAlarm: MaterialButton
    private lateinit var btnBatteryOpt: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvNextChime: TextView

    // 通知权限请求 Launcher（API 33+）
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Toast.makeText(this, R.string.perm_granted_notif, Toast.LENGTH_SHORT).show()
            } else {
                // 执行纪律 #3: 权限兜底降级——不静默失效
                Toast.makeText(this, R.string.perm_denied_notif, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupClickListeners()

        // 首次启动请求通知权限
        checkAndRequestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun bindViews() {
        btnToggle = findViewById(R.id.btnToggle)
        btnPreview1 = findViewById(R.id.btnPreview1)
        btnPreview2 = findViewById(R.id.btnPreview2)
        btnPreview3 = findViewById(R.id.btnPreview3)
        btnExactAlarm = findViewById(R.id.btnExactAlarm)
        btnBatteryOpt = findViewById(R.id.btnBatteryOpt)
        tvStatus = findViewById(R.id.tvStatus)
        tvNextChime = findViewById(R.id.tvNextChime)
    }

    private fun setupClickListeners() {
        // 主开关
        btnToggle.setOnClickListener {
            toggleChime()
        }

        // 试听按钮
        btnPreview1.setOnClickListener {
            AlarmReceiver.sendPreview(this, 1)
        }
        btnPreview2.setOnClickListener {
            AlarmReceiver.sendPreview(this, 2)
        }
        btnPreview3.setOnClickListener {
            AlarmReceiver.sendPreview(this, 3)
        }

        // 精确闹钟权限引导
        btnExactAlarm.setOnClickListener {
            requestExactAlarmPermission()
        }

        // 电池优化白名单
        btnBatteryOpt.setOnClickListener {
            requestBatteryOptimization()
        }
    }

    /**
     * 切换报时开关
     */
    private fun toggleChime() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_ENABLED, false)

        if (isEnabled) {
            // 关闭报时
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            AlarmReceiver.cancelNextChime(this)
            stopService(Intent(this, ChimeService::class.java))
            Log.i(TAG, "报时已关闭")
        } else {
            // 开启报时
            prefs.edit().putBoolean(KEY_ENABLED, true).apply()
            val serviceIntent = Intent(this, ChimeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.i(TAG, "报时已开启")
        }

        updateUI()
    }

    /**
     * 更新 UI 状态
     */
    private fun updateUI() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_ENABLED, false)

        if (isEnabled) {
            tvStatus.text = getString(R.string.status_on)
            btnToggle.text = getString(R.string.btn_stop)
            tvNextChime.text = "${getString(R.string.status_next)}: ${AlarmReceiver.getNextChimeDescription()}"
        } else {
            tvStatus.text = getString(R.string.status_off)
            btnToggle.text = getString(R.string.btn_start)
            tvNextChime.text = ""
        }
    }

    /**
     * 检查并请求通知权限
     * 执行纪律 #3: 权限兜底降级
     */
    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * 请求精确闹钟权限
     * 执行纪律 #3: 权限兜底降级——被拒时提示并降级为非精确触发
     */
    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // 跳转到精确闹钟权限设置页
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "无法跳转精确闹钟权限设置", e)
                    // 降级：跳转到应用详情页
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
                Toast.makeText(this, R.string.perm_denied_exact, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, R.string.perm_granted_exact, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, R.string.perm_granted_exact, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 请求电池优化白名单
     */
    private fun requestBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "无法跳转电池优化设置", e)
                // 降级：跳转到电池优化列表页
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } catch (e2: Exception) {
                    // 最终降级：跳转到应用详情页
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            }
        } else {
            Toast.makeText(this, "已在电池优化白名单中", Toast.LENGTH_SHORT).show()
        }
    }
}
