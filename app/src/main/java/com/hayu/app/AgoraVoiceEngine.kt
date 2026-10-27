// file: app/src/main/java/com/hayu/app/AgoraVoiceEngine.kt
// Wrapper ringan untuk Agora RTC Engine — khusus voice room.
// Satu instance per RoomActivity; panggil release() di onDestroy().

package com.hayu.app

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import io.agora.rtc2.ChannelMediaOptions
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig

private const val TAG = "AgoraVoiceEngine"

// App ID Agora — jangan di-hardcode di production, pakai BuildConfig / server token
private const val TOKEN_URL = "https://hayu-android-token-server.vercel.app/api/token"

private const val AGORA_APP_ID = "02b1a0caeb97457c9a39c51e9d9e416d"

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
    /** Volume tiap pembicara (key = uid Agora, 0 = diri sendiri). Dipanggil di main thread. */
    fun onVolume(volumes: Map<Int, Int>) {}
}

class AgoraVoiceEngine(
    private val context: Context,
    private val callback: AgoraVoiceCallback
) {

    companion object {
        /** uid Agora tetap & unik per akun, supaya suara bisa dipetakan ke kursi. */
        fun uidFor(firebaseUid: String): Int {
            val h = firebaseUid.hashCode() and 0x7FFFFFFF
            return if (h == 0) 1 else h
        }
    }

    private var engine: RtcEngine? = null
    private var joined = false
    private var joining = false
    private var currentChannel = ""
    private var wantPublisher = false
    private val http = OkHttpClient()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

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
            engine?.enableAudioVolumeIndication(500, 3, true)
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
    fun joinChannel(channelId: String, asPublisher: Boolean, uid: Int = 0) {
        if (engine == null || joined || joining) return
        joining = true
        currentChannel = channelId
        wantPublisher = asPublisher

        fetchToken(channelId) { token, err ->
            joining = false
            val eng = engine
            if (eng == null || currentChannel != channelId) return@fetchToken
            if (token == null) {
                callback.onError(-2, "Gagal ambil token suara: $err")
                return@fetchToken
            }
            val options = ChannelMediaOptions().apply {
                clientRoleType = if (wantPublisher)
                    Constants.CLIENT_ROLE_BROADCASTER
                else
                    Constants.CLIENT_ROLE_AUDIENCE
                autoSubscribeAudio = true
                publishMicrophoneTrack = wantPublisher
            }
            val ret = eng.joinChannel(token, channelId, uid, options)
            if (ret != 0) {
                Log.e(TAG, "joinChannel gagal: $ret")
                callback.onError(ret, "Gagal join channel Agora (kode $ret)")
            } else {
                joined = true
                Log.d(TAG, "Bergabung ke channel: $channelId (publisher=$wantPublisher)")
            }
        }
    }

    private fun fetchToken(channel: String, onResult: (String?, String?) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            main.post { onResult(null, "belum login") }
            return
        }
        user.getIdToken(false)
            .addOnSuccessListener { r ->
                val req = Request.Builder()
                    .url("$TOKEN_URL?channel=$channel")
                    .header("Authorization", "Bearer ${r.token}")
                    .build()
                http.newCall(req).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        main.post { onResult(null, e.message ?: "jaringan bermasalah") }
                    }

                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        response.use { resp ->
                            val body = resp.body?.string().orEmpty()
                            val ok = resp.isSuccessful
                            val code = resp.code
                            val token = try {
                                JSONObject(body).optString("token", "")
                            } catch (e: Exception) {
                                ""
                            }
                            main.post {
                                if (ok && token.isNotEmpty()) onResult(token, null)
                                else onResult(null, "server $code")
                            }
                        }
                    }
                })
            }
            .addOnFailureListener { main.post { onResult(null, it.message ?: "gagal login") } }
    }

    private fun renewToken() {
        val ch = currentChannel
        if (ch.isEmpty()) return
        fetchToken(ch) { t, _ -> if (t != null) engine?.renewToken(t) }
    }

    fun leaveChannel() {
        currentChannel = ""
        joining = false
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
        wantPublisher = asPublisher
        if (!joined) return
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

        override fun onAudioVolumeIndication(
            speakers: Array<out IRtcEngineEventHandler.AudioVolumeInfo>?, totalVolume: Int
        ) {
            val list = speakers ?: return
            val map = HashMap<Int, Int>()
            for (s in list) map[s.uid] = s.volume
            main.post { callback.onVolume(map) }
        }

        override fun onTokenPrivilegeWillExpire(token: String?) {
            renewToken()
        }

        override fun onRequestToken() {
            renewToken()
        }

        override fun onLeaveChannel(stats: RtcStats) {
            Log.d(TAG, "Keluar channel — durasi: ${stats.totalDuration}s")
        }
    }
}
