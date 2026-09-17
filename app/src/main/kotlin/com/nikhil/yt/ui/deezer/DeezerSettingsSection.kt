/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.ui.deezer

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nikhil.yt.R
import com.nikhil.yt.constants.DeezerArlKey
import com.nikhil.yt.constants.DeezerQualityKey
import com.nikhil.yt.constants.EnableDeezerKey
import com.nikhil.yt.ui.component.EditTextPreference
import com.nikhil.yt.ui.component.InfoLabel
import com.nikhil.yt.ui.component.ListPreference
import com.nikhil.yt.ui.component.PreferenceGroupTitle
import com.nikhil.yt.ui.component.SwitchPreference
import com.nikhil.yt.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Deezer section of Settings > Integrations. Owns its own prefs + context so
 * IntegrationScreen only needs to call DeezerSettingsSection() with no parameters.
 */
@Composable
fun DeezerSettingsSection(navController: NavController) {
    val context = LocalContext.current
    val (enableDeezer, onEnableDeezerChange) = rememberPreference(EnableDeezerKey, false)
    val (deezerArl, onDeezerArlChange) = rememberPreference(DeezerArlKey, "")
    val (deezerQuality, onDeezerQualityChange) = rememberPreference(DeezerQualityKey, "MP3_128")

    PreferenceGroupTitle(
        title = "Deezer",
    )
    SwitchPreference(
        title = { Text("Deezer downloads") },
        description = "Download current song from Deezer at 128kbps (requires USA IP)",
        icon = { Icon(painterResource(R.drawable.download), null) },
        checked = enableDeezer,
        onCheckedChange = onEnableDeezerChange,
    )
    if (enableDeezer) {
        EditTextPreference(
            title = { Text("Deezer ARL token") },
            value = deezerArl,
            onValueChange = onDeezerArlChange,
            singleLine = false,
            isInputValid = { it.isNotEmpty() },
            icon = { Icon(painterResource(R.drawable.token), null) },
        )
        ListPreference(
            title = { Text("Deezer download quality") },
            icon = { Icon(painterResource(R.drawable.tune), null) },
            selectedValue = deezerQuality,
            values = listOf("MP3_128", "MP3_320", "FLAC"),
            valueText = { it },
            onValueSelected = onDeezerQualityChange,
        )
        InfoLabel(text = "Get ARL: login to deezer.com from USA IP → F12 → Application → Cookies → arl → copy value")

        val coroutineScope = rememberCoroutineScope()

        FilledTonalButton(
            onClick = { navController.navigate("settings/deezer/login") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text("Login Deezer (genera ARL)")
        }

        FilledTonalButton(
            onClick = { navController.navigate("settings/deezer/generate") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text("Genera nuovo account + ARL")
        }

        FilledTonalButton(
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    val result = loginToDeezer(context, deezerArl)
                    val msg = result.fold(
                        onSuccess = { "Deezer ARL: valid ✓" },
                        onFailure = { "Deezer ARL: invalid - ${it.message}" }
                    )
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text("Test Deezer connection")
        }

        FilledTonalButton(
            onClick = {
                val tmpDir = context.cacheDir.resolve("deezer_tmp")
                if (tmpDir.exists()) {
                    tmpDir.deleteRecursively()
                    Toast.makeText(context, "Deezer cache cleared", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No Deezer cache to clear", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text("Clear Deezer download cache")
        }
    }
}
