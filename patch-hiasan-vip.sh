#!/data/data/com.termux/files/usr/bin/bash
# HAYU - Patch: hiasan VIP & SVIP masuk ke Milikku (Hiasan Saya), TIDAK terpasang otomatis
#  - Bingkai VIP/SVIP dan Efek Masuk SVIP yang sudah didapat tampil di Milikku -> ketuk Pakai / Lepas.
#  - Gelar VIP (⭐ VIP n) juga masuk tab Gelar di Milikku.
#  - Tidak ada lagi bingkai / efek masuk / gelar yang terpasang sendiri dari level VIP / SVIP.
#   cd ~/Hayu-android && bash /sdcard/Download/patch-hiasan-vip.sh
set -e
REPO="${REPO:-$PWD}"
cd "$REPO"
command -v python3 >/dev/null 2>&1 || pkg install -y python

python3 - <<'PY'
import sys
D = "app/src/main/java/com/hayu/app/"

def load(f):
    return open(D + f, encoding="utf-8").read()

def save(f, s):
    open(D + f, "w", encoding="utf-8").write(s)

def rep(s, f, old, new):
    if s.count(old) != 1:
        sys.exit("❌ %s: bagian tidak ditemukan / ganda:\n%s" % (f, old))
    return s.replace(old, new)

# ---------- 1) VipAssets: tidak ada lagi bingkai / efek masuk otomatis ----------
f = "VipAssets.kt"
s = load(f)
if "tidak lagi otomatis" in s:
    print("   VipAssets.kt (sudah, dilewati)")
else:
    s = rep(s, f, '''    /** Bingkai yang tampil: bingkai pasangan sendiri dulu; kalau kosong pakai bingkai bawaan SVIP / VIP. */
    fun frameUrl(doc: DocumentSnapshot): String {
        val own = doc.getString("equippedFrameUrl") ?: ""
        if (own.isNotEmpty()) return own
        val sv = svipLevel(doc)
        if (sv > 0) return "${PREFIX}svip/frame/$sv.svga"
        val v = vipLevel(doc)
        if (v > 0) return "${PREFIX}vip/frame/$v.svga"
        return ""
    }
''', '''    /** Bingkai yang tampil: hanya yang DIPAKAI user lewat Milikku (tidak lagi otomatis dari VIP / SVIP). */
    fun frameUrl(doc: DocumentSnapshot): String = doc.getString("equippedFrameUrl") ?: ""
''')
    s = rep(s, f, '''    /** Efek masuk room (hanya SVIP yang punya animasi .svga). */
    fun entryUrl(doc: DocumentSnapshot): String {
        val sv = svipLevel(doc)
        return if (sv > 0) "${PREFIX}svip/entry/$sv.svga" else ""
    }
''', '''    /** Efek masuk room: hanya yang DIPAKAI user lewat Milikku (tidak lagi otomatis dari SVIP). */
    fun entryUrl(doc: DocumentSnapshot): String = doc.getString("equippedEntryUrl") ?: ""
''')
    save(f, s)
    print("   OK  VipAssets.kt")

# ---------- 2) VipActivity: beli VIP tidak lagi memasang gelar sendiri ----------
f = "VipActivity.kt"
s = load(f)
old = '''            val title = s.getString("equippedTitle") ?: ""
            if (title.isBlank() || title.startsWith("⭐ VIP")) upd["equippedTitle"] = "⭐ VIP$level"
            tx.update(ref, upd)'''
if old not in s:
    print("   VipActivity.kt (sudah, dilewati)")
else:
    s = rep(s, f, old, "            tx.update(ref, upd)")
    save(f, s)
    print("   OK  VipActivity.kt")

# ---------- 3) HiasanActivity: item VIP / SVIP masuk Milikku ----------
f = "HiasanActivity.kt"
s = load(f)
if "vipCards" in s:
    print("   HiasanActivity.kt (sudah, dilewati)")
