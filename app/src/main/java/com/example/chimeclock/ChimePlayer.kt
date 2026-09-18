package com.example.chimeclock

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log

/**
 * ChimePlayer — 播放封装
 *
 * 职责：
 * 1. 使用 ToneGenerator 走 USAGE_ALARM 通道（静音模式仍可发声）
 * 2. 连响用 Handler.postDelayed 间隔约 800ms 串行播放
 * 3. 播放期间持 partial WakeLock 防休眠
 * 4. 监听 AudioFocusChange，被打断即终止本轮，不补响
 *
 * 执行纪律 #2: 音频只走 USAGE_ALARM 通道
 * 执行纪律 #5: 连响用 Handler.postDelayed 间隔约 800ms
 * 执行纪律 #6: AudioFocusChange 打断即止
 */
class ChimePlayer(private val context: Context) {

    companion object {
        private const val TAG = "ChimePlayer"
        private const val CHIME_INTERVAL_MS = 800L
        private const val TONE_TYPE = ToneGenerator.TONE_PROP_BEEP
        private const val TONE_VOLUME = 100 // 0-100, 原值80提升至100(最大值)，音量提升约30%
        private const val TONE_DURATION_MS = 600
    }

    private val handler = Handler(Looper.getMainLooper())
    private var toneGenerator: ToneGenerator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isPlaying = false
    private var remainingChimes = 0

    // AudioFocus 监听器——被打断即终止本轮
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.i(TAG, "AudioFocus被打断($focusChange)，终止本轮报时")
                stop()
            }
        }
    }

    init {
        // 初始化 ToneGenerator，走 STREAM_ALARM 通道
        try {
            // 提升系统 ALARM 流音量至最大（配合 TONE_VOLUME=100 实现约30%音量提升）
            val maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (currentAlarmVolume < maxAlarmVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVolume, 0)
                Log.i(TAG, "ALARM音量已提升至最大: $currentAlarmVolume -> $maxAlarmVolume")
            }
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, TONE_VOLUME)
        } catch (e: Exception) {
            Log.e(TAG, "ToneGenerator 初始化失败", e)
        }
    }

    /**
     * 播放指定次数的报时钟声
     * @param count 响数：3=整点, 2=半点, 1=一刻/三刻
     */
    fun playChimes(count: Int) {
        if (count <= 0) return
        if (isPlaying) {
            Log.w(TAG, "已有报时进行中，跳过本次")
            return
        }

        // 请求 AudioFocus（被打断即终止）
        requestAudioFocus()

        // 获取 WakeLock 防休眠
        acquireWakeLock()

        isPlaying = true
        remainingChimes = count
        Log.i(TAG, "开始报时：响 $count 下")

        playOne()
    }

    /**
     * 播放单次钟声
     */
    private fun playOne() {
        if (!isPlaying || remainingChimes <= 0) {
            onPlaybackComplete()
            return
        }

        try {
            toneGenerator?.let { tg ->
                tg.startTone(TONE_TYPE, TONE_DURATION_MS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放钟声异常", e)
            onPlaybackComplete()
            return
        }

        remainingChimes--
        if (remainingChimes > 0) {
            // 串行播放下一响，间隔约 800ms
            handler.postDelayed({
                if (isPlaying) {
                    playOne()
                }
            }, CHIME_INTERVAL_MS)
        } else {
            // 最后一响播放完毕后等待完成
            handler.postDelayed({
                onPlaybackComplete()
            }, TONE_DURATION_MS.toLong())
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        if (!isPlaying) return
        isPlaying = false
        remainingChimes = 0
        handler.removeCallbacksAndMessages(null)
        try {
            toneGenerator?.stopTone()
        } catch (e: Exception) {
            Log.e(TAG, "停止钟声异常", e)
        }
        onPlaybackComplete()
    }

    private fun onPlaybackComplete() {
        releaseWakeLock()
        abandonAudioFocus()
        isPlaying = false
        Log.i(TAG, "报时播放完毕")
    }

    /**
     * 请求 AudioFocus
     */
    private fun requestAudioFocus() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    /**
     * 释放 AudioFocus
     */
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
                audioFocusRequest = null
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    /**
     * 获取 partial WakeLock
     */
    private fun acquireWakeLock() {
        try {
            val powerManager =
                context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ChimeClock::PlaybackWakeLock"
            )
            wakeLock?.acquire(30_000L) // 最多持 30 秒防泄漏
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock 获取失败", e)
        }
    }

    /**
     * 释放 WakeLock
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock 释放异常", e)
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        stop()
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e(TAG, "ToneGenerator release 异常", e)
        }
    }
}
