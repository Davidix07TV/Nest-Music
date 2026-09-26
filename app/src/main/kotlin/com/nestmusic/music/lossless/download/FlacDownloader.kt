package com.nestmusic.music.lossless.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nestmusic.music.constants.DownloadLocationUriKey
import com.nestmusic.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

object FlacDownloader {
    fun downloadFlac(
        context: Context,
        songId: String,
        title: String,
        artist: String,
        album: String,
    ) {
        val data = Data.Builder()
            .putString("songId", songId)
            .putString("title", title)
            .putString("artist", artist)
            .putString("album", album)
            .build()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<FlacDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("flac_download_$songId", ExistingWorkPolicy.KEEP, request)
    }

    fun deleteFlac(
        context: Context,
        songId: String,
        title: String,
        artist: String,
        album: String,
    ) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("flac_download_$songId")
        wm.pruneWork()
        CoroutineScope(Dispatchers.IO).launch {
            val treeUriString = context.dataStore.data.first()[DownloadLocationUriKey] ?: ""
            if (treeUriString.isNotEmpty()) {
                try {
                    val treeUri = Uri.parse(treeUriString)
                    val saf = SafDirectoryManager(context)
                    val albumDir = saf.getOrCreateAlbumDirectory(treeUri, artist, album)
                    albumDir?.let {
                        val fileName = "${sanitizeFileName(title)}.flac"
                        it.findFile(fileName)?.delete()
                    }
                    saf.clearCache()
                } catch (_: Exception) {}
            } else {
                try {
                    val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    val nestDir = File(musicDir, "NestMusic")
                    val artistDir = File(nestDir, sanitizeFileName(artist))
                    val albumDir = File(artistDir, sanitizeFileName(album))
                    val file = File(albumDir, "${sanitizeFileName(title)}.flac")
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
        }
    }
}

fun sanitizeFileName(name: String): String {
    return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().takeIf { it.isNotEmpty() } ?: "Unknown"
}
