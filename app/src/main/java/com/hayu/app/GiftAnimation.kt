package com.hayu.app

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

object GiftAnimation {

    private const val TAG = "hayu_gift_animation"

    fun show(context: Context, emoji: String, giftName: String) {
        val activity = context.findActivity() ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        val root = activity.window.decorView as? ViewGroup ?: return

        for (i in root.childCount - 1 downTo 0) {
            if (root.getChildAt(i).tag == TAG) root.removeViewAt(i)
        }

        fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

        val overlay = FrameLayout(activity).apply {
            tag = TAG
            setBackgroundColor(Color.argb(170, 5, 7, 24))
            alpha = 0f
            isClickable = true
            isFocusable = true
        }

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(28), dp(24), dp(28))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(60, 34, 91),
                    Color.rgb(20, 24, 49),
                    Color.rgb(43, 25, 64)
                )
            ).apply {
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.rgb(237, 194, 112))
            }
            elevation = dp(20).toFloat()
            scaleX = 0.45f
            scaleY = 0.45f
            alpha = 0f
        }

        fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
            TextView(activity).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                if (bold) setTypeface(null, Typeface.BOLD)
            }

        card.addView(label("✦  HADIAH TERKIRIM  ✦", 12f,
            Color.rgb(246, 207, 133), true))

        val gift = label(emoji, 78f, Color.WHITE).apply {
            setShadowLayer(dp(18).toFloat(), 0f, 0f, Color.rgb(255, 174, 76))
        }
        card.addView(gift, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(125)
        ))

        card.addView(label(giftName, 27f, Color.WHITE, true),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))

        val subtitle = label("Sebuah hadiah penuh kasih ✨", 13f,
            Color.rgb(218, 209, 235))
        (subtitle.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(8)
        card.addView(subtitle)

        overlay.addView(card, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            leftMargin = dp(28)
            rightMargin = dp(28)
        })

        repeat(12) { i ->
            val sparkle = label(
                listOf("✦", "✧", "✨", "⋆")[i % 4],
                if (i % 2 == 0) 22f else 16f,
                if (i % 2 == 0) Color.rgb(255, 218, 139) else Color.WHITE
            ).apply {
                alpha = 0f
                scaleX = 0.2f
                scaleY = 0.2f
            }

            overlay.addView(sparkle, FrameLayout.LayoutParams(
                dp(42), dp(42), Gravity.CENTER
            ))

            sparkle.translationX = dp(Random.nextInt(-145, 146)).toFloat()
            sparkle.translationY = dp(Random.nextInt(-210, 211)).toFloat()

            sparkle.animate()
                .alpha(1f)
                .scaleX(1.2f)
                .scaleY(1.2f)
                .rotationBy(if (i % 2 == 0) 35f else -35f)
                .setStartDelay(i * 45L)
                .setDuration(400)
                .setInterpolator(OvershootInterpolator())
                .withEndAction {
                    sparkle.animate()
                        .alpha(0f)
                        .translationYBy(-dp(25).toFloat())
                        .setStartDelay(350)
                        .setDuration(500)
                        .start()
                }
                .start()
        }

        root.addView(overlay, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        overlay.animate().alpha(1f).setDuration(220).start()

        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(550)
            .setInterpolator(OvershootInterpolator(1.15f))
            .start()

        gift.postDelayed({
            if (gift.parent != null) {
                ObjectAnimator.ofFloat(gift, View.SCALE_X, 1f, 1.13f, 1f)
                    .setDuration(750).start()
                ObjectAnimator.ofFloat(gift, View.SCALE_Y, 1f, 1.13f, 1f)
                    .setDuration(750).start()
            }
        }, 350L)

        fun dismiss() {
            if (overlay.parent == null) return
            overlay.animate().alpha(0f).setDuration(220).withEndAction {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
            }.start()
        }

        overlay.setOnClickListener { dismiss() }
        overlay.postDelayed({ dismiss() }, 2500L)
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
