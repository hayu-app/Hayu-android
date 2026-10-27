#!/data/data/com.termux/files/usr/bin/bash
# HAYU - Perbaiki aset online yang "tidak bisa diunduh"
# Penyebab: jsDelivr menolak melayani repo GitHub yang ukurannya > 50MB (repo ini ±80MB karena remote-assets/).
# Perbaikan: alamat utama = raw.githubusercontent.com, cadangan = jsDelivr. Kalau satu gagal, coba yang lain.
# Menimpa: VipAssets.kt dan SvgaPlayer.kt (backup otomatis).
# Jalankan di root repo:
#   bash pasang-aset-host.sh                    (user/repo ditebak dari git remote origin)
#   bash pasang-aset-host.sh hayu-app/Hayu-android
# SYARAT: repo GitHub PUBLIC dan remote-assets/ sudah di-push.
set -e
cd "${REPO:-$PWD}"
D="app/src/main/java/com/hayu/app"
for f in VipAssets SvgaPlayer; do
  [ -f "$D/$f.kt" ] || { echo "❌ $D/$f.kt tidak ditemukan. Jalankan di root repo Hayu-android"; exit 1; }
done
grep -q "fun url(" "$D/VipAssets.kt" || { echo "❌ VipAssets.kt belum versi online. Jalankan dulu: bash pasang-aset-remote.sh"; exit 1; }

# --- cari user/repo ---
SLUG="$1"
if [ -z "$SLUG" ]; then
  REMOTE="$(git remote get-url origin 2>/dev/null || true)"
  SLUG="$(echo "$REMOTE" | sed -E 's#^(https?://([^@/]*@)?github\.com/|git@github\.com:)##; s#\.git$##; s#/$##')"
  case "$SLUG" in */*) ;; *) SLUG="" ;; esac
fi
if [ -z "$SLUG" ]; then
  # ambil dari alamat lama di VipAssets.kt
  OLD="$(grep -o 'cdn.jsdelivr.net/gh/[^"]*' "$D/VipAssets.kt" | head -1)"
  SLUG="$(echo "$OLD" | sed -E 's#cdn\.jsdelivr\.net/gh/([^@/]+/[^@/]+)@.*#\1#')"
  case "$SLUG" in */*) ;; *) SLUG="" ;; esac
fi
[ -n "$SLUG" ] || { echo "❌ user/repo tidak terbaca. Jalankan: bash pasang-aset-host.sh USER/REPO"; exit 1; }
RAW="https://raw.githubusercontent.com/$SLUG/main/remote-assets/"
CDN="https://cdn.jsdelivr.net/gh/$SLUG@main/remote-assets/"
echo "Alamat utama   : $RAW"
echo "Alamat cadangan: $CDN"

