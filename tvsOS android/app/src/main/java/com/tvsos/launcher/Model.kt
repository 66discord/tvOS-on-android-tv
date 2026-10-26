package com.tvsos.launcher

import org.json.JSONArray
import org.json.JSONObject

data class AppItem(val pkg: String, val label: String)

/**
 * Sala: um filtro de apps com nome proprio.
 * Lista de apps vazia significa "todos os apps visiveis".
 */
class Room(
    val id: String,
    var name: String,
    val apps: MutableList<String> = mutableListOf()
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        val arr = JSONArray()
        for (a in apps) arr.put(a)
        o.put("apps", arr)
        return o
    }

    companion object {
        const val ALL = "all"

        fun fromJson(o: JSONObject): Room {
            val id = o.optString("id", "")
            val name = o.optString("name", "Sala")
            val room = Room(id, name)
            val arr = o.optJSONArray("apps")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val p = arr.optString(i, "")
                    if (p.isNotEmpty()) room.apps.add(p)
                }
            }
            return room
        }
    }
}
