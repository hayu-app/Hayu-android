package com.hayu.app.model

data class DMMessage(
    val messageId: String = "",
    val senderUid: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val seen: Boolean = false
) {
    companion object {
        @JvmStatic
        fun fromDoc(messageId: String, map: Map<String, Any?>): DMMessage = DMMessage(
            messageId = messageId,
            senderUid = map["senderUid"] as? String ?: "",
            text = map["text"] as? String ?: "",
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
            seen = map["seen"] as? Boolean ?: false
        )
    }
}
