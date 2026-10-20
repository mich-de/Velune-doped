/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.ui.deezer

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nikhil.yt.R
import com.nikhil.yt.constants.DeezerArlKey
import com.nikhil.yt.constants.DeezerQualityKey
import com.nikhil.yt.constants.EnableDeezerKey
import com.nikhil.yt.models.MediaMetadata
import com.nikhil.yt.ui.component.VeluneLoader
import com.nikhil.yt.utils.rememberPreference
import kotlinx.coroutines.launch

@Composable
fun DeezerDownloadButton(
    mediaMetadata: MediaMetadata,
    iconButtonColor: Color,
    textBackgroundColor: Color,
    size: androidx.compose.ui.unit.Dp = 24.dp,
    backgroundColor: Color? = null,
    containerSize: androidx.compose.ui.unit.Dp? = null,
    shape: androidx.compose.ui.graphics.Shape = CircleShape
) {
    val context = LocalContext.current
    val (enableDeezerPref) = rememberPreference(EnableDeezerKey, false)
    val (deezerArl) = rememberPreference(DeezerArlKey, "")
    val (deezerQuality) = rememberPreference(DeezerQualityKey, "MP3_128")
    val coroutineScope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }

    if (!enableDeezerPref) return

    Box(
        modifier = Modifier
            .size(containerSize ?: (size + 12.dp))
            .clip(shape)
            .then(if (backgroundColor != null) Modifier.background(backgroundColor) else Modifier)
            .clickable(enabled = !isDownloading) {
                if (deezerArl.isBlank()) {
                    Toast.makeText(context, "Deezer: enter ARL in Settings → Integrations", Toast.LENGTH_LONG).show()
                    return@clickable
                }
                isDownloading = true
                coroutineScope.launch {
                    downloadFromDeezer(
                        context = context,
                        arl = deezerArl,
                        quality = deezerQuality,
                        title = mediaMetadata.title,
                        artist = mediaMetadata.artists.joinToString(", ") { it.name },
                        onComplete = { isDownloading = false },
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isDownloading) {
            VeluneLoader(size = size, color = iconButtonColor)
        } else {
            Icon(
                painter = painterResource(R.drawable.download),
                contentDescription = "Download from Deezer",
                tint = iconButtonColor,
                modifier = Modifier.size(size)
            )
        }
    }
}