T="$(mktemp -d)"
cat > "$T/b.b64" <<'B64_EOF'
H4sIAAAAAAAAA+07aW/bSJb92b+iWggaZFamZMdOFt44XsVXjCi2YSvpxmIBoSSWpIookkMWZXs8/u/7Xh28JR+TZGexfOi2JLKOV+8+KjQMO7/9ZOgCvNvdlZ8A5U/5fWt3+113e2vrze6b37pb3Tdv3vxGdn82YghJLGhEyG9REIh14x57/38UKPA/jsY/VQaez/+tre3thv+/Agz/F5T7P0sIns//7e7O24b/vwIK/P9Ol/QnCMEL+P/u7buG/78CqvwfB4sfLAPP5/+b3Z3dhv+/Aur5P6N3yY8Tgufzf2d3q4n/fgms4T/9UanB8/i/A/yHHzsN/38FPML/bzzsxTETsTN/+emRwW93dlbwf2unu/u2xP93u29B/7s/8Jwr4f85/0M6ntMpI8B1B7nuANc3NvgiDCJBqO9GAXedceAL5gvnED9vRfn1NKLhjI9j5wsVEb8tv15yduN8gz+nUZCE5bc33J0y4ZwtAAscZN6PA+45HJ/2A+qyqPDcg0eFBxH7W8JivcyV+lEYEPO/M+eIL5gf88CvvrqGP9nThTMNgqnHnAmP2IjG6kssgggWCcYJLCOufRrGs0A8bdaJfnRinmxsdF6/3iCvSQ/Ui3w7uyR/kGv8sLxgGrTJgrnU420y4v50TvEL9X0WtYnrebYDE3HuCfeYf0fJ4Oyo95l4dMqJC/9Rjy5I7/IzseIkpPAev8/ZmMNM0meCznESjJwEHpCWRGwRCLZJpaZ37pc8bMfw5wG3gFERCwNyysWnZARr+/CILkbcI4HvcZ8Rj91QQfBwXyOvDVh4CQzZHNPxjJFABAsqeEysQ6C1PONp75JGMYvkKQjZJD0f+Bxz4sTLKaIVJqiSatlreHTp0TvA0mX+FHb/etUnQCJYsiUR3ut0ANfOJKIL1nkj12jphY/oBFeaAJXIHfWnhLp0j+hjqkMPue+yW0fcCmJJCgGB+SiBnVPMA4/NSEhjWABIxMSmmunEM1vvo88OvIoRf8BScCKoH1KShC4VjIBW7ZGU9wR/D0GrJnzaUegQwJJ5LjFrwcqdjWD0nY1BOIwRJvcbBAAmxoIsqUcur45Pzv4i+xkpWqURX3p/DfvH3477MOjfN+TLTof0QEDgiEDnBSVWRG+cKRezZJQAW4yygzzbkt1joJqkvPU9PmIeXwLniPkKcuoHHp0rIflAdrtfPrbNNt8pSg+9SYWHhxRkGVg74jPiJl7ikM/MTagSRw4ysJrqswD0O545cvEw4kuka3bQo+OT3tf+YPixd308RBkBogzTX8Nha8W8k16//7F3+Lk0MX2sJ8vZ//kNjipAmuSvJYiW5tYeuQbLBwK2X8FDDs1vDWfRfHj9mpxKUaGKHXhCXASPq8U/ExmrIjKO3txua1kLWQSqF3EPyAejAbkICAxyhLtNEh94NIHlZodyFcvW4oRQMU8OGOUzoBH1x8yyQRg8D0QRTKfVyhBp2Y6rraGlJDCGRzDTstOVERzquhf+dTIeszju8xjkC/T5nrhk80NhoCKrRxIgpIsLKbJaLX3Uln3gwJMFYBREx4tQ3JV2QuATYiUOunUR/wlibbVmQoQt2071dF+PYb6rR3TwdUKYFzPSepVoNTLwsKH+pmyTNoihYQ5J4otkDpIqZpJxYLl57JBWXDFKBVYkkWfhHCM5dk6EDJr/JlfNdtVaa/SxnepTqjRzkKTEiJPS7imdUq+w9YR63gjc/tc1KFS1Io8MrgLH67Ml8yyQgD1Sdoqw1pkvyH5KRhyGDO0HyE4zuWWTgz3S7duOCGC8FDSwQOzMt7rtzHbZ2bbxM/dF2wlyu+TizinMzZDBx5cB91F4DTrr0EBWfFReWXkVoHPIwQZoV62tFhjMGASMR1xauv/QzJkHMexKFOPMjBG9oTBe+v8OBgNFhqEUIbdWHVmzLVNn1KHgxkctUic1egSREQ9D5p7oJdWJW5m4o2LATIfH54HQGmaD4RBJ5OOShS3iJexQoKpdWAjefyDddHrr1b1yWA855XgVL43Pzq2MC69ed9WyuVXLi5qxrbIqH0/YnED4AToMIf6CWDPpjSQrJHfDBH/TfIxiF9gDvIjuns2edbTTyOZJWKKd3DOjnTZchcMhaoKz6Bzo8eNQq2OrpBXg8lQW5o5nTidXWNaeA+KIShwnGASQEIaaENSCmI2OKSgcKBkGwKB0K8I8u8aTG9eMHl0OBMIw8V4R58MBHMRPPG+jMBbJK8daYwHjdWpkF2bm6CqHHjgeePb7lABCe5aU8BDCFebvE2BzbhUE2A6TNI+PKfpjvbGjY4IgZOCiy4cGrzxKJhMWMfeKYTYFlhZivT7QDoJK4snPGl8sXzgL8HL3gK12veQB8hpPSA8OD/OWAl6J4BONZ9eVGCA76QN4MDGeEYvtkePbMQvxGHbplAwXLK7yUCQmyiiEUiWhMo9yGt5DW6ucMyYAB+SzNMSuEirBXYheXS7YIpkrpwoSNoUjw9iint9C5BIX2N0mJQf6MYC4FSx5UaG4i9im0lLVBPcWyGho+I9/qGiCyxdllcbU18KUGjydyZlLaOR3x9xbTjEBh12mtKfjMSvwj6MoiADTezJskyEIRG5+OWqAdR7KvHmoURHEcQ3N2uRGuuw2mclPO3esMhXRoqQvi4TkYAjH1GODu5DlRznX5qlzcjYYHh6fD46v8rMgtQwSAQkpXcQwMS1TOP3cCwsoPMs2k/TgS3WQKjOXZQEc0KmycH5LR4uggZCF++D33SlYL4z6IXXf3nHDomsB+wmTS+Tz0KIaojE+nYmjUIc9sMQaArowAK0HhPhBAuFN7Lg8DoECXxjwYgw/sToi7gqTZjDJMttgPSCN1SoHN6xuEwzwOoJOO6/uJbZZPLWVj6cenBs2Cltt2ABW3nbe7E7S1VOK5wj5UZJL+ZvHaPm2TEt0aHKB55Dz7b8GOaXPlwd8JkW3366n6CldjKQRlESzVI3JRnMoY9o5VnvQmY7oFMwi6Q1610gxnAOOJpkVKCyC8DAKwvXazl0xu7wt0Fv/fLnqP1GJ1dbZrs8yH196g6uzv9IZKp2WdUYryY43pv4VcP8u9QRla4unicwWulDpfEw41uGK5zLguFRQK6l5gYVLCwuX2dHSCqfzFbzNBNy3a9fMBMeHWXpgcnJp890IspARhFg1gYAhEhMS8SM91DJzqnuYw95gEqJHOZBkAZ1iPv4TEa7fBaLCGxUV3tcOMAsj0vrcIOEnYJRBxiF7ulk5Cw4glUqVqWG6+gKxEMZTEGdh9CA5bsVtEtu5iCMP1acPNSRe61bxkEZU7FSOKi62jbl6zOzaDUYoNKUgC81RrmbuMB9WTpgV1UVRqfhm24koYasd2sPG/3a3ooEfDY/0/7Ly+z/RAFzf/9vudne2yv2/d923Tf/vV8CT+n/wzDFVtGf2Bkuv/4xgrayZV2kR/pjeYen9FcNcf8lUNJDv12G2rII3WUcJlaRji+pQ2+Knja7pXq4brvpfTxv7jbssOPZFjvSopg4PHOz+nflhgtU9RheF1z6cG7sPpoeU66PdV2oeHo3FIIA/4KS6/Sw6vGQxBH2yggzBtpvoJNoUXRZ0Hjtk6xYbD2RrFyJxwedEtxxVXr1gEF/637EICpvTKAvJIRRgkQ8Lq8gRNr/AVkMxdlzE09qsFqb5AUYW13fgZRfOOIkikLIBRD9fuAeu1ypWrHDwZu6Y7wHdIdieviliZbldjhQwK31uRCqInU/w3QPHnnvUD4CBERZav4AdVb8s23bCABYqhjEl2ZR7OUBJNoATW/XFHUmIdv3U/vH56eDTsH9xfmo78Sy4qVRMcuG+B5hh3LRlKmWy1domXew6sCjxdHEtAb6idEEioPj+R66ji425TIoLGQDKrVUoPuyRgn6005dJ1jXLHkr8TPK1lT0P/BPu8xjyCAtiPAigvvpc2KYwlx8nY636YXJUXoyqtTUULFDH0GNIfZintWcvf2D5cZiNqoapwZJFEeitpAlyUY5l1hJ1+QwEVq2XU+1Vwa60f6Bz38zMbI0VIbcq2Cg2y8/Vw8baxpXPaWzfmgC8dMJLmsQMG4r1EXPNDMVP5uKk9OcBJAjLYI4rPXmhKxYyGfk/fe9rwUJL9gl0GhpiKu0LkFEskieYz6xarv6pYhN2HOVdBtknrfJHK+IazKT0WquEQb/PqFSzw+pfKNuhlGC0m6k4W0oUdPm6MEM2SSMv30rN7oOpbkSlmojQ6Sg/obpZWK3ZQ/OB7e3Ud4wSWVvIXVapnkZhLLBkgmhgUXvJLiNIa2+riNTORilOQlkSMMMrSVaVKYD/OBjRQh/VtO+UM8y/Nu3Y4ojvyZTKBjzJKikhutPKbrLDx8R49qw6ggHFUcdl48BlJ1GwAKdv4f8JpHMvs14GXmLF9nPm06mbU2+1arZbrwoG8ok0+eMPw/DfQWIemyppL+muJplke+2kB9WhenzlLKLJ61ebtOR1L4ZNRxUh6TtD0s3u/bf/KmmtxwChQGNNqPVor3y7wso9ZloQFPEyzVpZR5DTFeGQXxiKXss7JjyWn7I5scqSSHq5PIbgVhmM3JWYMBABqp709XESg+DIK0eyUaiiFXkbTUY5tQaCu3XGJcNwlXHJRmDkjqFSic/cRccmEVlVTgNSyPf7KjxZJ6+PW/4cjVcvUw14ylAxJ7n8wnpULksJiTweSENrEg9f3eMPxwen+9Bq5yRYCcvapVfL9pNaiWV4GjHl8k+t/9WJehWJ1bZaXuLKaPJPdU5XH8+kAw/y7ml67VImZqqcX0gLiDXlE8w9ArzsmV2RgBxuoJM7fzqjmDNwvOoikhn1ndy9RVSSC7DqsEEp6cyu3w16p3jhDksPQ8x8h4Ga0Cq2EmXrROVdMPxFyVhlvRBv58jbTjJjTgT3nF4U0bsjLIy+v6Q8ep8mYLoj/8EqrxORURLfwSLSe+S6zj7owJ6y9OZKa0xFIpNl+cXKSNomc0CGRlQ2oNEWFLLoGHgCiC6wqw8Gr3T7RJV4teXJJc+5DCsvI/rYeCfPTAJvhaOLOfPveC5bpnTnmJoWekf5Fm82oty1AX8PhDEbhoHnlfJyNaLe/hXImn9Rytgz9dAzCvYEGUTdJV5jdCurIZL6JZbnK8Hv72ZmnUnJrVprwUqEq6KbfZNpO9ICL4zH6D3wewx2wnfbZqNsGa0IsrpwxLCo41r3ZhjmTu2svAEeNMR70wtwnvp2oFb7BR2DZ+1guq/8aJ6/smGKWK0VK4gw/aPAZ/X5dlkcqK4gyiBRXVqZcN81hcWSaGSj1Wp4LcI8g8BBJYyoFcXnRxAbRMFdlWUK0xVGf6VIyXZdEGA5It3kBpAObqQZj2SvksYHWc+xcAg1tV6+n4dQ+nUSgGHmeDkEF3fGM+65h0HiC7JJtggg5g+CavPMIINW8RCn9ITFbQdSXkQPjLCtllNhEB5GDqhDQN7twmPvF0s7KceyWTIkknvgFsXHL7iuITEEsyV3K+a2hdISwooecEVL68dht/fw0/CydwUYtF8yqTAno4idp2ME7PJZrVVSfKgYJUk5VVQkKuCEmAI8b1EE7YMcH+Wwcj1FGjfcvM6waaRqjdpaoS3ugbagRqNyHSVZLZSsQ6MCRqudVoJgf3WENsn6p+pJSbyKRlANUTZwu/s0G7jKrx3WGqk9Yr4fFOxbRHQROjWVSMMZz+pvNzOM9i09jPCYFJsidTqbG2y2Te9d6neVIo+v9tav5b8SMM2Z8vJq6H462CYjCNznhXEGhX25cA1Hi+hIUUybRWqkDjjNpWk0G5jJUUFjAqEXhkbyWueIRdSlWVWo2Lgph5byDvPTA0tZ8Gtl7o0KARmipfYvXKbLebiDshPT+rav8a7VP7xSXTLgmdVWE55jt/WM1ZZbD1htu/OrYbLL43NwSBdR6X530cZ7YXbKwgUbPGuJM3kzWHN63NcLHXlbg7zfJ1102vBAXcSRTyqNGHnFg/8dLVE29zXZcnZyl8Se4pXUCSoFzh/sm3B/KWF9JNsa8lh4qLY8WingUYhyF6tXcsPzi+HZUVkiwJidzRNBwO7hv5fS6uOiLfMpXlaH7x7+Ez+sOFpz7LxhjAfmb1a01hpbdKhXicesEs69/tnp+bB/fDJokxS1F6wwuLh8dAGPTSBXi6YcWy6bkkLYrtNcx0s927XzRBDWTlNyVZxXm5U/cgLF4+Gni6uz/7o4H/T69ejn0fDyPzfJY0hlWZnSYRPdKK+oN8iJ+Rrv2c27yvv8yKwWWVSGNmkZk6yK1i5fJFS00vtGzZWfBhpooIEGGmiggQYaaKCBBhpooIEGGmiggQYaaKCBBhpooIEGGmiggQYaaKCBBhpooIEGGviXgv8BmV5U+wB4AAA=
B64_EOF
base64 -d "$T/b.b64" > "$T/b.tgz"
mkdir "$T/new" && tar xzf "$T/b.tgz" -C "$T/new"

BK="$HOME/.hayu-backup-host-$(date +%H%M%S)"
mkdir -p "$BK/$D"
for f in VipAssets SvgaPlayer; do cp "$D/$f.kt" "$BK/$D/$f.kt"; cp "$T/new/$D/$f.kt" "$D/$f.kt"; done
rm -rf "$T"
sed -i "s#__BASE_URL__#$RAW#; s#__FALLBACK_URL__#$CDN#" "$D/VipAssets.kt"
grep -q "__BASE_URL__\|__FALLBACK_URL__" "$D/VipAssets.kt" && { echo "❌ Gagal mengisi alamat"; exit 1; }
echo "  dipasang  $D/VipAssets.kt, SvgaPlayer.kt"

echo ""
echo "✅ Selesai. Backup: $BK"
echo "Lanjut: git add -A && git commit -m 'aset online via raw github' && git push"
echo ""
echo "CEK DULU di browser HP (harus muncul gambar, bukan 404):"
echo "  ${RAW}vip/hero_arch.webp"
echo "Kalau 404: repo masih PRIVATE (jadikan Public di GitHub > Settings > Danger Zone) atau remote-assets/ belum ter-push."