else:
    # assetCards
    s = rep(s, f, "        return assets\n            .filter { it.type == type &&",
                  "        val catalogCards: List<View> = assets\n            .filter { it.type == type &&")
    s = rep(s, f, '''                ) { if (on) equipSvga(field, "", "") else equipSvga(field, a.url, a.name) }
            }
    }
''', '''                ) { if (on) equipSvga(field, "", "") else equipSvga(field, a.url, a.name) }
            }
        return vipCards(u, field, equippedUrl) + catalogCards
    }

    /** Bingkai VIP / SVIP dan Efek Masuk SVIP yang sudah didapat: masuk Milikku, dipakai manual. */
    private fun vipCards(u: DocumentSnapshot, field: String, equippedUrl: String): List<View> {
        val kind = when (tab) {
            "frame" -> "frame"
            "entry" -> "entry"
            else -> return emptyList()
        }
        val out = ArrayList<View>()
        fun add(tier: String, lv: Int) {
            val base = "${tier.lowercase()}/$kind/$lv"
            if (!VipAssets.exists(this, "$base.svga") && !VipAssets.exists(this, "$base.webp")) return
            val url = VipAssets.PREFIX + "$base.svga"
            val on = equippedUrl == url
            val label = if (kind == "frame") "Bingkai $tier $lv" else "Efek Masuk $tier $lv"
            out.add(
                itemCard(
                    status = "$tier $lv",
                    emoji = if (tier == "SVIP") "👑" else "⭐",
                    name = label,
                    svgaUrl = null,
                    on = on,
                    imagePath = "$base.webp"
                ) { if (on) equipSvga(field, "", "") else equipSvga(field, url, label) }
            )
        }
        for (lv in VipAssets.svipLevel(u) downTo 1) add("SVIP", lv)
        if (kind == "frame") for (lv in VipAssets.vipLevel(u) downTo 1) add("VIP", lv)
        return out
    }
''')
    # titleCards
    s = rep(s, f, "        return ShopUtil.TITLES.filter { it.id in owned }.map { t ->",
                  "        val shopTitles: List<View> = ShopUtil.TITLES.filter { it.id in owned }.map { t ->")
    s = rep(s, f, "            }\n        }\n    }\n\n    private fun itemCard(", '''            }
        }
        return vipTitleCards(u, equipped) + shopTitles
    }

    /** Gelar VIP yang sudah didapat: dipakai manual. */
    private fun vipTitleCards(u: DocumentSnapshot, equipped: String): List<View> {
        val out = ArrayList<View>()
        for (lv in VipAssets.vipLevel(u) downTo 1) {
            val label = "⭐ VIP$lv"
            val on = equipped == label
            out.add(itemCard("Sesuai level VIP", "⭐", "VIP $lv", null, on) {
                val uid = auth.currentUser?.uid ?: return@itemCard
                db.collection("users").document(uid)
                    .update("equippedTitle", if (on) "" else label)
                    .addOnSuccessListener { load() }
                    .addOnFailureListener { toast("Gagal: ${it.message}") }
            })
        }
        return out
    }

    private fun itemCard(''')
    # itemCard: parameter gambar statis
    s = rep(s, f, "        status: String, emoji: String, name: String, svgaUrl: String?, on: Boolean, onToggle: () -> Unit\n    ): View {",
                  "        status: String, emoji: String, name: String, svgaUrl: String?, on: Boolean,\n        imagePath: String? = null, onToggle: () -> Unit\n    ): View {")
    s = rep(s, f, "        stage.addView(fb, FrameLayout.LayoutParams(-1, -1))\n",
                  "        stage.addView(fb, FrameLayout.LayoutParams(-1, -1))\n"
                  "        if (imagePath != null) {\n"
                  "            val img = ImageView(this)\n"
                  "            img.scaleType = ImageView.ScaleType.FIT_CENTER\n"
                  "            VipAssets.load(img, imagePath)\n"
                  "            stage.addView(img, FrameLayout.LayoutParams(-1, -1))\n"
                  "        }\n")
    save(f, s)
    print("   OK  HiasanActivity.kt")
PY

echo ""
echo "✅ Selesai. Lanjut:"
echo "   git add -A && git commit -m 'Hiasan VIP/SVIP masuk Milikku, tidak otomatis' && git pull --rebase && git push"
