#!/data/data/com.termux/files/usr/bin/bash
# HAYU - Tes API key imgbb + ganti di ImgbbUploader.kt
# Jalankan di root repo: bash perbaiki-foto.sh
set -e
KEY="${1:-f2b4a5671d7727f32ff5a533b27f5e82}"
F="app/src/main/java/com/hayu/app/util/ImgbbUploader.kt"
[ -f "$F" ] || { echo "Jalankan di root repo Hayu-android"; exit 1; }

IMG="$(mktemp /data/data/com.termux/files/usr/tmp/hayu.XXXXXX.png)"
printf 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==' | base64 -d > "$IMG"
RES="$(curl -s -F "image=@$IMG" "https://api.imgbb.com/1/upload?key=$KEY")"
rm -f "$IMG"

case "$RES" in
  *'"success":true'*|*i.ibb.co*)
    echo "Key OK, lanjut patch..." ;;
  *)
    echo "Key DITOLAK imgbb. Jawaban imgbb:"
    echo "$RES" | head -c 300; echo
    exit 1 ;;
esac

sed -i "s/private const val API_KEY = \"[^\"]*\"/private const val API_KEY = \"$KEY\"/" "$F"
grep -n "API_KEY =" "$F"
git add "$F"
git commit -m "fix: ganti API key imgbb" || true
git push
echo "SELESAI. Tunggu GitHub Actions build APK baru."
