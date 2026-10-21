package com.hayu.app

object Role {
    const val DEVELOPER    = "developer"
    const val SUPERADMIN   = "superadmin"
    const val ADMIN        = "admin"
    const val BD           = "bd"
    const val AGENCY       = "agency"
    const val HOST         = "host"
    const val COINSELLER   = "coinseller"
    const val FINANCE      = "finance"
    const val MODERATOR    = "moderator"
    const val CS           = "cs"
    const val USER         = "user"

    val access = mapOf(
        DEVELOPER   to listOf("all"),
        SUPERADMIN  to listOf("dashboard","user","admin","bd","agency","host","coinseller","finance","moderator","cs","room","coin","vip","event","security","notif","system","report"),
        ADMIN       to listOf("dashboard","user","room","host","agency","bd","coinseller","report","ban","event","transaction","cs","verify","logs"),
        BD          to listOf("dashboard","agency","host","commission","report","recruit"),
        AGENCY      to listOf("dashboard","host","attendance","performance","commission","withdrawal","member"),
        HOST        to listOf("dashboard","room","gift","earnings","level","task","attendance","ranking","withdrawal","wallet"),
        COINSELLER  to listOf("dashboard","customer","topup","order","wallet","commission","report"),
        FINANCE     to listOf("dashboard","revenue","transaction","commission","withdrawal","refund","report"),
        MODERATOR   to listOf("dashboard","room","report","ban","chat","appeal","history"),
        CS          to listOf("dashboard","complaint","recovery","refund","ticket","support"),
        USER        to listOf()
    )

    // Hirarki: angka lebih besar = lebih tinggi
    private val rank = mapOf(
        DEVELOPER to 100, SUPERADMIN to 90, ADMIN to 80, BD to 60, AGENCY to 50,
        FINANCE to 45, MODERATOR to 40, COINSELLER to 35, CS to 30, HOST to 20, USER to 0
    )

    // Role apa saja yang boleh DIBERIKAN oleh masing-masing role
    private val assignable = mapOf(
        DEVELOPER  to listOf(SUPERADMIN, ADMIN, BD, AGENCY, HOST, COINSELLER, FINANCE, MODERATOR, CS, USER),
        SUPERADMIN to listOf(ADMIN, BD, AGENCY, HOST, COINSELLER, FINANCE, MODERATOR, CS, USER),
        ADMIN      to listOf(BD, AGENCY, HOST, COINSELLER, FINANCE, MODERATOR, CS, USER),
        BD         to listOf(AGENCY, HOST, USER)
    )

    fun normalize(raw: String?): String {
        val r = (raw ?: USER).trim().lowercase()
        return if (r.isEmpty()) USER else r
    }

    fun canAccess(role: String?, panel: String): Boolean {
        val normalizedRole = normalize(role)
        val perms = access[normalizedRole] ?: return false
        return perms.contains("all") || perms.contains(panel)
    }

    fun rankOf(role: String?): Int = rank[normalize(role)] ?: 0

    fun assignableBy(actorRole: String?): List<String> = assignable[normalize(actorRole)] ?: emptyList()

    fun canManageRoles(role: String?): Boolean = assignableBy(role).isNotEmpty()

    /** Actor boleh mengubah role target hanya jika pangkat actor LEBIH TINGGI dari target. */
    fun canManageTarget(actorRole: String?, targetRole: String?): Boolean =
        canManageRoles(actorRole) && rankOf(actorRole) > rankOf(targetRole)

    fun label(role: String?): String = when (normalize(role)) {
        DEVELOPER -> "👑 Developer"
        SUPERADMIN -> "🛡️ SuperAdmin"
        ADMIN -> "⭐ Admin"
        BD -> "🏡 BD"
        AGENCY -> "🏢 Agency"
        HOST -> "🎙️ Host"
        COINSELLER -> "🪙 Coinseller"
        FINANCE -> "💰 Finance"
        MODERATOR -> "🔍 Moderator"
        CS -> "🎧 CS"
        else -> "👤 User (tanpa role)"
    }

    /** Teks badge panjang (profil). null = user biasa, badge disembunyikan. */
    fun badge(role: String?): String? = when (normalize(role)) {
        DEVELOPER -> "🛡️ DEVELOPER"
        SUPERADMIN -> "🛡️ SUPERADMIN"
        ADMIN -> "⭐ ADMIN"
        BD -> "🏡 BD"
        AGENCY -> "🏢 AGENCY"
        HOST -> "🎙️ HOST"
        COINSELLER -> "🪙 COINSELLER"
        FINANCE -> "💰 FINANCE"
        MODERATOR -> "🔍 MODERATOR"
        CS -> "🎧 CS"
        else -> null
    }

    /** Teks badge pendek (list user). */
    fun badgeShort(role: String?): String? = when (normalize(role)) {
        DEVELOPER -> "🛡️ DEV"
        SUPERADMIN -> "🛡️ SA"
        ADMIN -> "⭐ ADM"
        BD -> "🏡 BD"
        AGENCY -> "🏢 AGN"
        HOST -> "🎙️ HOST"
        COINSELLER -> "🪙 COIN"
        FINANCE -> "💰 FIN"
        MODERATOR -> "🔍 MOD"
        CS -> "🎧 CS"
        else -> null
    }
}
