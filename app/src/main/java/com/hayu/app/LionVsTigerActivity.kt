package com.hayu.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class LionVsTigerActivity : AppCompatActivity() {

    private val gold = Color.rgb(255, 204, 65)
    private val dark = Color.rgb(30, 20, 44)
    private val amounts = listOf(1000, 10000, 100000, 200000, 500000)

    private var balance = 100000
    private var selectedAmount = 1000
    private var selectedSide = ""
    private var round = 1
    private var profit = 0
    private var lastBet = 0
    private var lastSide = ""
    private var busy = false

    private lateinit var balanceText: TextView
    private lateinit var profitText: TextView
    private lateinit var roundText: TextView
    private lateinit var resultText: TextView
    private lateinit var amountRow: LinearLayout
    private lateinit var lionButton: TextView
    private lateinit var drawButton: TextView
    private lateinit var tigerButton: TextView
    private lateinit var historyText: TextView
    private lateinit var betButton: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun panel(color: Int, radius: Int = 14): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), gold)
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int = Color.WHITE,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(5), dp(7), dp(5), dp(7))
    }

    private fun add(parent: LinearLayout, view: View, height: Int = -2) {
        parent.addView(
            view,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (height == -1) ViewGroup.LayoutParams.MATCH_PARENT
                else if (height == -2) ViewGroup.LayoutParams.WRAP_CONTENT
                else dp(height)
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(dark)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(20))
        }
        scroll.addView(root)
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val back = text("‹", 34f, gold, true).apply {
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(48)))
        header.addView(
            text("LION  VS  TIGER", 21f, gold, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        add(root, header)

        roundText = text("Round $round of Today", 19f, Color.WHITE, true)
        add(root, roundText)

        resultText = text("Pilih Lion, Draw atau Tiger", 14f, gold, true)
        add(root, resultText)

        // Arena dengan aset yang diberikan pengguna
        val arena = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = panel(Color.rgb(46, 34, 47), 18)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }

        val lionImage = ImageView(this).apply {
            setImageResource(R.drawable.lion)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Lion"
        }
        val tigerImage = ImageView(this).apply {
            setImageResource(R.drawable.tiger)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Tiger"
        }

        arena.addView(lionImage, LinearLayout.LayoutParams(0, dp(205), 1f))
        arena.addView(tigerImage, LinearLayout.LayoutParams(0, dp(205), 1f))
        add(root, arena)

        add(root, text("RIWAYAT HASIL", 13f, gold, true))
        historyText = text("Belum ada hasil", 13f)
        historyText.background = panel(Color.rgb(66, 49, 45))
        add(root, historyText)

        add(root, text("PILIH TARUHAN", 14f, gold, true))

        val choiceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        lionButton = choice("🦁\nLION")
        drawButton = choice("⚖️\nDRAW")
        tigerButton = choice("🐯\nTIGER")

        listOf(lionButton, drawButton, tigerButton).forEach { button ->
            val lp = LinearLayout.LayoutParams(0, dp(74), 1f)
            lp.setMargins(dp(3), dp(3), dp(3), dp(3))
            choiceRow.addView(button, lp)
        }
        add(root, choiceRow)

        add(root, text("NOMINAL COIN", 14f, gold, true))
        amountRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        add(root, amountRow)
        buildAmounts()

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = panel(Color.rgb(49, 76, 67))
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val balanceColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        balanceText = text("Coins Balance\n${fmt(balance)}", 14f, gold, true)
        profitText = text("Profits of Today\n${fmt(profit)}", 14f, gold, true)
        balanceColumn.addView(balanceText)
        stats.addView(balanceColumn, LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(profitText, LinearLayout.LayoutParams(0, -2, 1f))
        add(root, stats)

        betButton = text("▶  MULAI RONDE", 17f, Color.WHITE, true).apply {
            background = panel(Color.rgb(38, 160, 69), 16)
            setOnClickListener { playRound() }
        }
        add(root, betButton, 58)

        val repeat = text("↻  REPEAT TARUHAN TERAKHIR", 14f, gold, true).apply {
            background = panel(Color.rgb(73, 55, 30))
            setOnClickListener {
                if (lastBet <= 0 || lastSide.isEmpty()) {
                    Toast.makeText(
                        this@LionVsTigerActivity,
                        "Belum ada taruhan sebelumnya",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    selectedSide = lastSide
                    selectedAmount = lastBet
                    refreshChoices()
                    buildAmounts()
                    playRound()
                }
            }
        }
        add(root, repeat, 52)

        refreshChoices()
    }

    private fun choice(label: String): TextView {
        return text(label, 16f, Color.WHITE, true).apply {
            background = panel(Color.rgb(102, 70, 30), 12)
            setOnClickListener {
                if (!busy) {
                    selectedSide = when (this) {
                        lionButton -> "LION"
                        tigerButton -> "TIGER"
                        else -> "DRAW"
                    }
                    refreshChoices()
                }
            }
        }
    }

    private fun buildAmounts() {
        amountRow.removeAllViews()
        amounts.forEach { amount ->
            val button = text(
                when (amount) {
                    1000 -> "1K"
                    else -> "${amount / 1000}K"
                },
                12f,
                if (selectedAmount == amount) Color.BLACK else Color.WHITE,
                true
            )
            button.background = panel(
                if (selectedAmount == amount) gold else Color.rgb(75, 72, 79),
                10
            )
            button.setOnClickListener {
                if (!busy) {
                    selectedAmount = amount
                    buildAmounts()
                }
            }
            val lp = LinearLayout.LayoutParams(0, dp(55), 1f)
            lp.setMargins(dp(2), dp(2), dp(2), dp(2))
            amountRow.addView(button, lp)
        }
    }

    private fun refreshChoices() {
        if (!::lionButton.isInitialized) return
        listOf(
            lionButton to "LION",
            drawButton to "DRAW",
            tigerButton to "TIGER"
        ).forEach { (button, side) ->
            button.background = panel(
                if (selectedSide == side) Color.rgb(32, 143, 85)
                else Color.rgb(102, 70, 30),
                12
            )
        }
    }

    private fun playRound() {
        if (busy) return
        if (selectedSide.isEmpty()) {
            Toast.makeText(this, "Pilih Lion, Draw atau Tiger", Toast.LENGTH_SHORT).show()
            return
        }
        if (balance < selectedAmount) {
            Toast.makeText(this, "Saldo demo tidak cukup", Toast.LENGTH_SHORT).show()
            return
        }

        busy = true
        lastBet = selectedAmount
        lastSide = selectedSide
        balance -= selectedAmount
        updateStats()
        betButton.text = "⏳  MENENTUKAN HASIL..."

        // Simulasi lokal untuk uji UI, bukan hasil server atau taruhan nyata.
        betButton.postDelayed({
            val outcomes = listOf("LION", "DRAW", "TIGER")
            val outcome = outcomes[Random.nextInt(outcomes.size)]
            val won = outcome == selectedSide

            if (won) {
                val payout = when (outcome) {
                    "DRAW" -> selectedAmount * 3
                    else -> selectedAmount * 2
                }
                balance += payout
                profit += payout - selectedAmount
                resultText.text = "HASIL: $outcome  •  MENANG +${fmt(payout - selectedAmount)}"
            } else {
                profit -= selectedAmount
                resultText.text = "HASIL: $outcome  •  KALAH -${fmt(selectedAmount)}"
            }

            historyText.text = "$outcome   •   ${historyText.text}"
                .take(220)
            round++
            roundText.text = "Round $round of Today"
            updateStats()
            betButton.text = "▶  MULAI RONDE"
            busy = false
        }, 900)
    }

    private fun updateStats() {
        balanceText.text = "Coins Balance\n${fmt(balance)}"
        profitText.text = "Profits of Today\n${fmt(profit)}"
    }

    private fun fmt(value: Int): String =
        String.format("%,d", value).replace(',', '.')

}
