package com.hayu.app

import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

data class GameItem(
    val emoji: String,
    val name: String,
    val tag: String = "",   // "Hot" / "New" / ""
    val url: String = ""    // url game kalau mau buka WebView
)

object GamePanelSheet {

    private val luckyGames = listOf(
        GameItem("🐔", "Greedy Eat",  "Hot"),
        GameItem("🚗", "Super Car",   "Hot"),
        GameItem("🎣", "Fishing",     "Hot"),
        GameItem("🎰", "Greedy Jackpot", "Hot"),
        GameItem("🃏", "Teen Patti",  "Hot"),
        GameItem("🏎️", "Speed Racing","New"),
        GameItem("🐱", "White Cat",   "New"),
        GameItem("🍉", "Fruit Party", "New"),
        GameItem("💰", "MegaJackpot", "New"),
        GameItem("🥑", "Fruit Party 2",""),
        GameItem("🦁", "Lion vs Tiger",""),
        GameItem("7️⃣", "Lucky 777",  ""),
        GameItem("⚡", "Olympus",     ""),
        GameItem("🗿", "Sphinxt",     ""),
        GameItem("🎲", "Jackpot",     ""),
        GameItem("🎯", "Original777", "New")
    )

    private val santaiGames = listOf(
        GameItem("🎮", "Mini Games",  "Hot"),
        GameItem("🧩", "Puzzle",      "New"),
        GameItem("🎪", "Fun Park",    ""),
        GameItem("🎨", "Draw It",     "New"),
        GameItem("🏆", "Quiz",        "Hot"),
        GameItem("🎸", "Music Beat",  ""),
        GameItem("🃏", "Card War",    "New"),
        GameItem("🎳", "Bowling",     "")
    )

    fun show(activity: AppCompatActivity) {
        val dialog = BottomSheetDialog(activity)
        val d = { v: Int -> (v * activity.resources.displayMetrics.density).toInt() }

        val root = LinearLayout(activity)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundResource(R.drawable.bg_bottom_sheet)
        root.setPadding(d(16), d(16), d(16), d(32))

        // --- TAB ROW ---
        val tabRow = LinearLayout(activity)
        tabRow.orientation = LinearLayout.HORIZONTAL
        tabRow.setPadding(0, 0, 0, d(12))

        val tabs = listOf("Lucky" to luckyGames, "Santai" to santaiGames)
        val tabViews = ArrayList<TextView>()
        val gridContainer = FrameLayout(activity)
        val grids = ArrayList<ScrollView>()

        tabs.forEachIndexed { idx, (label, games) ->
            val tv = TextView(activity)
            tv.text = label
            tv.textSize = 16f
            tv.typeface = android.graphics.Typeface.DEFAULT_BOLD
            tv.setPadding(d(16), d(8), d(16), d(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = d(8)
            tabRow.addView(tv, lp)
            tabViews.add(tv)

            // build grid buat tab ini
            val sv = ScrollView(activity)
            val grid = buildGrid(activity, dialog, games, d)
            sv.addView(grid)
            sv.visibility = if (idx == 0) android.view.View.VISIBLE else android.view.View.GONE
            grids.add(sv)
            gridContainer.addView(sv)
        }

        // tab click logic
        fun selectTab(idx: Int) {
            tabViews.forEachIndexed { i, tv ->
                tv.setTextColor(if (i == idx) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#8892AA"))
                if (i == idx) tv.paintFlags = tv.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                else tv.paintFlags = tv.paintFlags and android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv()
            }
            grids.forEachIndexed { i, sv ->
                sv.visibility = if (i == idx) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
        selectTab(0)
        tabViews.forEachIndexed { i, tv -> tv.setOnClickListener { selectTab(i) } }

        root.addView(tabRow)

        val gridLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(400))
        root.addView(gridContainer, gridLp)

        dialog.setContentView(root)
        dialog.show()
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun buildGrid(
        activity: AppCompatActivity,
        dialog: BottomSheetDialog,
        games: List<GameItem>,
        d: (Int) -> Int
    ): LinearLayout {
        val container = LinearLayout(activity)
        container.orientation = LinearLayout.VERTICAL

        var i = 0
        while (i < games.size) {
            val row = LinearLayout(activity)
            row.orientation = LinearLayout.HORIZONTAL
            for (j in i until i + 4) {
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins(d(4), d(4), d(4), d(4))
                if (j >= games.size) { row.addView(TextView(activity), lp); continue }
                val g = games[j]

                val tile = LinearLayout(activity)
                tile.orientation = LinearLayout.VERTICAL
                tile.gravity = Gravity.CENTER_HORIZONTAL
                tile.setPadding(d(4), d(8), d(4), d(8))
                tile.setBackgroundResource(R.drawable.bg_card_category)

                // emoji icon
                val icon = TextView(activity)
                icon.text = g.emoji
                icon.textSize = 32f
                icon.gravity = Gravity.CENTER

                // tag badge
                if (g.tag.isNotEmpty()) {
                    val badge = TextView(activity)
                    badge.text = "🔥 ${g.tag}"
                    badge.textSize = 9f
                    badge.setTextColor(android.graphics.Color.WHITE)
                    badge.setBackgroundColor(android.graphics.Color.parseColor("#EF4444"))
                    badge.setPadding(d(4), d(1), d(4), d(1))
                    val frame = FrameLayout(activity)
                    frame.addView(icon)
                    val badgeLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    badgeLp.gravity = Gravity.TOP or Gravity.END
                    frame.addView(badge, badgeLp)
                    tile.addView(frame)
                } else {
                    tile.addView(icon)
                }

                val name = TextView(activity)
                name.text = g.name
                name.textSize = 10f
                name.setTextColor(android.graphics.Color.parseColor("#C4CAFF"))
                name.gravity = Gravity.CENTER
                name.maxLines = 2
                name.setPadding(0, d(4), 0, 0)
                tile.addView(name)

                tile.setOnClickListener {
                    if (g.name == "Greedy Eat") {
                        dialog.dismiss()
                        activity.startActivity(Intent(activity, GreedyEatActivity::class.java))
                    } else if (g.name == "Lion vs Tiger") {
                        dialog.dismiss()
                        activity.startActivity(
                            Intent(activity, LionVsTigerActivity::class.java)
                        )
                    } else if (g.name == "Fruit Party" || g.name == "Fruit Party 2") {
                        dialog.dismiss()
                        activity.startActivity(
                            Intent(activity, FruitPartyActivity::class.java)
                        )
                    } else if (g.name == "Olympus") {
                        dialog.dismiss()
                        activity.startActivity(
                            Intent(activity, GatesOfOlympusActivity::class.java)
                        )
                    } else {
                        Toast.makeText(
                            activity,
                            "${g.emoji} ${g.name} segera hadir!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                row.addView(tile, lp)
            }
            val rowLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            rowLp.bottomMargin = d(4)
            container.addView(row, rowLp)
            i += 4
        }
        return container
    }
}
