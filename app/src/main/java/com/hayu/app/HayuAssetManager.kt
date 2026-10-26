package com.hayu.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

object HayuAssetManager {
    private const val TAG = "HayuAssetManager"
    private const val DOWNLOAD_URL =
        "https://github.com/hayu-app/Hayu-android/releases/download/assets-v1/Hayu-assets-v1.zip"

    private val executor = Executors.newSingleThreadExecutor()
    private const val MAX_ZIP_BYTES = 150L * 1024 * 1024

    private fun root(context: Context) =
        File(context.filesDir, "hayu-assets-v1")

    fun getFile(context: Context, relativePath: String): File? {
        if (relativePath.startsWith("/") ||
            relativePath.split("/").any { it == ".." }) return null

        val base = root(context)
        val file = File(base, relativePath)
        return if (file.isFile && file.canonicalPath.startsWith(
                base.canonicalPath + File.separator
            )
        ) file else null
    }

    fun isReady(context: Context): Boolean {
        val marker = File(root(context), ".ready")
        return marker.isFile &&
            File(root(context), "vip").isDirectory &&
            File(root(context), "svip").isDirectory
    }

    fun ensureDownloaded(context: Context) {
        val appContext = context.applicationContext
        if (isReady(appContext)) return

        executor.execute {
            if (isReady(appContext)) return@execute

            val staging = File(appContext.filesDir, "hayu-assets-staging")
            val zipFile = File(appContext.cacheDir, "hayu-assets-download.zip")
            staging.deleteRecursively()
            zipFile.delete()

            var connection: HttpURLConnection? = null
            try {
                connection = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }
                connection.connect()

                check(connection.responseCode in 200..299) {
                    "HTTP ${connection.responseCode}"
                }

                val expected = connection.contentLengthLong
                if (expected > MAX_ZIP_BYTES) error("ZIP terlalu besar")

                connection.inputStream.use { input ->
                    FileOutputStream(zipFile).use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_ZIP_BYTES) error("ZIP melewati batas ukuran")
                            output.write(buffer, 0, count)
                        }
                    }
                }

                staging.mkdirs()
                var foundVip = false
                var foundSvip = false
                var extracted = 0L

                ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name

                        if (name.startsWith("/") ||
                            name.split("/").any { it == ".." }) {
                            error("Path ZIP tidak aman")
                        }

                        // ZIP berisi folder induk "assets/"
                        if (name == "assets/" || name == "assets") {
                            zip.closeEntry()
                            continue
                        }
                        if (!name.startsWith("assets/")) {
                            zip.closeEntry()
                            continue
                        }

                        val relative = name.removePrefix("assets/")
                        if (!(relative.startsWith("vip/") ||
                              relative.startsWith("svip/"))) {
                            zip.closeEntry()
                            continue
                        }

                        val target = File(staging, relative)
                        check(target.canonicalPath.startsWith(
                            staging.canonicalPath + File.separator
                        )) { "Path ekstraksi tidak aman" }

                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { output ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    extracted += count
                                    if (extracted > MAX_ZIP_BYTES) {
                                        error("Aset hasil ekstraksi terlalu besar")
                                    }
                                    output.write(buffer, 0, count)
                                }
                            }
                            if (relative.startsWith("vip/")) foundVip = true
                            if (relative.startsWith("svip/")) foundSvip = true
                        }
                        zip.closeEntry()
                    }
                }

                check(foundVip && foundSvip) { "Folder VIP/SVIP tidak lengkap" }
                File(staging, ".ready").writeText("assets-v1")

                val destination = root(appContext)
                val backup = File(appContext.filesDir, "hayu-assets-backup")
                backup.deleteRecursively()

                if (destination.exists() && !destination.renameTo(backup)) {
                    error("Tidak bisa memindahkan cache aset lama")
                }
                if (!staging.renameTo(destination)) {
                    if (backup.exists()) backup.renameTo(destination)
                    error("Tidak bisa memasang cache aset baru")
                }
                backup.deleteRecursively()
                Log.i(TAG, "Aset VIP/SVIP berhasil diunduh dan dipasang")
            } catch (e: Exception) {
                Log.w(TAG, "Unduhan aset gagal; gunakan aset bawaan APK", e)
                staging.deleteRecursively()
            } finally {
                connection?.disconnect()
                zipFile.delete()
            }
        }
    }
}
