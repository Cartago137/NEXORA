package com.nexora.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A game entry keeps the original document URI. NEXORA does not silently move,
 * rename, or delete the user's ROM file when importing it.
 */
data class GameEntry(
    val name: String,
    val uri: String,
    val sizeBytes: Long,
    val lastPlayedAt: Long = 0L,
    val folderId: String? = null,
    val isPinned: Boolean = false
)

data class GameFolder(
    val id: String,
    val name: String,
    val isPinned: Boolean = false
)

object GameLibraryStore {
    private const val PREFS_NAME = "nexora_library"
    private const val GAMES_KEY = "games_json"
    private const val FOLDERS_KEY = "folders_json"

    fun load(context: Context): List<GameEntry> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(GAMES_KEY, "[]") ?: "[]"

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        GameEntry(
                            name = item.getString("name"),
                            uri = item.getString("uri"),
                            sizeBytes = item.optLong("sizeBytes", 0L),
                            lastPlayedAt = item.optLong("lastPlayedAt", 0L),
                            folderId = item.optString("folderId").takeIf { it.isNotBlank() },
                            isPinned = item.optBoolean("isPinned", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }


    fun loadFolders(context: Context): List<GameFolder> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(FOLDERS_KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        GameFolder(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            isPinned = item.optBoolean("isPinned", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveFolders(context: Context, folders: List<GameFolder>) {
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

    fun save(context: Context, games: List<GameEntry>) {
        val array = JSONArray()
        games.forEach { game ->
            array.put(
                JSONObject()
                    .put("name", game.name)
                    .put("uri", game.uri)
                    .put("sizeBytes", game.sizeBytes)
                    .put("lastPlayedAt", game.lastPlayedAt)
                    .put("folderId", game.folderId ?: "")
                    .put("isPinned", game.isPinned)
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(GAMES_KEY, array.toString())
            .apply()
    }
}
