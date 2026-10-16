package com.hayu.app.model

data class User(
    val uid: String = "",
    val userId: Long = 0L,
    val email: String = "",
    val nickname: String = "",
    val age: Int = 0,
    val gender: String = "",
    val country: String = "",
    val bio: String = "",
    val photoUrl: String = "",
    val coins: Int = 0,
    val totalCoinsEarned: Int = 0,
    val lastDailyClaim: Long = 0L,
    val level: Int = 1,
    val online: Boolean = false,
    val onboarded: Boolean = false
) {
    companion object {
        @JvmStatic
        fun fromDoc(uid: String, map: Map<String, Any?>): User = User(
            uid = uid,
            userId = (map["userId"] as? Number)?.toLong() ?: 0L,
            email = map["email"] as? String ?: "",
            nickname = map["nickname"] as? String ?: "",
            age = (map["age"] as? Number)?.toInt() ?: 0,
            gender = map["gender"] as? String ?: "",
            country = map["country"] as? String ?: "",
            bio = map["bio"] as? String ?: "",
            photoUrl = map["photoUrl"] as? String ?: "",
            coins = (map["coins"] as? Number)?.toInt() ?: 0,
            totalCoinsEarned = (map["totalCoinsEarned"] as? Number)?.toInt() ?: 0,
            lastDailyClaim = (map["lastDailyClaim"] as? Number)?.toLong() ?: 0L,
            level = (map["level"] as? Number)?.toInt() ?: 1,
            online = map["online"] as? Boolean ?: false,
            onboarded = map["onboarded"] as? Boolean ?: false
        )
    }
}
