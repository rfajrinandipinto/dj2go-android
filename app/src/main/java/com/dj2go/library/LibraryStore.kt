package com.dj2go.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the chosen music folder and the scanned crate, and walks a folder
 * tree looking for audio files.
 *
 * The folder is stored as a SAF tree URI with a persisted read permission, so
 * the app can keep loading from it across restarts.
 */
object LibraryStore {

    private const val PREFS = "dj2go_library"
    private const val KEY_FOLDER = "folder"
    private const val KEY_TRACKS = "tracks"
    private const val MAX_TRACKS = 2000
    private const val MAX_DEPTH = 6

    private val AUDIO_EXTENSIONS = listOf(
        ".mp3", ".m4a", ".aac", ".wav", ".flac", ".ogg", ".opus", ".wma"
    )

    fun folder(context: Context): String? =
        prefs(context).getString(KEY_FOLDER, null)

    fun saveFolder(context: Context, uri: String?) {
        prefs(context).edit().putString(KEY_FOLDER, uri).apply()
    }

    fun tracks(context: Context): List<LibraryTrack> {
        val raw = prefs(context).getString(KEY_TRACKS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val uri = obj.optString("uri")
                if (uri.isNullOrEmpty()) null
                else LibraryTrack(uri, obj.optString("name", "track"))
            }
        }.getOrDefault(emptyList())
    }

    fun saveTracks(context: Context, tracks: List<LibraryTrack>) {
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(JSONObject().apply {
                put("uri", track.uri)
                put("name", track.name)
            })
        }
        prefs(context).edit().putString(KEY_TRACKS, array.toString()).apply()
    }

    fun scan(context: Context, treeUri: Uri): List<LibraryTrack> {
        val root = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
            ?: return emptyList()
        val result = mutableListOf<LibraryTrack>()
        walk(root, result, 0)
        result.sortBy { it.name.lowercase() }
        return result
    }

    private fun walk(dir: DocumentFile, out: MutableList<LibraryTrack>, depth: Int) {
        if (depth > MAX_DEPTH || out.size >= MAX_TRACKS) return
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            if (out.size >= MAX_TRACKS) return
            when {
                child.isDirectory -> walk(child, out, depth + 1)
                isAudio(child) -> out.add(
                    LibraryTrack(child.uri.toString(), child.name ?: "track")
                )
            }
        }
    }

    private fun isAudio(file: DocumentFile): Boolean {
        val type = file.type
        if (type != null && type.startsWith("audio/")) return true
        val name = file.name?.lowercase() ?: return false
        return AUDIO_EXTENSIONS.any { name.endsWith(it) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
