package com.hayu.app.model

data class DMChat(
    val chatId: String = "",
    val user1Uid: String = "",
    val user2Uid: String = "",
    val peerUid: String = "",       // lawan bicara (otomatis set)
    val peerName: String = "",
    val peerPhoto: String = "",
    val lastMessage: String = "",
    val lastTimestamp: Long = 0L,
    val lastSenderUid: String = ""
) {
    companion object {
        @JvmStatic
        fun fromDoc(chatId: String, map: Map<String, Any?>, myUid: String): DMChat {
            val user1 = map["user1Uid"] as? String ?: ""
            val user2 = map["user2Uid"] as? String ?: ""
            val peer = if (user1 == myUid) user2 else user1

            // Ambil data peer dari field dinamis
            val peerNameKey = "peerName_$peer"
            val peerPhotoKey = "peerPhoto_$peer"

            return DMChat(
                chatId = chatId,
                user1Uid = user1,
                user2Uid = user2,
                peerUid = peer,
                peerName = map[peerNameKey] as? String ?: "",
                peerPhoto = map[peerPhotoKey] as? String ?: "",
                lastMessage = map["lastMessage"] as? String ?: "",
                lastTimestamp = (map["lastTimestamp"] as? Number)?.toLong() ?: 0L,
                lastSenderUid = map["lastSenderUid"] as? String ?: ""
            )
        }
    }
}
