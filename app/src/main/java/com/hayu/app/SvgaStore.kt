package com.hayu.app

import android.content.Context
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Penyimpanan file .svga di Firestore (tanpa Firebase Storage / paket Blaze).
 * File dipecah jadi potongan ~700 KB (batas dokumen Firestore 1 MB), jadi ukuran file tidak dibatasi aplikasi.
 *  - svgaChunks/{assetId}_{index} : { assetId, index, data(Blob) }
 *  - URL aset berbentuk "fs://{assetId}"; SvgaPlayer mengunduh potongan lalu menyimpannya di cache.
 */
object SvgaStore {
    const val CHUNKS = "svgaChunks"
    const val PREFIX = "fs://"
    private const val CHUNK_SIZE = 700_000

    fun isStored(url: String) = url.startsWith(PREFIX)

    private fun close(s: InputStream) {
        try { s.close() } catch (_: Exception) {}
    }

    /** Unggah file per potongan. onDone(assetId, jumlahPotongan, error): error != null → gagal. */
    fun upload(
        file: File,
        onProgress: (Int) -> Unit,
        onDone: (String?, Int, Exception?) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val id = db.collection(SvgaCatalog.COLLECTION).document().id
        val total = file.length()
        val count = ((total + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt().coerceAtLeast(1)
        val input = try {
            FileInputStream(file)
        } catch (e: Exception) {
            onDone(null, 0, e)
            return
        }

        fun fail(e: Exception) {
            close(input)
            deleteChunks(id) { onDone(null, 0, e) }
        }

        fun writeNext(i: Int) {
            if (i >= count) {
                close(input)
                onDone(id, count, null)
                return
            }
            val remaining = total - i.toLong() * CHUNK_SIZE
            val buf = ByteArray(minOf(CHUNK_SIZE.toLong(), remaining).toInt())
            try {
                var off = 0
                while (off < buf.size) {
                    val n = input.read(buf, off, buf.size - off)
                    if (n < 0) break
                    off += n
                }
                if (off != buf.size) throw IllegalStateException("File terbaca tidak lengkap")
            } catch (e: Exception) {
                fail(e)
                return
            }
            val data = hashMapOf<String, Any>(
                "assetId" to id,
                "index" to i.toLong(),
                "data" to Blob.fromBytes(buf)
            )
            db.collection(CHUNKS).document("${id}_${i.toString().padStart(5, '0')}").set(data)
                .addOnSuccessListener {
                    onProgress(((i + 1) * 100) / count)
                    writeNext(i + 1)
                }
                .addOnFailureListener { e -> fail(e) }
        }

        writeNext(0)
    }

    /** Ambil file lokal untuk aset (dari cache, atau susun dari potongan Firestore). */
    fun openFile(context: Context, assetId: String, onResult: (File?) -> Unit) {
        val cache = File(context.cacheDir, "svga_$assetId.svga")
        if (cache.exists() && cache.length() > 0L) {
            onResult(cache)
            return
        }
        FirebaseFirestore.getInstance().collection(CHUNKS)
            .whereEqualTo("assetId", assetId).get()
            .addOnSuccessListener { snap ->
                val parts = snap.documents.sortedBy { it.getLong("index") ?: 0L }
                if (parts.isEmpty()) {
                    onResult(null)
                    return@addOnSuccessListener
                }
                try {
                    val tmp = File(context.cacheDir, "svga_$assetId.part")
                    tmp.outputStream().use { out ->
                        for (d in parts) {
                            val blob = d.getBlob("data") ?: throw IllegalStateException("Potongan hilang")
                            out.write(blob.toBytes())
                        }
                    }
                    if (!tmp.renameTo(cache)) {
                        tmp.copyTo(cache, overwrite = true)
                        tmp.delete()
                    }
                    onResult(cache)
                } catch (e: Exception) {
                    onResult(null)
                }
            }
            .addOnFailureListener { onResult(null) }
    }

    /** Hapus semua potongan milik aset (dan cache lokalnya). */
    fun deleteChunks(assetId: String, onDone: () -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection(CHUNKS).whereEqualTo("assetId", assetId).get()
            .addOnSuccessListener { snap ->
                val batches = snap.documents.chunked(400)
                fun run(i: Int) {
                    if (i >= batches.size) {
                        onDone()
                        return
                    }
                    val batch = db.batch()
                    for (d in batches[i]) batch.delete(d.reference)
                    batch.commit().addOnCompleteListener { run(i + 1) }
                }
                run(0)
            }
            .addOnFailureListener { onDone() }
    }
}
