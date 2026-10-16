package com.hayu.app.model

data class Notification(
    val notificationId: String = "",
    val userId: String = "",        // penerima notifikasi
    val type: String = "",          // follow, friend_request, message, room_invite
    val fromUid: String = "",       // pengirim
    val fromName: String = "",      // nama pengirim
    val message: String = "",       // teks notifikasi
    val read: Boolean = false,      // sudah dibaca atau belum
    val timestamp: Long = 0L
) {
    companion object {
        @JvmStatic
        fun fromDoc(notificationId: String, map: Map<String, Any?>): Notification = Notification(
            notificationId = notificationId,
            userId = map["userId"] as? String ?: "",
            type = map["type"] as? String ?: "",
            fromUid = map["fromUid"] as? String ?: "",
            fromName = map["fromName"] as? String ?: "",
            message = map["message"] as? String ?: "",
            read = map["read"] as? Boolean ?: false,
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L
        )
    }
}
