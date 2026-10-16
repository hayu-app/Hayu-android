package com.hayu.app.model

data class Room(
    val roomId: String = "",
    val name: String = "",
    val topic: String = "",
    val maxMic: Int = 5,
    val currentUsers: Int = 1,
    val ownerUid: String = "",
    val ownerName: String = "",
    val privacy: String = "publik",
    val coverColor: String = "purple",
    val active: Boolean = true,
    val lastActiveAt: Long = 0L,
    val createdAt: Long = 0L
) {
    companion object {
        @JvmStatic
        fun fromDoc(roomId: String, map: Map<String, Any?>): Room = Room(
            roomId = roomId,
            name = map["name"] as? String ?: "",
            topic = map["topic"] as? String ?: "",
            maxMic = (map["maxMic"] as? Number)?.toInt() ?: 5,
            currentUsers = (map["currentUsers"] as? Number)?.toInt() ?: 1,
            ownerUid = map["ownerUid"] as? String ?: "",
            ownerName = map["ownerName"] as? String ?: "",
            privacy = map["privacy"] as? String ?: "publik",
            coverColor = map["coverColor"] as? String ?: "purple",
            active = map["active"] as? Boolean ?: true,
            lastActiveAt = (map["lastActiveAt"] as? Number)?.toLong() ?: 0L,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }
}
