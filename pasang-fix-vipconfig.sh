#!/data/data/com.termux/files/usr/bin/bash
# HAYU - Perbaiki force close di VipConfigActivity
# Penyebab: EditText(this) dibuat saat properti kelas dibuat, padahal activity belum siap (context masih null).
# Perbaikan: dibuat malas (by lazy), baru dibuat saat pertama dipakai di onCreate.
# Jalankan di root repo:  bash pasang-fix-vipconfig.sh
set -e
cd "${REPO:-$PWD}"
F="app/src/main/java/com/hayu/app/VipConfigActivity.kt"
[ -f "$F" ] || { echo "❌ $F tidak ditemukan. Jalankan di root repo Hayu-android"; exit 1; }
if grep -q "vipFields by lazy" "$F"; then
  echo "ok, sudah diperbaiki"; exit 0
fi
BK="$HOME/.hayu-backup-vipconfig-$(date +%H%M%S)"
mkdir -p "$BK" && cp "$F" "$BK/VipConfigActivity.kt"
sed -i 's#private val vipFields = Array(7) { EditText(this) }#private val vipFields by lazy { Array(7) { EditText(this) } }#' "$F"
sed -i 's#private val svipFields = Array(7) { EditText(this) }#private val svipFields by lazy { Array(7) { EditText(this) } }#' "$F"
if grep -q "vipFields by lazy" "$F" && grep -q "svipFields by lazy" "$F"; then
  echo "✅ Diperbaiki:"
  grep -n "Fields by lazy" "$F"
  echo "Backup: $BK"
  echo "Lanjut: git add -A && git commit -m 'fix force close VipConfigActivity' && git push"
else
  cp "$BK/VipConfigActivity.kt" "$F"
  echo "❌ Baris vipFields/svipFields tidak cocok. Tidak ada yang diubah."
  exit 1
fi
