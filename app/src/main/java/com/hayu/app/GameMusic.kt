package com.hayu.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** Soundtrack loop per game + tombol mute melayang. Pakai: GameMusic.attach(this, R.raw.bgm_xxx) */
object GameMusic {
    private const val PREF = "game_music"
    private const val KEY_ON = "on"
    private const val VOLUME = 0.45f

    private fun isOn(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_ON, true)
    private fun setOn(c: Context, v: Boolean) =
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, v).apply()

    fun attach(activity: AppCompatActivity, resId: Int) {
        var player: MediaPlayer? = null
        var button: TextView? = null

        fun ensure(): MediaPlayer? {
            if (player == null) {
                player = try {
                    MediaPlayer.create(
                        activity, resId, AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                        android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
                    )?.apply {
                        isLooping = true
                        setVolume(VOLUME, VOLUME)
                    }
                } catch (e: Throwable) {
                    null
                }
            }
            return player
        }

        fun apply() {
            val on = isOn(activity)
            button?.text = if (on) "🔊" else "🔇"
            try {
                val p = ensure() ?: return
                if (on) { if (!p.isPlaying) p.start() } else if (p.isPlaying) p.pause()
            } catch (e: Throwable) {
            }
        }

        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (button == null) {
                    val d = activity.resources.displayMetrics.density
                    val b = TextView(activity).apply {
                        textSize = 18f
                        gravity = Gravity.CENTER
                        setBackgroundResource(R.drawable.bg_circle_btn)
                        elevation = 8 * d
                        setOnClickListener {
                            setOn(activity, !isOn(activity))
                            apply()
                        }
                    }
                    val lp = FrameLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt(), Gravity.TOP or Gravity.END)
                    lp.topMargin = (96 * d).toInt()
                    lp.marginEnd = (8 * d).toInt()
                    activity.addContentView(b, lp as ViewGroup.LayoutParams)
                    button = b
                }
            }

            override fun onResume(owner: LifecycleOwner) = apply()

            override fun onPause(owner: LifecycleOwner) {
                try { player?.let { if (it.isPlaying) it.pause() } } catch (e: Throwable) {}
            }

            override fun onDestroy(owner: LifecycleOwner) {
                try { player?.release() } catch (e: Throwable) {}
                player = null
            }
        })
    }
}
