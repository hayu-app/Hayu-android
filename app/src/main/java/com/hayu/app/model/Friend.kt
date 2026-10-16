package com.hayu.app.model

data class Friend(
    val requestId: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val fromPhoto: String = "",
    val toUid: String = "",
    val toName: String = "",
    val status: String = "",       // pending, accepted, rejected
    val createdAt: Long = 0L
) {
    companion object {
        @JvmStatic
        fun fromDoc(requestId: String, map: Map<String, Any?>): Friend = Friend(
            requestId = requestId,
            fromUid = map["fromUid"] as? String ?: "",
            fromName = map["fromName"] as? String ?: "",
            fromPhoto = map["fromPhoto"] as? String ?: "",
            toUid = map["toUid"] as? String ?: "",
            toName = map["toName"] as? String ?: "",
            status = map["status"] as? String ?: "pending",
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }
}
