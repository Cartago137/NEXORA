package com.nexora.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stores user-created library folders independently from ROM files. */
object GameFolderStore {
    private const val PREFS_NAME = "nexora_library"
    private const val FOLDERS_KEY = "folders_json"

    fun load(context: Context): List<GameFolder> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(FOLDERS_KEY, "[]") ?: "[]"

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val id = item.optString("id")
                    val name = item.optString("name").trim()
                    if (id.isNotBlank() && name.isNotBlank()) {
                        add(GameFolder(id = id, name = name, isPinned = item.optBoolean("isPinned", false)))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, folders: List<GameFolder>) {
        val array = JSONArray()
        folders.forEach { folder ->
            array.put(
                JSONObject()
                    .put("id", folder.id)
                    .put("name", folder.name)
                    .put("isPinned", folder.isPinned)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(FOLDERS_KEY, array.toString())
            .apply()
    }
}
