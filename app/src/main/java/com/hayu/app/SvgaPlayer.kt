package com.hayu.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import com.opensource.svgaplayer.SVGACallback
import com.opensource.svgaplayer.SVGAImageView
import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGAVideoEntity
import java.io.FileInputStream
import java.net.URL

object SvgaPlayer {
    private var lastToast = 0L

    /** Pesan gagal unduh aset online (maks. 1x per 15 detik supaya tidak membanjiri layar). */
    internal fun toastOnce(ctx: Context, msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastToast < 15_000L) return
        lastToast = now
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(ctx.applicationContext, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** loops = 1 sekali putar, 0 = berulang terus. File diunduh & di-cache oleh SVGAParser. */
    fun play(
        view: SVGAImageView,
        url: String,
        loops: Int = 1,
        onFinish: (() -> Unit)? = null,
        onError: (() -> Unit)? = null
    ) {
        try {
            val completion = object : SVGAParser.ParseCompletion {
                override fun onComplete(videoItem: SVGAVideoEntity) {
                    view.setVideoItem(videoItem)
                    view.loops = loops
                    view.callback = object : SVGACallback {
                        override fun onPause() {}
                        override fun onFinished() { onFinish?.invoke() }
                        override fun onRepeat() {}
                        override fun onStep(frame: Int, percentage: Double) {}
                    }
                    view.startAnimation()
                }

                override fun onError() {
                    onError?.invoke()
                }
            }
            val parser = SVGAParser(view.context)
            if (url.startsWith(VipAssets.PREFIX)) {
                // aset VIP / SVIP: di-host online (bukan di dalam APK)
                val remote = VipAssets.url(url.removePrefix(VipAssets.PREFIX))
                parser.decodeFromURL(URL(remote), object : SVGAParser.ParseCompletion {
                    override fun onComplete(videoItem: SVGAVideoEntity) = completion.onComplete(videoItem)
                    override fun onError() {
                        toastOnce(view.context, "Aset efek tidak bisa diunduh:\n$remote")
                        completion.onError()
                    }
                })
            } else if (SvgaStore.isStored(url)) {
                // Aset disimpan di Firestore (potongan) -> susun jadi file cache lalu putar
                val id = url.removePrefix(SvgaStore.PREFIX)
                SvgaStore.openFile(view.context, id) { file ->
                    if (file == null) {
                        onError?.invoke()
                    } else {
                        try {
                            parser.decodeFromInputStream(
                                FileInputStream(file), "fs_${file.name}", completion, true
                            )
                        } catch (e: Exception) {
                            onError?.invoke()
                        }
                    }
                }
            } else {
                parser.decodeFromURL(URL(url), completion)
            }
        } catch (e: Exception) {
            onError?.invoke()
        }
    }
}

/** Animasi layar penuh sekali putar (gift, mobil masuk room). Tidak menghalangi sentuhan. */
object SvgaOverlay {
    private const val TAG = "hayu_svga_overlay"

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pending = java.util.ArrayDeque<Pair<Context, String>>()
    private var busy = false

    /** Antre: efek diputar satu per satu (masuk room, kendaraan, dll) supaya tidak saling memotong. */
    fun enqueue(context: Context, url: String) {
        pending.add(context to url)
        if (!busy) playNext()
    }

    private fun playNext() {
        val item = pending.poll()
        if (item == null) {
            busy = false
            return
        }
        busy = true
        var advanced = false
        val advance = {
            if (!advanced) {
                advanced = true
                playNext()
            }
        }
        show(item.first, item.second, advance)
        handler.postDelayed({ advance() }, 15_000L) // pengaman kalau animasi macet / terpotong
    }

    fun show(context: Context, url: String, onDone: (() -> Unit)? = null) {
        val activity = context.findActivity()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onDone?.invoke()
            return
        }
        val root = activity.window.decorView as? ViewGroup
        if (root == null) {
            onDone?.invoke()
            return
        }

        for (i in root.childCount - 1 downTo 0) {
            if (root.getChildAt(i).tag == TAG) root.removeViewAt(i)
        }

        val view = SVGAImageView(activity)
        view.tag = TAG
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        root.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        var done = false
        val remove = {
            view.post { (view.parent as? ViewGroup)?.removeView(view) }
            if (!done) {
                done = true
                onDone?.invoke()
            }
            Unit
        }
        SvgaPlayer.play(view, url, 1, onFinish = remove, onError = remove)
        view.postDelayed({ remove() }, 20_000L) // pengaman kalau animasi macet
    }

    private fun Context.findActivity(): Activity? {
        var current: Context = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            val next = current.baseContext
            if (next === current) break
            current = next
        }
        return current as? Activity
    }
}

/** Bingkai SVGA di atas avatar yang berada di dalam RelativeLayout. */
object SvgaFrame {
    private const val TAG = "hayu_svga_frame"

    fun attach(avatar: ImageView, url: String?) {
        val parent = avatar.parent as? ViewGroup ?: return
        for (i in parent.childCount - 1 downTo 0) {
            if (parent.getChildAt(i).tag == TAG) parent.removeViewAt(i)
        }
        if (url.isNullOrEmpty()) return

        val lp = avatar.layoutParams as? RelativeLayout.LayoutParams ?: return
        if (lp.width <= 0 || lp.height <= 0) return
        val size = (lp.width * 1.45f).toInt()

        val view = SVGAImageView(avatar.context)
        view.tag = TAG
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        val frameLp = RelativeLayout.LayoutParams(size, size)
        if (avatar.id != View.NO_ID) {
            // Ikut posisi avatar di mana pun diletakkan (kiri / tengah)
            frameLp.addRule(RelativeLayout.ALIGN_LEFT, avatar.id)
            frameLp.addRule(RelativeLayout.ALIGN_TOP, avatar.id)
            frameLp.leftMargin = -(size - lp.width) / 2
            frameLp.topMargin = -(size - lp.height) / 2
        } else {
            frameLp.addRule(RelativeLayout.CENTER_HORIZONTAL)
            frameLp.topMargin = lp.topMargin - (size - lp.height) / 2
        }
        parent.addView(view, frameLp)

        SvgaPlayer.play(view, url, 0, onError = { SvgaPlayer.toastOnce(avatar.context, "Bingkai gagal dimuat") })
    }
}
