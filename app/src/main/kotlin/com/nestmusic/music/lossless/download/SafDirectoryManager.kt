package com.nestmusic.music.lossless.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafDirectoryManager(private val context: Context) {
    private val cache = mutableMapOf<String, DocumentFile>()

    suspend fun getOrCreateAlbumDirectory(treeUri: Uri, artist: String, album: String): DocumentFile? =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext null
            val artistName = sanitizeFileName(artist)
            val albumName = sanitizeFileName(album)
            val artistDir = getOrCreateDir(root, artistName) ?: return@withContext null
            getOrCreateDir(artistDir, albumName)
        }

    private fun getOrCreateDir(parent: DocumentFile, name: String): DocumentFile? {
        val key = "${parent.uri}_$name"
        cache[key]?.let { if (it.exists()) return it }
        var dir = parent.findFile(name)
        if (dir == null || !dir.exists()) {
            dir = parent.createDirectory(name)
        }
        if (dir != null) cache[key] = dir
        return dir
    }

    fun clearCache() { cache.clear() }
}
