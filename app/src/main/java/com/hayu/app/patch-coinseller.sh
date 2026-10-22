#!/data/data/com.termux/files/usr/bin/bash
# HAYU - Patch 8: COINSELLER, TOP UP, ROLE BD/HOST
#  - Coinseller: hapus Dashboard, Order Masuk, Refund Request; tambah "Isi Ulang Stok Koin"
#    (coinseller mengajukan, developer/superadmin/admin menyetujui -> wallet bertambah)
#  - Developer/Superadmin/Admin: bisa isi wallet coinseller & konfirmasi top up
#  - BD hanya bisa menambah role agency
#  - Role host hanya didapat otomatis setelah agency menyetujui
#   cd ~/Hayu-android && bash patch-coinseller.sh
set -e
REPO="${REPO:-$PWD}"
cd "$REPO"
D="app/src/main/java/com/hayu/app"
[ -d "$D" ] || { echo "❌ Jalankan di root repo Hayu-android"; exit 1; }
grep -q "seller_stock_requests" "$D/CoinAdminActivity.kt" && { echo "✅ Patch ini sudah terpasang, tidak ada yang diubah."; exit 0; }
command -v git >/dev/null 2>&1 || pkg install -y git
command -v patch >/dev/null 2>&1 || pkg install -y patch

P="$(mktemp)"
cat > "$P" <<'PATCH_EOF'
diff --git a/app/src/main/java/com/hayu/app/CoinAdminActivity.kt b/app/src/main/java/com/hayu/app/CoinAdminActivity.kt
index bdc3137..bb30de7 100644
--- a/app/src/main/java/com/hayu/app/CoinAdminActivity.kt
+++ b/app/src/main/java/com/hayu/app/CoinAdminActivity.kt
@@ -34,6 +34,7 @@ import java.util.Locale
  *  packages = paket & harga koin | ledger = riwayat transaksi koin
  *  wallet   = wallet coinseller (seller: saldo sendiri, admin: isi wallet) | send = coinseller kirim koin
  *  sales    = laporan penjualan coinseller
