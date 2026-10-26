package com.tvsos.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistencia simples em SharedPreferences (sem dependencias externas). */
class Store(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("tvsos.prefs", Context.MODE_PRIVATE)

    var accentIndex: Int
        get() = sp.getInt("accent", 0).coerceIn(0, C.ACCENTS.size - 1)
        set(value) {
            sp.edit().putInt("accent", value.coerceIn(0, C.ACCENTS.size - 1)).apply()
        }

    val accent: Int get() = C.ACCENTS[accentIndex]

    var bgStyle: Int
        get() = sp.getInt("bg_style", 0)
        set(value) {
            sp.edit().putInt("bg_style", value).apply()
        }

    var columns: Int
        get() = sp.getInt("columns", 5).coerceIn(3, 7)
        set(value) {
            sp.edit().putInt("columns", value.coerceIn(3, 7)).apply()
        }

    var showLabels: Boolean
        get() = sp.getBoolean("labels", true)
        set(value) {
            sp.edit().putBoolean("labels", value).apply()
        }

    var showClock: Boolean
        get() = sp.getBoolean("clock", true)
        set(value) {
            sp.edit().putBoolean("clock", value).apply()
        }

    var topShelf: Boolean
        get() = sp.getBoolean("top_shelf", true)
        set(value) {
            sp.edit().putBoolean("top_shelf", value).apply()
        }

    var animations: Boolean
        get() = sp.getBoolean("animations", true)
        set(value) {
            sp.edit().putBoolean("animations", value).apply()
        }

    var saverMinutes: Int
        get() = sp.getInt("saver", 5)
        set(value) {
            sp.edit().putInt("saver", value).apply()
        }

    var activeRoom: String
        get() = sp.getString("active_room", Room.ALL) ?: Room.ALL
        set(value) {
            sp.edit().putString("active_room", value).apply()
        }

    fun hidden(): MutableSet<String> {
        val s = sp.getStringSet("hidden", null) ?: return mutableSetOf()
        return s.toMutableSet()
    }

    fun setHidden(items: Collection<String>) {
        sp.edit().putStringSet("hidden", items.toSet()).apply()
    }

    fun pinned(): MutableList<String> = decodeList(sp.getString("pinned", null))

    fun setPinned(items: Collection<String>) {
        sp.edit().putString("pinned", encodeList(items.toList())).apply()
    }

    fun rooms(): MutableList<Room> {
        val out = mutableListOf<Room>()
        val raw = sp.getString("rooms", null)
        if (!raw.isNullOrEmpty()) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val r = Room.fromJson(o)
                    if (r.id.isNotEmpty()) out.add(r)
                }
            } catch (_: Exception) {
            }
        }
        if (out.none { it.id == Room.ALL }) {
            out.add(0, Room(Room.ALL, "Todos"))
        }
        return out
    }

    fun setRooms(rooms: List<Room>) {
        val arr = JSONArray()
        for (r in rooms) arr.put(r.toJson())
        sp.edit().putString("rooms", arr.toString()).apply()
    }

    fun reset() {
        sp.edit().clear().apply()
    }

    private fun encodeList(list: List<String>): String {
        val arr = JSONArray()
        for (s in list) arr.put(s)
        return arr.toString()
    }

    private fun decodeList(raw: String?): MutableList<String> {
        val out = mutableListOf<String>()
        if (raw.isNullOrEmpty()) return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "")
                if (s.isNotEmpty()) out.add(s)
            }
        } catch (_: Exception) {
        }
        return out
    }
}
