package com.hayu.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ImageUtil {

    // Baca gambar dari Uri, kecilkan (maks sisi 1024px), simpan sebagai JPEG di cache
    fun uriToFile(ctx: Context, uri: Uri, maxSide: Int = 1024): File? {
        return try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            var sample = 1
            while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) {
                sample *= 2
            }
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
            val result = if (scale < 1f) {
                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
            } else {
                bmp
            }
            val file = File.createTempFile("upload_", ".jpg", ctx.cacheDir)
            FileOutputStream(file).use { result.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file
        } catch (e: Exception) {
            null
        }
    }
}
