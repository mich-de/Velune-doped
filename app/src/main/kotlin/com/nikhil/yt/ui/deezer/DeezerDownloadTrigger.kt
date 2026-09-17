/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.ui.deezer

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.widget.Toast
import com.nikhil.yt.deezer.Deezer

suspend fun loginToDeezer(context: Context, arl: String): Result<Unit> {
    Deezer.setLogDir(context.cacheDir)
    Deezer.setArl(arl)
    return Deezer.login()
}

/**
 * Logs in to Deezer, searches for [title]/[artist], downloads the best match and
 * saves it into Music/Deezer/ via MediaStore.
 *
 * Plain suspend function - call it from inside your own `coroutineScope.launch { }`,
 * it does not launch its own coroutine.
 */
suspend fun downloadFromDeezer(
    context: Context,
    arl: String,
    quality: String,
    title: String,
    artist: String,
    onDownloaded: () -> Unit = {},
    onComplete: () -> Unit = {},
) {
    try {
        val loginResult = loginToDeezer(context, arl)
        loginResult.onFailure {
            Toast.makeText(context, "Deezer: Login failed - ${it.message}", Toast.LENGTH_LONG).show()
            return
        }
        val cacheDir = context.cacheDir.resolve("deezer_tmp")
        val result = Deezer.searchAndDownload(title, artist, cacheDir, quality)
        result.onSuccess { file ->
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Deezer")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            file.delete()
            Toast.makeText(context, "Saved: ${file.name} → Music/Deezer/", Toast.LENGTH_SHORT).show()
            onDownloaded()
        }.onFailure {
            Toast.makeText(context, "Deezer: ${it.message}", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Deezer: ${e.javaClass.simpleName}: ${e.message}", Toast.LENGTH_LONG).show()
    } finally {
        onComplete()
    }
}
