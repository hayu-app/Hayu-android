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

    fun normalize(raw: String?): String {
        return (raw ?: USER).trim().lowercase()
    }

    fun canAccess(role: String?, panel: String): Boolean {
        val normalizedRole = normalize(role)
        val perms = access[normalizedRole] ?: return false
        return perms.contains("all") || perms.contains(panel)
    }
}
