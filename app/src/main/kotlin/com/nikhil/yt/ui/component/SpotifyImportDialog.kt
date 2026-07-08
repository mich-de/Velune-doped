package com.nikhil.yt.ui.component

import com.nikhil.yt.models.toMediaMetadata
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.nikhil.yt.LocalDatabase
import com.nikhil.yt.R
import com.nikhil.yt.db.entities.PlaylistEntity
import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.innertube.models.SongItem
import com.nikhil.yt.utils.SpotifyImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@Composable
fun SpotifyImportDialog(
    isVisible: Boolean,
    snackbarHostState: SnackbarHostState? = null,
    onDismiss: () -> Unit,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var isImporting by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    fun showMessage(message: String) {
        coroutineScope.launch {
            if (snackbarHostState != null) {
                snackbarHostState.showSnackbar(message)
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (isVisible) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null) },
            title = { Text(text = "Importa da Spotify") },
            initialTextFieldValue = TextFieldValue(""),
            autoFocus = true,
            onDismiss = {
                if (!isImporting) {
                    onDismiss()
                }
            },
            extraContent = {
                if (isImporting) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        VeluneLoader(size = 48.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            onDone = { enteredUrl ->
                if (enteredUrl.isBlank()) {
                    showMessage("Inserisci un link valido")
                    return@TextFieldDialog
                }
                
                isImporting = true
                statusText = "Connessione a Spotify..."
                
                coroutineScope.launch(Dispatchers.IO) {
                    val result = SpotifyImporter.fetchPlaylist(enteredUrl)
                    result.onSuccess { spotifyPlaylist ->
                        val tracks = spotifyPlaylist.tracks
                        if (tracks.isEmpty()) {
                            showMessage("Nessun brano trovato nella playlist")
                            withContext(Dispatchers.Main) {
                                isImporting = false
                                onDismiss()
                            }
                            return@launch
                        }

                        // Create local playlist
                        val newPlaylist = PlaylistEntity(
                            name = spotifyPlaylist.name,
                            bookmarkedAt = LocalDateTime.now(),
                            isEditable = true
                        )
                        database.query { insert(newPlaylist) }
                        
                        val playlist = database.playlist(newPlaylist.id).firstOrNull()
                        if (playlist == null) {
                            showMessage("Errore nella creazione della playlist")
                            withContext(Dispatchers.Main) {
                                isImporting = false
                                onDismiss()
                            }
                            return@launch
                        }

                        var matchCount = 0
                        val matchIds = mutableListOf<String>()

                        tracks.forEachIndexed { index, track ->
                            val queryText = "${track.title} ${track.artist}"
                            withContext(Dispatchers.Main) {
                                statusText = "Cerco (${index + 1}/${tracks.size}): ${track.title}..."
                            }

                            val searchResult = YouTube.search(queryText, YouTube.SearchFilter.FILTER_SONG)
                            searchResult.onSuccess { search ->
                                val bestMatch = search.items.distinctBy { it.id }.firstOrNull() as? SongItem
                                if (bestMatch != null) {
                                    val media = bestMatch.toMediaMetadata()
                                    try {
                                        database.insert(media)
                                        matchIds.add(bestMatch.id)
                                        matchCount++
                                    } catch (_: Exception) {}
                                }
                            }
                        }

                        if (matchIds.isNotEmpty()) {
                            database.addSongToPlaylist(playlist, matchIds)
                        }

                        showMessage("Importato: $matchCount su ${tracks.size} brani in '${spotifyPlaylist.name}'")
                        withContext(Dispatchers.Main) {
                            isImporting = false
                            onDismiss()
                        }
                    }.onFailure { exception ->
                        showMessage("Errore: ${exception.message ?: "Non riuscito"}")
                        withContext(Dispatchers.Main) {
                            isImporting = false
                            onDismiss()
                        }
                    }
                }
            }
        )
    }
}
