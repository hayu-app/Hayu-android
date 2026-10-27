package com.hayu.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object ImgbbUploader {

    private const val API_KEY = "f2b4a5671d7727f32ff5a533b27f5e82"
    private const val UPLOAD_URL = "https://api.imgbb.com/1/upload"
    private const val MAX_SIDE = 1600

    // Timeout default OkHttp cuma 10 detik -> foto besar di sinyal HP sering gagal.
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    fun uploadImage(
        imageFile: File,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // Kecilkan & putar sesuai EXIF dulu, supaya cepat dan tidak ditolak
        val file = try {
            shrink(imageFile)
        } catch (e: Throwable) {
            imageFile
        }
        val mime = if (file === imageFile) "image/*" else "image/jpeg"

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", file.name, file.asRequestBody(mime.toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url("$UPLOAD_URL?key=$API_KEY")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "Gagal terhubung ke server")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        // Tampilkan alasan asli dari ImgBB (mis. Invalid API v1 key)
                        val reason = try {
                            JSONObject(text).getJSONObject("error").getString("message")
                        } catch (e: Exception) {
                            text.take(120)
                        }
                        onError("HTTP ${response.code}: $reason")
                        return
                    }
                    try {
                        val data = JSONObject(text).getJSONObject("data")
                        val url = data.optString("url").ifEmpty { data.optString("display_url") }
                        if (url.isEmpty()) {
                            onError("ImgBB tidak mengirim link gambar")
                        } else {
                            onSuccess(url.replace("http://", "https://"))
                        }
                    } catch (e: Exception) {
                        onError("Gagal baca respon: ${e.message}")
                    }
                }
            }
        })
    }

    private fun shrink(src: File): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return src

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeFile(src.absolutePath, opts) ?: return src

        val rotation = when (
            ExifInterface(src.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
        if (rotation != 0f || scale < 1f) {
            val m = Matrix()
            if (scale < 1f) m.postScale(scale, scale)
            if (rotation != 0f) m.postRotate(rotation)
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }

        val out = File(src.parentFile, "shrunk_${System.currentTimeMillis()}.jpg")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return out
    }
}
