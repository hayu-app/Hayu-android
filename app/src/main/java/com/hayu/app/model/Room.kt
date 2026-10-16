package com.hayu.app.model

data class Room(
    val roomId: String = "",
    val name: String = "",
    val topic: String = "",
    val maxUsers: Int = 10,
    val currentUsers: Int = 1,
    val ownerUid: String = "",
    val ownerName: String = "",
    val coverColor: String = "purple",
    val active: Boolean = true,
    val createdAt: Long = 0L
) {
    companion object {
        @JvmStatic
        fun fromDoc(roomId: String, map: Map<String, Any?>): Room = Room(
            roomId = roomId,
            name = map["name"] as? String ?: "",
            topic = map["topic"] as? String ?: "",
            maxUsers = (map["maxUsers"] as? Number)?.toInt() ?: 10,
            currentUsers = (map["currentUsers"] as? Number)?.toInt() ?: 1,
            ownerUid = map["ownerUid"] as? String ?: "",
            ownerName = map["ownerName"] as? String ?: "",
            coverColor = map["coverColor"] as? String ?: "purple",
            active = map["active"] as? Boolean ?: true,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }
}
