#!/data/data/com.termux/files/usr/bin/bash
# HAYU - Patch: angka jumlah Red Packet di tombol 🧧 (kanan atas)
#  Kalau ada 2+ Red Packet yang belum kamu ambil, muncul angka kecil di pojok kanan atas tombol.
#  Untuk repo yang SUDAH menjalankan patch-redpacket.sh.
#   cd ~/Hayu-android && bash /sdcard/Download/patch-redpacket-count.sh
set -e
F="app/src/main/java/com/hayu/app/RedPacket.kt"
[ -f "$F" ] || { echo "❌ RedPacket.kt tidak ada. Jalankan patch-redpacket.sh dulu, dari root repo."; exit 1; }
command -v python3 >/dev/null 2>&1 || pkg install -y python
cat > /tmp/rp_edit.py <<'PYEOF'
import sys
p = sys.argv[1]
s = open(p, encoding="utf-8").read()
if "badgeCount" in s:
    print("   (sudah ada, dilewati)")
    sys.exit(0)

def cut(s, start, end, new):
    a = s.find(start)
    b = s.find(end)
    if a < 0 or b < 0 or b <= a or s.count(start) != 1:
        sys.exit("❌ RedPacket.kt: bagian tidak ditemukan: " + start.strip())
    return s[:a] + new + s[b:]

def rep(s, old, new):
    if s.count(old) != 1:
        sys.exit("❌ RedPacket.kt: bagian tidak ditemukan: " + old.strip())
    return s.replace(old, new)

s = rep(s, "import android.graphics.Color\n",
        "import android.graphics.Color\nimport android.graphics.Typeface\n")
s = rep(s, "    private var badge: TextView? = null\n",
        "    private var badge: View? = null\n"
        "    private var badgeIcon: TextView? = null\n"
        "    private var badgeCount: TextView? = null\n")
s = rep(s, "        badge = null\n    }\n",
        "        badge = null\n        badgeIcon = null\n        badgeCount = null\n    }\n")

new_add = '''    private fun addBadge() {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val size = Ui.dp(activity, 58)

        val box = FrameLayout(activity)
        box.elevation = Ui.dp(activity, 8).toFloat()
        box.visibility = View.GONE
        box.setOnClickListener { onBadgeClick() }

        val icon = TextView(activity)
        icon.gravity = Gravity.CENTER
        icon.textSize = 11f
        icon.setTextColor(Color.WHITE)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.parseColor("#EF4444"))
        bg.setStroke(Ui.dp(activity, 2), Color.parseColor("#FBBF24"))
        icon.background = bg
        box.addView(icon, FrameLayout.LayoutParams(size, size, Gravity.CENTER))

        // angka kecil di kanan atas: jumlah Red Packet yang belum kamu ambil
        val cnt = TextView(activity)
        cnt.gravity = Gravity.CENTER
        cnt.textSize = 11f
        cnt.setTextColor(Color.parseColor("#EF4444"))
        cnt.setTypeface(null, Typeface.BOLD)
        cnt.setPadding(Ui.dp(activity, 5), 0, Ui.dp(activity, 5), 0)
        cnt.minWidth = Ui.dp(activity, 20)
        cnt.minHeight = Ui.dp(activity, 20)
        val cbg = GradientDrawable()
        cbg.shape = GradientDrawable.RECTANGLE
        cbg.cornerRadius = Ui.dp(activity, 20).toFloat()
        cbg.setColor(Color.WHITE)
        cbg.setStroke(Ui.dp(activity, 1), Color.parseColor("#FBBF24"))
        cnt.background = cbg
        cnt.visibility = View.GONE
        box.addView(
            cnt,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
        )

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        lp.rightMargin = Ui.dp(activity, 8)
        root.addView(box, lp)
        badge = box
        badgeIcon = icon
        badgeCount = cnt
    }

'''
s = cut(s, "    private fun addBadge() {\n", "    private fun visiblePackets", new_add)

new_refresh = '''    private fun refreshBadge() {
        val box = badge ?: return
        val now = System.currentTimeMillis()
        val visible = visiblePackets(now)
        if (visible.isEmpty()) {
            box.visibility = View.GONE
            return
        }
        val available = visible.filter { !it.hasClaimed(myUid) }
        val first = available.firstOrNull() ?: visible.first()
        val wait = first.openAt - now
        val sub = when {
            wait > 0 -> "${(wait + 999) / 1000}s"
            first.hasClaimed(myUid) -> "✓"
            else -> "Ambil"
        }
        badgeIcon?.text = "🧧\\n$sub"

        val n = available.size
        badgeCount?.let { c ->
            if (n >= 2) {
                c.text = if (n > 99) "99+" else n.toString()
                c.visibility = View.VISIBLE
            } else {
                c.visibility = View.GONE
            }
        }
        box.visibility = View.VISIBLE
    }

'''
s = cut(s, "    private fun refreshBadge() {\n", "    private fun onBadgeClick()", new_refresh)
open(p, "w", encoding="utf-8").write(s)
print("   OK")
PYEOF
python3 /tmp/rp_edit.py "$F"
rm -f /tmp/rp_edit.py
echo ""
echo "✅ Selesai. Lanjut:"
echo "   git add -A && git commit -m 'Angka jumlah Red Packet' && git pull --rebase && git push"
