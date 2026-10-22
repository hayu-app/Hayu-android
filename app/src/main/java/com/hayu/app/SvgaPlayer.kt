package com.hayu.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import com.opensource.svgaplayer.SVGACallback
import com.opensource.svgaplayer.SVGAImageView
import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGAVideoEntity
import java.net.URL

object SvgaPlayer {
    /** loops = 1 sekali putar, 0 = berulang terus. File diunduh & di-cache oleh SVGAParser. */
    fun play(
        view: SVGAImageView,
        url: String,
        loops: Int = 1,
        onFinish: (() -> Unit)? = null,
        onError: (() -> Unit)? = null
    ) {
        try {
            SVGAParser(view.context).decodeFromURL(URL(url), object : SVGAParser.ParseCompletion {
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
            })
        } catch (e: Exception) {
            onError?.invoke()
        }
    }
}

/** Animasi layar penuh sekali putar (gift, mobil masuk room). Tidak menghalangi sentuhan. */
object SvgaOverlay {
    private const val TAG = "hayu_svga_overlay"

    fun show(context: Context, url: String) {
        val activity = context.findActivity() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        val root = activity.window.decorView as? ViewGroup ?: return

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

        val remove = { view.post { (view.parent as? ViewGroup)?.removeView(view) }; Unit }
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
        frameLp.addRule(RelativeLayout.CENTER_HORIZONTAL)
        frameLp.topMargin = lp.topMargin - (size - lp.height) / 2
        parent.addView(view, frameLp)

        SvgaPlayer.play(view, url, 0)
    }
}
