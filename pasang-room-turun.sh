#!/data/data/com.termux/files/usr/bin/bash
# HAYU - Turunkan kursi mic di room supaya bingkai SVGA tidak menabrak header
# Jalankan di root repo:  bash pasang-room-turun.sh
set -e
cd "${REPO:-$PWD}"
F="app/src/main/res/layout/activity_room.xml"
[ -f "$F" ] || { echo "❌ Jalankan di root repo Hayu-android"; exit 1; }
if grep -q 'android:paddingTop="34dp" android:paddingBottom="10dp"' "$F"; then
  echo "ok, sudah terpasang"; exit 0
fi
grep -q 'android:paddingTop="14dp" android:paddingBottom="10dp"' "$F" || { echo "❌ Baris kursi mic tidak ditemukan"; exit 1; }
cp "$F" "$HOME/activity_room.xml.bak"
sed -i 's#android:paddingTop="14dp" android:paddingBottom="10dp"#android:paddingTop="34dp" android:paddingBottom="10dp"#' "$F"
echo "✅ Kursi mic diturunkan (backup: ~/activity_room.xml.bak)"
echo "Lanjut: git add -A && git commit -m 'turunkan kursi mic' && git push"
