// file: app/src/main/java/com/hayu/app/AgoraVoiceEngine.kt
// Wrapper ringan untuk Agora RTC Engine — khusus voice room.
// Satu instance per RoomActivity; panggil release() di onDestroy().

package com.hayu.app

import android.content.Context
import android.util.Log
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig

private const val TAG = "AgoraVoiceEngine"

// App ID Agora — jangan di-hardcode di production, pakai BuildConfig / server token
private const val AGORA_APP_ID = "883ce86dfc7f4e6ca8eaeac12cd74c54"

/**
 * Callback yang dipakai RoomActivity untuk merespons event Agora.
 * Semua callback dipanggil di main thread.
 */
interface AgoraVoiceCallback {
    /** User lain bergabung ke channel */
    fun onRemoteUserJoined(uid: Int)
    /** User lain keluar dari channel */
    fun onRemoteUserLeft(uid: Int)
    /** Terjadi error (misal token kedaluwarsa) */
    fun onError(code: Int, message: String)
}

class AgoraVoiceEngine(
    private val context: Context,
    private val callback: AgoraVoiceCallback
) {

    private var engine: RtcEngine? = null
    private var joined = false

    // ── Inisialisasi ────────────────────────────────────────────────────────

    fun init() {
        if (engine != null) return
        try {
            val appCtx: Context = context.applicationContext ?: context
            val config = RtcEngineConfig()
            config.mContext = appCtx
            config.mAppId = AGORA_APP_ID
            config.mEventHandler = handler
            engine = RtcEngine.create(config)
            // Mode suara: audio call berkualitas tinggi dengan noise suppression
            engine?.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
            engine?.setAudioProfile(
                Constants.AUDIO_PROFILE_MUSIC_HIGH_QUALITY_STEREO,
                Constants.AUDIO_SCENARIO_GAME_STREAMING
            )
            Log.d(TAG, "Agora engine siap")
        } catch (e: Throwable) {
            val where = e.stackTrace.firstOrNull()?.toString() ?: "?"
            Log.e(TAG, "Gagal init Agora", e)
            callback.onError(-1, "Gagal inisialisasi Agora: ${e.javaClass.simpleName}: ${e.message} @ $where")
        }
    }

    // ── Join / Leave Channel ─────────────────────────────────────────────────

    /**
     * Bergabung ke channel suara.
     * @param channelId  roomId dari Firestore — dipakai sebagai nama channel Agora
     * @param asPublisher  true = bisa ngomong (on mic), false = hanya dengarkan
     *
     * Untuk demo / development tanpa token server, token dikosongkan ("").
     * Di production: generate token dari server dan isi di sini.
     */
    fun joinChannel(channelId: String, asPublisher: Boolean) {
        val eng = engine ?: return
        if (joined) return

        val options = ChannelMediaOptions().apply {
            clientRoleType = if (asPublisher)
                Constants.CLIENT_ROLE_BROADCASTER
            else
                Constants.CLIENT_ROLE_AUDIENCE
            autoSubscribeAudio = true
            publishMicrophoneTrack = asPublisher
        }

        // Token kosong = mode testing (tidak perlu token server).
        // Ganti dengan token asli dari servermu sebelum publish ke Play Store.
        val ret = eng.joinChannel("", channelId, 0, options)
        if (ret != 0) {
            Log.e(TAG, "joinChannel gagal: $ret")
            callback.onError(ret, "Gagal join channel Agora (kode $ret)")
        } else {
            joined = true
            Log.d(TAG, "Bergabung ke channel: $channelId (publisher=$asPublisher)")
        }
    }

    fun leaveChannel() {
        if (!joined) return
        engine?.leaveChannel()
        joined = false
        Log.d(TAG, "Keluar dari channel Agora")
    }

    // ── Mic & Speaker ────────────────────────────────────────────────────────

    /** Mute / unmute mikrofon lokal */
    fun setMicMuted(muted: Boolean) {
        engine?.muteLocalAudioStream(muted)
        Log.d(TAG, if (muted) "Mic muted" else "Mic unmuted")
    }

    /** Aktif / nonaktif speaker (mendengar orang lain) */
    fun setSpeakerEnabled(enabled: Boolean) {
        // true  → suara keluar lewat speaker (earpiece off)
        // false → semua audio remote di-mute
        engine?.muteAllRemoteAudioStreams(!enabled)
        engine?.setEnableSpeakerphone(enabled)
        Log.d(TAG, if (enabled) "Speaker on" else "Speaker off")
    }

    /** Ubah peran: audience → broadcaster (naik mic) atau sebaliknya (turun mic) */
    fun updateRole(asPublisher: Boolean) {
        val options = ChannelMediaOptions().apply {
            clientRoleType = if (asPublisher)
                Constants.CLIENT_ROLE_BROADCASTER
            else
                Constants.CLIENT_ROLE_AUDIENCE
            publishMicrophoneTrack = asPublisher
            autoSubscribeAudio = true
        }
        engine?.updateChannelMediaOptions(options)
        Log.d(TAG, "Role diperbarui: publisher=$asPublisher")
    }

    // ── Bersihkan ────────────────────────────────────────────────────────────

    fun release() {
        leaveChannel()
        RtcEngine.destroy()
        engine = null
        Log.d(TAG, "Agora engine dilepas")
    }

    // ── Event Handler ────────────────────────────────────────────────────────

    private val handler = object : IRtcEngineEventHandler() {

        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            Log.d(TAG, "Berhasil join channel $channel (uid Agora lokal: $uid)")
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.d(TAG, "Remote user bergabung: $uid")
            (context as? android.app.Activity)?.runOnUiThread {
                callback.onRemoteUserJoined(uid)
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.d(TAG, "Remote user keluar: $uid (reason=$reason)")
            (context as? android.app.Activity)?.runOnUiThread {
                callback.onRemoteUserLeft(uid)
            }
        }

        override fun onError(err: Int) {
            Log.e(TAG, "Agora error: $err")
            (context as? android.app.Activity)?.runOnUiThread {
                callback.onError(err, "Agora error kode $err")
            }
        }

        override fun onLeaveChannel(stats: RtcStats) {
            Log.d(TAG, "Keluar channel — durasi: ${stats.totalDuration}s")
        }
    }
}
