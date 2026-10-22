#!/data/data/com.termux/files/usr/bin/bash
# Hapus deklarasi ganda (myRoles & usersByRole) penyebab error "Conflicting declarations"
# Jalankan dari folder root repo Hayu-android:  bash fix-dup.sh
set -e
D="app/src/main/java/com/hayu/app"
[ -d "$D" ] || { echo "Jalankan dari root repo Hayu-android"; exit 1; }
command -v python3 >/dev/null || pkg install -y python

python3 - "$D" <<'PY'
import sys, re
d = sys.argv[1]

def dedupe_prop(path):
    lines = open(path, encoding="utf-8").read().split("\n")
    out, prev = [], None
    for l in lines:
        if l.strip().endswith("var myRoles: Set<String> = emptySet()") and prev == l:
            continue
        out.append(l); prev = l
    open(path, "w", encoding="utf-8").write("\n".join(out))

for f in ("CoinAdminActivity.kt", "UserAdminActivity.kt", "ListPageActivity.kt"):
    dedupe_prop(f"{d}/{f}")

# Hapus salinan kedua usersByRole (beserta komentar KDoc-nya) di ListPageActivity
p = f"{d}/ListPageActivity.kt"
s = open(p, encoding="utf-8").read()
m = re.search(r"    /\*\* User yang memegang role tertentu[^\n]*\n    protected fun usersByRole\(.*?\n    }\n\n", s, re.S)
block = m.group(0)
first = s.find(block)
second = s.find(block, first + len(block))
if second != -1:
    s = s[:second] + s[second + len(block):]
open(p, "w", encoding="utf-8").write(s)
print("OK: duplikat dihapus")
PY

echo "--- cek ---"
grep -n "var myRoles" $D/CoinAdminActivity.kt $D/UserAdminActivity.kt $D/ListPageActivity.kt
grep -n "fun usersByRole" $D/ListPageActivity.kt
