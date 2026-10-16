package com.hayu.app.util

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
import java.io.IOException

object ImgbbUploader {

    // ⚠️ GANTI DENGAN API KEY IMGBB KAMU
    private const val API_KEY = "35f45f1430f9e7cd7aee2c088d7d6495"
    private const val UPLOAD_URL = "https://api.imgbb.com/1/upload"

    fun uploadImage(
        imageFile: File,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val client = OkHttpClient()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                imageFile.name,
                imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            )
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
                    if (!response.isSuccessful) {
                        onError("Upload gagal: HTTP ${response.code}")
                        return
                    }

                    val responseBody = response.body?.string()
                    try {
                        val json = JSONObject(responseBody ?: "")
                        val data = json.getJSONObject("data")
                        val imageUrl = data.getString("url")
                        onSuccess(imageUrl)
                    } catch (e: Exception) {
                        onError("Gagal baca respon: ${e.message}")
                    }
                }
            }
        })
    }
}
