package com.hayu.app.util

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hayu_session", Context.MODE_PRIVATE)

    fun setActiveRoomId(roomId: String) {
        prefs.edit().putString(KEY_ACTIVE_ROOM, roomId).apply()
    }

    fun getActiveRoomId(): String? {
        return prefs.getString(KEY_ACTIVE_ROOM, null)
    }

    fun clearActiveRoom() {
        prefs.edit().remove(KEY_ACTIVE_ROOM).apply()
    }

    fun setActiveRoomName(name: String) {
        prefs.edit().putString(KEY_ACTIVE_ROOM_NAME, name).apply()
    }

    fun getActiveRoomName(): String {
        return prefs.getString(KEY_ACTIVE_ROOM_NAME, "") ?: ""
    }

    companion object {
        private const val KEY_ACTIVE_ROOM = "active_room_id"
        private const val KEY_ACTIVE_ROOM_NAME = "active_room_name"
    }
}