+ *  restock  = isi ulang stok: coinseller mengajukan, developer/superadmin/admin menyetujui
  */
 class CoinAdminActivity : AppCompatActivity() {
 
@@ -62,6 +63,12 @@ class CoinAdminActivity : AppCompatActivity() {
     private fun isCoinAdmin() = Role.canAccessAny(myRoles, "coin")
     private fun isSeller() = Role.COINSELLER in myRoles
 
+    /** Developer, Superadmin, Admin (atau yang punya akses panel koin) boleh mengisi stok coinseller. */
+    private fun canStock(roles: Set<String>) =
+        roles.any { it == Role.DEVELOPER || it == Role.SUPERADMIN || it == Role.ADMIN } ||
+            Role.canAccessAny(roles, "coin")
+    private fun isStockAdmin() = canStock(myRoles)
+
     private fun setInfo(s: String) {
         tvInfo.text = if (walletInfo.isEmpty()) s else "$walletInfo\n$s"
     }
@@ -89,7 +96,8 @@ class CoinAdminActivity : AppCompatActivity() {
     private fun allowed(): Boolean = when (mode) {
         "packages" -> isCoinAdmin() || isSeller() || Role.canAccessAny(myRoles, "transaction")
         "ledger" -> isCoinAdmin() || Role.canAccessAny(myRoles, "transaction")
-        "wallet" -> isCoinAdmin() || isSeller()
+        "wallet" -> isStockAdmin() || isSeller()
+        "restock" -> isStockAdmin() || isSeller()
         "send" -> isSeller()
         "sales" -> isCoinAdmin() || isSeller() || Role.canAccessAny(myRoles, "transaction")
         else -> false
@@ -104,6 +112,7 @@ class CoinAdminActivity : AppCompatActivity() {
             }
             "ledger" -> loadLedger()
             "sales" -> loadSales()
+            "restock" -> startRestock()
             "wallet" -> if (isSeller()) loadMyWallet() else {
                 searchBar.visibility = View.VISIBLE
                 setInfo("Cari coinseller (nickname / UserID / UID), tap untuk isi wallet")
@@ -414,6 +423,132 @@ class CoinAdminActivity : AppCompatActivity() {
         }.addOnFailureListener { toast("Gagal: ${it.message}") }
     }
 
+    // ---------------- ISI ULANG STOK COINSELLER ----------------
+    // Coinseller mengajukan stok -> developer/superadmin/admin menyetujui -> wallet bertambah.
+    private fun startRestock() {
+        if (isSeller()) {
+            addAction("➕ Ajukan isi ulang") {
+                amountDialog("Ajukan isi ulang stok", 10_000_000L) { requestStock(it) }
+            }
+        }
+        if (isStockAdmin()) {
+            addAction("💼 Isi langsung") {
+                startActivity(
+                    android.content.Intent(this, CoinAdminActivity::class.java)
+                        .putExtra("mode", "wallet")
+                        .putExtra("title", "💼 Isi Wallet Coinseller")
+                )
+            }
+            loadStockRequests(null)
+        } else {
+            refreshSellerBalance()
+            loadStockRequests(myUid)
+        }
+    }
+
+    private fun loadStockRequests(onlyUid: String?) {
+        val base = db.collection("seller_stock_requests")
+        val q: Query = if (onlyUid == null) base.whereEqualTo("status", "pending")
+        else base.whereEqualTo("uid", onlyUid)
+        q.limit(100).get()
+            .addOnSuccessListener { snap ->
+                list.removeAllViews()
+                val docs = snap.documents.sortedByDescending { num(it, "createdAt") }
+                if (docs.isEmpty()) {
+                    setInfo(if (onlyUid == null) "Tidak ada permintaan 🎉" else "Belum ada pengajuan stok")
+                    return@addOnSuccessListener
+                }
+                setInfo(if (onlyUid == null) "${docs.size} permintaan menunggu — tap untuk proses" else "Riwayat pengajuan stok")
+                for (r in docs) {
+                    val st = when (r.getString("status")) {
+                        "approved" -> "✅ Disetujui"
+                        "rejected" -> "❌ Ditolak"
+                        else -> "⏳ Menunggu"
+                    }
+                    val who = r.getString("name").orEmpty().ifEmpty { "Coinseller" }
+                    addRow("🔄 ${fmt(num(r, "amount"))} koin • $who", "$st • ${timeStr(num(r, "createdAt"))}") {
+                        if (onlyUid == null) stockDecisionMenu(r)
+                    }
+                }
+            }
+            .addOnFailureListener { toast("Gagal: ${it.message}") }
+    }
+
+    private fun requestStock(amount: Long) {
+        db.collection("users").document(myUid).get()
+            .addOnSuccessListener { me ->
+                val data = mapOf<String, Any>(
+                    "uid" to myUid, "name" to me.getString("nickname").orEmpty(),
+                    "amount" to amount, "status" to "pending",
+                    "createdAt" to System.currentTimeMillis()
+                )
+                db.collection("seller_stock_requests").add(data)
+                    .addOnSuccessListener {
+                        toast("✅ Pengajuan ${fmt(amount)} koin dikirim")
+                        loadStockRequests(if (isStockAdmin()) null else myUid)
+                    }
+                    .addOnFailureListener { toast("Gagal: ${it.message}") }
+            }
+            .addOnFailureListener { toast("Gagal: ${it.message}") }
+    }
+
+    private fun stockDecisionMenu(r: DocumentSnapshot) {
+        AlertDialog.Builder(this)
+            .setTitle("${fmt(num(r, "amount"))} koin untuk ${r.getString("name").orEmpty()}")
+            .setItems(arrayOf("✅ Setujui & isi wallet", "❌ Tolak")) { _, which ->
+                decideStock(r.id, which == 0)
+            }
+            .setNegativeButton("Batal", null)
+            .show()
+    }
+
+    private fun decideStock(reqId: String, approve: Boolean) {
+        val reqRef = db.collection("seller_stock_requests").document(reqId)
+        val actorRef = db.collection("users").document(myUid)
+        val ledgerRef = db.collection("coin_ledger").document()
+
+        db.runTransaction(Transaction.Function<Void?> { tx ->
+            val r = tx.get(reqRef)
+            val actor = tx.get(actorRef)
+            if (!canStock(Role.rolesOf(actor))) throw IllegalStateException("Kamu tidak boleh memproses ini")
+            if (r.getString("status") != "pending") throw IllegalStateException("Sudah diproses")
+            val sellerUid = r.getString("uid") ?: throw IllegalStateException("Data tidak valid")
+            val amount = num(r, "amount")
+            val now = System.currentTimeMillis()
+
+            if (approve) {
+                val sellerRef = db.collection("users").document(sellerUid)
+                val seller = tx.get(sellerRef)
+                if (Role.COINSELLER !in Role.rolesOf(seller)) throw IllegalStateException("Target bukan coinseller")
+                val newWallet = num(seller, "sellerWallet") + amount
+                tx.update(sellerRef, "sellerWallet", newWallet)
+                tx.set(
+                    ledgerRef, mapOf<String, Any>(
+                        "type" to "seller_stock", "uid" to sellerUid,
+                        "name" to (seller.getString("nickname") ?: ""),
+                        "delta" to amount, "balanceAfter" to newWallet,
+                        "byUid" to myUid, "at" to now
+                    )
+                )
+            }
+            tx.update(
+                reqRef, mapOf<String, Any>(
+                    "status" to (if (approve) "approved" else "rejected"),
+                    "processedBy" to myUid, "processedAt" to now
+                )
+            )
+            null
+        })
+            .addOnSuccessListener {
+                toast(if (approve) "✅ Stok ditambahkan ke wallet" else "Pengajuan ditolak")
+                loadStockRequests(null)
+            }
+            .addOnFailureListener {
+                toast("Gagal: ${it.message}")
+                loadStockRequests(null)
+            }
+    }
+
     // ---------------- CARI USER (send / wallet-admin) ----------------
     private fun doSearch() {
         if (!ready) { toast("Tunggu, mengecek akses…"); return }
@@ -511,7 +646,7 @@ class CoinAdminActivity : AppCompatActivity() {
         db.runTransaction(Transaction.Function<Void?> { tx ->
             val actor = tx.get(actorRef)
             val target = tx.get(targetRef)
-            if (!Role.canAccessAny(Role.rolesOf(actor), "coin")) {
+            if (!canStock(Role.rolesOf(actor))) {
                 throw IllegalStateException("Kamu tidak boleh mengisi wallet")
             }
             if (Role.COINSELLER !in Role.rolesOf(target)) {
diff --git a/app/src/main/java/com/hayu/app/OrgActivity.kt b/app/src/main/java/com/hayu/app/OrgActivity.kt
index db46b01..054eb24 100644
--- a/app/src/main/java/com/hayu/app/OrgActivity.kt
+++ b/app/src/main/java/com/hayu/app/OrgActivity.kt
@@ -26,7 +26,7 @@ class OrgActivity : ListPageActivity() {
     override fun allowed(): Boolean = when {
         mode == "register_agency" || mode == "verify_agency" || mode.startsWith("agencies_") -> isBd() || isOrgAdmin()
         mode == "recruit_host" || mode == "attendance_agency" || mode.startsWith("hosts_") -> isAgency()
-        mode == "verify_host" -> isAgency() || isOrgAdmin()
+        mode == "verify_host" -> isAgency()   // hanya agency yang menyetujui host
         mode == "host_home" -> Role.HOST in myRoles
         mode == "ranking_host" -> isAgency() || isBd() || isOrgAdmin() || Role.HOST in myRoles
         else -> false
@@ -35,7 +35,7 @@ class OrgActivity : ListPageActivity() {
     override fun onReady() {
         when {
             mode == "register_agency" -> { showSearch(); setInfo("Cari user biasa untuk dijadikan agency") }
-            mode == "recruit_host" -> { showSearch(); setInfo("Cari user biasa untuk direkrut jadi host") }
+            mode == "recruit_host" -> { showSearch(); setInfo("Cari user biasa. Jadi host otomatis setelah disetujui agency") }
             mode == "verify_agency" -> loadPendingAgencies()
             mode.startsWith("agencies_") -> loadAgencies(mode.substringAfter("_"))
             mode == "verify_host" -> loadPendingHosts()
@@ -155,7 +155,9 @@ class OrgActivity : ListPageActivity() {
                     .addOnSuccessListener { hSnap ->
                         val verified = hSnap.filter { it.getString("hostStatus") == "verified" }
                             .groupingBy { it.getString("agencyUid").orEmpty() }.eachCount()
-                        val pending = hSnap.filter { it.getString("hostStatus") == "pending" }
+                        db.collection("users").whereEqualTo("hostStatus", "pending").limit(500).get()
+                            .addOnSuccessListener { pSnap ->
+                        val pending = pSnap.documents
                             .groupingBy { it.getString("agencyUid").orEmpty() }.eachCount()
 
                         val mine = aSnap.filter { isOrgAdmin() || it.getString("bdUid") == myUid }
@@ -186,6 +188,8 @@ class OrgActivity : ListPageActivity() {
                                 "👥 ${verified[a.id] ?: 0} host (+${pending[a.id] ?: 0} menunggu) • $status$extra"
                             )
                         }
+                            }
+                            .addOnFailureListener { toast("Gagal: ${it.message}") }
                     }
                     .addOnFailureListener { toast("Gagal: ${it.message}") }
             }
@@ -203,10 +207,11 @@ class OrgActivity : ListPageActivity() {
             if (Role.AGENCY !in Role.rolesOf(actor)) throw IllegalStateException("Hanya agency yang boleh merekrut")
             if (actor.getString("agencyStatus") != "verified") throw IllegalStateException("Agency kamu belum diverifikasi")
             if (Role.HOST in Role.rolesOf(t)) throw IllegalStateException("User ini sudah menjadi host")
-            val newRoles = Role.rolesOf(t) + Role.HOST
+            if (t.getString("hostStatus") == "pending") throw IllegalStateException("User ini masih menunggu persetujuan")
+            // Role HOST BELUM diberikan di sini; baru diberikan otomatis saat agency menyetujui.
             tx.update(
                 targetRef, mapOf<String, Any>(
-                    "role" to Role.top(newRoles), "roles" to newRoles.toList(), "hostStatus" to "pending", "agencyUid" to myUid,
+                    "hostStatus" to "pending", "agencyUid" to myUid,
                     "agencyName" to (actor.getString("agencyName") ?: ""),
                     "recruitedAt" to System.currentTimeMillis()
                 )
@@ -219,7 +224,7 @@ class OrgActivity : ListPageActivity() {
             )
             null
         })
-            .addOnSuccessListener { toast("✅ ${nick(target)} direkrut (menunggu verifikasi)"); clearList(); doSearch() }
+            .addOnSuccessListener { toast("✅ ${nick(target)} diajukan, jadi host setelah disetujui"); clearList(); doSearch() }
             .addOnFailureListener { toast("Gagal: ${it.message}") }
     }
 
@@ -229,14 +234,14 @@ class OrgActivity : ListPageActivity() {
             .addOnSuccessListener { snap ->
                 clearList()
                 val docs = snap.documents
-                    .filter { isOrgAdmin() || it.getString("agencyUid") == myUid }
+                    .filter { it.getString("agencyUid") == myUid }
                     .sortedBy { num(it, "recruitedAt") }
                 setInfo(if (docs.isEmpty()) "Tidak ada host yang menunggu 🎉" else "${docs.size} host menunggu verifikasi")
                 for (h in docs) {
                     addRow("🎙️ ${nick(h)} • ID ${num(h, "userId")}", "Direkrut ${timeStr(num(h, "recruitedAt"))}") {
                         AlertDialog.Builder(this)
                             .setTitle(nick(h))
-                            .setItems(arrayOf("✅ Verifikasi", "❌ Tolak")) { _, which -> decideHost(h.id, which == 0) }
+                            .setItems(arrayOf("✅ Setujui (jadi host)", "❌ Tolak")) { _, which -> decideHost(h.id, which == 0) }
                             .setNegativeButton("Batal", null)
                             .show()
                     }
@@ -254,16 +259,19 @@ class OrgActivity : ListPageActivity() {
             val t = tx.get(targetRef)
             val ar = Role.rolesOf(actor)
             val own = Role.AGENCY in ar && t.getString("agencyUid") == myUid
-            if (!own && ar.none { it in orgAdminRoles }) throw IllegalStateException("Bukan host di bawah kamu")
+            if (!own) throw IllegalStateException("Hanya agency yang merekrut user ini yang bisa menyetujui")
             if (t.getString("hostStatus") != "pending") throw IllegalStateException("Sudah diproses")
             if (approve) {
-                tx.update(targetRef, mapOf<String, Any>("hostStatus" to "verified", "verifiedAt" to System.currentTimeMillis()))
-            } else {
+                // Disetujui agency -> otomatis jadi HOST
+                val newRoles = Role.rolesOf(t) + Role.HOST
                 tx.update(
                     targetRef, mapOf<String, Any>(
-                        "role" to Role.top(Role.rolesOf(t) - Role.HOST), "roles" to (Role.rolesOf(t) - Role.HOST).toList(), "hostStatus" to "rejected", "agencyUid" to FieldValue.delete()
+                        "role" to Role.top(newRoles), "roles" to newRoles.toList(),
+                        "hostStatus" to "verified", "verifiedAt" to System.currentTimeMillis()
                     )
                 )
+            } else {
+                tx.update(targetRef, mapOf<String, Any>("hostStatus" to "rejected", "agencyUid" to FieldValue.delete()))
             }
             tx.set(
                 logRef, mapOf<String, Any>(
@@ -273,7 +281,7 @@ class OrgActivity : ListPageActivity() {
             )
             null
         })
-            .addOnSuccessListener { toast(if (approve) "✅ Host diverifikasi" else "Host ditolak"); loadPendingHosts() }
+            .addOnSuccessListener { toast(if (approve) "✅ Disetujui, user kini menjadi host" else "Permintaan ditolak"); loadPendingHosts() }
             .addOnFailureListener { toast("Gagal: ${it.message}") }
     }
 
diff --git a/app/src/main/java/com/hayu/app/PanelDetailActivity.kt b/app/src/main/java/com/hayu/app/PanelDetailActivity.kt
index 5057495..eadffe1 100644
--- a/app/src/main/java/com/hayu/app/PanelDetailActivity.kt
+++ b/app/src/main/java/com/hayu/app/PanelDetailActivity.kt
@@ -100,15 +100,13 @@ class PanelDetailActivity : AppCompatActivity() {
             "🏆 Ranking Agency"
         ),
         "coinseller" to listOf(
-            "🪙 Dashboard Coinseller",
             "🔍 Cari UID Customer",
             "📦 Paket Koin",
-            "📋 Order Masuk",
+            "🔄 Isi Ulang Stok Koin",
             "✅ Verifikasi Pembayaran",
             "📤 Kirim Koin",
             "💼 Wallet Coinseller",
-            "📊 Laporan Penjualan",
-            "↩️ Refund Request"
+            "📊 Laporan Penjualan"
         ),
         "finance" to listOf(
             "💰 Total Pendapatan",
@@ -264,7 +262,7 @@ class PanelDetailActivity : AppCompatActivity() {
         )
         val coinModes = mapOf(
             "Paket & Harga Koin" to "packages", "Paket Koin" to "packages",
-            "Ledger Transaksi" to "ledger", "Dashboard Coinseller" to "wallet",
+            "Ledger Transaksi" to "ledger", "Isi Ulang Stok Koin" to "restock",
             "Wallet Coinseller" to "wallet", "Kirim Koin" to "send",
             "Laporan Penjualan" to "sales", "Top-up & Pembelian" to "topup"
         )
@@ -287,6 +285,10 @@ class PanelDetailActivity : AppCompatActivity() {
         if ((key == "dashboard" || key == "user" || key == "bd") && Role.canManageRoles(role)) {
             items.add(0, roleItem)
         }
+        // Developer / Superadmin / Admin: konfirmasi top up juga bisa dibuka dari panel Coinseller
+        if (key == "coinseller" && role in listOf(Role.DEVELOPER, Role.SUPERADMIN, Role.ADMIN)) {
+            items.add("💳 Top-up & Pembelian")
+        }
         for (item in items) {
             val row = LinearLayout(this)
             row.orientation = LinearLayout.HORIZONTAL
diff --git a/app/src/main/java/com/hayu/app/Role.kt b/app/src/main/java/com/hayu/app/Role.kt
index e508ab2..672ae0f 100644
--- a/app/src/main/java/com/hayu/app/Role.kt
+++ b/app/src/main/java/com/hayu/app/Role.kt
@@ -37,10 +37,11 @@ object Role {
 
     // Role apa saja yang boleh DIBERIKAN oleh masing-masing role
     private val assignable = mapOf(
-        DEVELOPER  to listOf(SUPERADMIN, ADMIN, BD, AGENCY, HOST, COINSELLER, FINANCE, MODERATOR, CS, USER),
-        SUPERADMIN to listOf(ADMIN, BD, AGENCY, HOST, COINSELLER, FINANCE, MODERATOR, CS, USER),
-        ADMIN      to listOf(BD, AGENCY, HOST, COINSELLER, FINANCE, MODERATOR, CS, USER),
-        BD         to listOf(AGENCY, HOST, USER)
+        // HOST tidak ada di daftar: role host HANYA didapat otomatis saat agency menyetujui.
+        DEVELOPER  to listOf(SUPERADMIN, ADMIN, BD, AGENCY, COINSELLER, FINANCE, MODERATOR, CS, USER),
+        SUPERADMIN to listOf(ADMIN, BD, AGENCY, COINSELLER, FINANCE, MODERATOR, CS, USER),
+        ADMIN      to listOf(BD, AGENCY, COINSELLER, FINANCE, MODERATOR, CS, USER),
+        BD         to listOf(AGENCY)   // BD hanya boleh menambah/mencabut role agency
     )
 
     fun normalize(raw: String?): String {
diff --git a/app/src/main/java/com/hayu/app/TopUpAdminActivity.kt b/app/src/main/java/com/hayu/app/TopUpAdminActivity.kt
index 128fd5c..4b13431 100644
--- a/app/src/main/java/com/hayu/app/TopUpAdminActivity.kt
+++ b/app/src/main/java/com/hayu/app/TopUpAdminActivity.kt
@@ -10,7 +10,7 @@ class TopUpAdminActivity : SimplePageActivity() {
 
     override fun render(user: DocumentSnapshot, following: Int, followers: Int) {
         val roles = Role.rolesOf(user)
-        if (Role.DEVELOPER !in roles && Role.SUPERADMIN !in roles) {
+        if (Role.DEVELOPER !in roles && Role.SUPERADMIN !in roles && Role.ADMIN !in roles) {
             toast("Kamu tidak punya akses")
             finish()
             return
PATCH_EOF

echo "▶ Mengecek patch..."
if git apply --check "$P" 2>/dev/null; then
  git apply "$P"
  echo "▶ Terpasang (cocok persis)."
elif patch -p1 --dry-run -F3 < "$P" >/dev/null 2>&1; then
  patch -p1 -F3 < "$P"
  echo "▶ Terpasang (dengan penyesuaian baris kecil, cek hasilnya)."
else
  echo "❌ Patch tidak cocok dengan repo-mu (file-file ini sudah berubah dari versi saya)."
  echo "   Tidak ada yang diubah. Kirim zip repo terbaru ke Claude untuk disesuaikan."
  rm -f "$P"
  exit 1
fi
rm -f "$P"

echo ""
echo "✅ Selesai: coinseller, top up, role BD/host."
echo "   Lanjut: git add -A && git commit -m \"coinseller & role\" && git push"
echo "   Catatan: atur Firestore Rules untuk koleksi seller_stock_requests."
