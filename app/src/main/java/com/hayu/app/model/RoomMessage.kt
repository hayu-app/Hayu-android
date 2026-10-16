package com.hayu.app.model

data class RoomMessage(
    val messageId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L
) {
    companion object {
        @JvmStatic
        fun fromDoc(messageId: String, map: Map<String, Any?>): RoomMessage = RoomMessage(
            messageId = messageId,
            senderUid = map["senderUid"] as? String ?: "",
            senderName = map["senderName"] as? String ?: "",
            text = map["text"] as? String ?: "",
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L
        )
    }
}
