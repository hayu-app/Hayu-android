#!/data/data/com.termux/files/usr/bin/bash
# HAYU - Pencatat crash (untuk mencari penyebab force close)
# Setelah app crash lalu dibuka lagi, muncul dialog berisi teks error. Tekan "Salin", tempel ke chat.
# Jalankan di root repo:  bash pasang-crash-log.sh
set -e
cd "${REPO:-$PWD}"
D="app/src/main/java/com/hayu/app"
H="$D/HayuApp.kt"
[ -f "$H" ] || { echo "❌ Jalankan di root repo Hayu-android"; exit 1; }
T="$(mktemp -d)"
cat > "$T/b.b64" <<'B64_EOF'
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
B64_EOF
base64 -d "$T/b.b64" > "$T/b.tgz"
mkdir "$T/new" && tar xzf "$T/b.tgz" -C "$T/new"
cp "$T/new/$D/CrashReporter.kt" "$D/CrashReporter.kt"
rm -rf "$T"
echo "  dipasang  $D/CrashReporter.kt"

if grep -q "CrashReporter.install" "$H"; then
  echo "  ok        $H"
else
  BK="$HOME/.hayu-backup-crash-$(date +%H%M%S)"
  mkdir -p "$BK" && cp "$H" "$BK/HayuApp.kt"
  sed -i 's#^\( *\)super.onCreate()$#\1super.onCreate()\n\1CrashReporter.install(this)#' "$H"
  grep -q "CrashReporter.install" "$H" || { cp "$BK/HayuApp.kt" "$H"; echo "❌ Baris super.onCreate() tidak ditemukan di $H"; exit 1; }
  echo "  DIPERBAIKI $H (backup: $BK)"
fi
echo ""
echo "✅ Selesai."
echo "Lanjut: git add -A && git commit -m 'pencatat crash' && git push"
echo "Setelah APK terpasang: buka fitur yang bikin force close, lalu buka app lagi -> tekan 'Salin' -> tempel ke chat."
