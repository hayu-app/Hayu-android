#!/data/data/com.termux/files/usr/bin/bash
# HAYU - Patch: saldo koin tidak lagi berubah negatif (-) kalau sangat besar
#  Penyebab: saldo dibaca sebagai Int (maks ±2,1 miliar) lalu meluap jadi negatif.
#  Perbaikan: semua tampilan / pengecekan saldo memakai Long.
#   cd ~/Hayu-android && bash /sdcard/Download/patch-coin-long.sh
set -e
REPO="${REPO:-$PWD}"
cd "$REPO"
D="app/src/main/java/com/hayu/app"
[ -f "$D/ProfileFragment.kt" ] || { echo "❌ Jalankan di root repo Hayu-android"; exit 1; }
command -v python3 >/dev/null 2>&1 || pkg install -y python

python3 - <<'PY'
import sys
D = "app/src/main/java/com/hayu/app/"
edits = [
 ("ProfileFragment.kt",
  'val coins = (doc.getLong("coins") ?: 100L).toInt()',
  'val coins = doc.getLong("coins") ?: 100L'),
 ("SimplePageActivity.kt",
  'val coins = (doc.getLong("coins") ?: 0L).toInt()',
  'val coins = doc.getLong("coins") ?: 0L'),
 ("TokoActivity.kt",
  'val coins = (user.getLong("coins") ?: 0L).toInt()',
  'val coins = user.getLong("coins") ?: 0L'),
 ("VipActivity.kt",
  'val coins = (user.getLong("coins") ?: 0L).toInt()',
  'val coins = user.getLong("coins") ?: 0L'),
 ("GiftSheet.kt",
  'val coins = (me.getLong("coins") ?: 0L).toInt()',
  'val coins = me.getLong("coins") ?: 0L'),
 ("GreedyEatActivity.kt",
  'private var balance = 0\n',
  'private var balance = 0L\n'),
 ("GreedyEatActivity.kt",
  'balance = (snap.getLong("coins") ?: 0L).toInt()',
  'balance = snap.getLong("coins") ?: 0L'),
 ("LionVsTigerActivity.kt",
  'private var balance = 0\n',
  'private var balance = 0L\n'),
 ("LionVsTigerActivity.kt",
  'balance = (snap.getLong("coins") ?: 0L).toInt()',
  'balance = snap.getLong("coins") ?: 0L'),
 ("model/User.kt",
  'val coins: Int = 0,\n    val totalCoinsEarned: Int = 0,',
  'val coins: Long = 0L,\n    val totalCoinsEarned: Long = 0L,'),
 ("model/User.kt",
  'coins = (map["coins"] as? Number)?.toInt() ?: 0,\n            totalCoinsEarned = (map["totalCoinsEarned"] as? Number)?.toInt() ?: 0,',
  'coins = (map["coins"] as? Number)?.toLong() ?: 0L,\n            totalCoinsEarned = (map["totalCoinsEarned"] as? Number)?.toLong() ?: 0L,'),
]
done = skipped = 0
for f, old, new in edits:
    p = D + f
    s = open(p, encoding="utf-8").read()
    if s.count(old) == 0 and s.count(new) >= 1:
        skipped += 1
        continue
    if s.count(old) != 1:
        sys.exit("❌ %s: bagian tidak ditemukan / ganda:\n%s" % (f, old))
    open(p, "w", encoding="utf-8").write(s.replace(old, new))
    done += 1
    print("   OK  " + f)
print("   %d diubah, %d sudah sesuai" % (done, skipped))
PY

echo ""
echo "✅ Selesai. Lanjut:"
echo "   git add -A && git commit -m 'Saldo koin pakai Long' && git pull --rebase && git push"
