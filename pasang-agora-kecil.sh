#!/data/data/com.termux/files/usr/bin/bash
# HAYU - Perkecil APK setelah pasang Agora
#  1. io.agora.rtc:full-sdk (audio+video+semua ekstensi AI) -> voice-rtc-basic (audio saja, tanpa ekstensi)
#  2. hanya bawa library native untuk HP 64-bit (arm64-v8a); tanpa x86 / x86_64 / 32-bit
#  3. library native dikompres di dalam APK (unduhan artifact lebih kecil)
# Kode AgoraVoiceEngine.kt tidak perlu diubah (hanya memakai API suara).
#
# Jalankan di root repo:
#   bash pasang-agora-kecil.sh                        (arm64-v8a saja = paling kecil)
#   bash pasang-agora-kecil.sh arm64-v8a,armeabi-v7a  (kalau HP 32-bit lama juga harus bisa pasang)
#   AGORA_VER=4.6.4 bash pasang-agora-kecil.sh        (versi lain, default 4.5.2)
set -e
cd "${REPO:-$PWD}"
G="app/build.gradle.kts"
[ -f "$G" ] || { echo "❌ Jalankan di root repo Hayu-android"; exit 1; }

echo "== Kondisi sekarang =="
grep -n "agora\|abiFilters\|useLegacyPackaging" "$G" || echo "(tidak ada baris agora di $G)"
for d in app/libs app/src/main/jniLibs; do [ -d "$d" ] && { echo "folder $d:"; du -sh "$d"; }; done
echo ""

BK="$HOME/.hayu-backup-agora-$(date +%H%M%S)"
mkdir -p "$BK" && cp "$G" "$BK/build.gradle.kts"

ABIS="${1:-arm64-v8a}"
LIST="$(echo "$ABIS" | sed 's/,/", "/g; s/^/"/; s/$/"/')"
AGORA_VER="${AGORA_VER:-4.5.2}"

# 1. dependency: full-sdk / voice-sdk / lite-sdk -> voice-rtc-basic (tanpa ekstensi)
sed -i -E "s#io\.agora\.rtc:(full-sdk|voice-sdk|lite-sdk):[0-9.]+#io.agora.rtc:voice-rtc-basic:$AGORA_VER#" "$G"
sed -i '/io\.agora\.rtc:full-screen-sharing/d' "$G"
grep -q "io.agora.rtc:voice-rtc-basic" "$G" || { cp "$BK/build.gradle.kts" "$G"; echo "❌ Baris dependency Agora tidak ditemukan di $G. Tidak ada yang diubah."; exit 1; }

# 2 + 3. ABI & packaging
NEED_NDK=1; grep -q "abiFilters" "$G" && NEED_NDK=0
NEED_PKG=1; grep -q "useLegacyPackaging" "$G" && NEED_PKG=0
DONE_NDK=0; DONE_PKG=0
: > "$G.new"
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in
    *"buildFeatures {"*)
      if [ "$NEED_PKG" = 1 ] && [ "$DONE_PKG" = 0 ]; then
        printf '%s\n' "    packaging {" "        jniLibs {" "            useLegacyPackaging = true" "        }" "    }" "" >> "$G.new"
        DONE_PKG=1
      fi ;;
  esac
  printf '%s\n' "$line" >> "$G.new"
  case "$line" in
    *"defaultConfig {"*)
      if [ "$NEED_NDK" = 1 ] && [ "$DONE_NDK" = 0 ]; then
        printf '%s\n' "        ndk { abiFilters += listOf($LIST) }" >> "$G.new"
        DONE_NDK=1
      fi ;;
  esac
done < "$G"

if { [ "$NEED_NDK" = 1 ] && [ "$DONE_NDK" = 0 ]; } || { [ "$NEED_PKG" = 1 ] && [ "$DONE_PKG" = 0 ]; }; then
  rm -f "$G.new"; cp "$BK/build.gradle.kts" "$G"
  echo "❌ Blok defaultConfig / buildFeatures tidak ditemukan di $G. Tidak ada yang diubah."
  echo "   Tambahkan manual di dalam android { }:"
  echo "     defaultConfig { ndk { abiFilters += listOf($LIST) } }"
  echo "     packaging { jniLibs { useLegacyPackaging = true } }"
  exit 1
fi
mv "$G.new" "$G"

echo "== Hasil =="
grep -n "agora\|abiFilters\|useLegacyPackaging" "$G"
echo ""
echo "✅ Selesai. Backup: $BK"
echo "Lanjut: git add -A && git commit -m 'perkecil APK (Agora voice-rtc-basic, arm64)' && git push"
echo "Ukuran baru: GitHub Actions > langkah 'Ukuran APK' (ada setelah pasang-aset-remote.sh)."
