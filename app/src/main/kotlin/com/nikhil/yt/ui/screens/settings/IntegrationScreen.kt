/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.nikhil.yt.LocalPlayerAwareWindowInsets
import com.nikhil.yt.R
import com.nikhil.yt.ui.component.PreferenceGroupTitle
import com.nikhil.yt.constants.ListenBrainzEnabledKey
import com.nikhil.yt.constants.ListenBrainzTokenKey
import com.nikhil.yt.ui.component.EditTextPreference
import com.nikhil.yt.ui.component.IconButton
import com.nikhil.yt.ui.component.InfoLabel
import com.nikhil.yt.ui.component.PreferenceEntry
import com.nikhil.yt.ui.component.SwitchPreference
import com.nikhil.yt.ui.component.TextFieldDialog
import com.nikhil.yt.ui.utils.backToMain
import com.nikhil.yt.utils.rememberPreference
import com.nikhil.yt.constants.DeezerArlKey
import com.nikhil.yt.constants.DeezerQualityKey
import com.nikhil.yt.constants.EnableDeezerKey
import com.nikhil.yt.ui.component.ListPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current

    val (listenBrainzEnabled, onListenBrainzEnabledChange) = rememberPreference(ListenBrainzEnabledKey, false)
    val (listenBrainzToken, onListenBrainzTokenChange) = rememberPreference(ListenBrainzTokenKey, "")
    val (enableDeezer, onEnableDeezerChange) = rememberPreference(EnableDeezerKey, false)
    val (deezerArl, onDeezerArlChange) = rememberPreference(DeezerArlKey, "")
    val (deezerQuality, onDeezerQualityChange) = rememberPreference(DeezerQualityKey, "MP3_128")

    var showListenBrainzTokenEditor = remember { mutableStateOf(false) }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        PreferenceGroupTitle(
            title = "Integrazione Discord",
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.discord_integration)) },
            icon = { Icon(painterResource(R.drawable.discord), null) },
            onClick = {
                navController.navigate("settings/discord")
            },
        )

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
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.scrobbling),
        )

        PreferenceEntry(
            title = { Text(stringResource(R.string.lastfm_integration)) },
            icon = { Icon(painterResource(R.drawable.token), null) },
            onClick = {
                navController.navigate("settings/lastfm")
            },
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.listenbrainz_scrobbling)) },
            description = stringResource(R.string.listenbrainz_scrobbling_description),
            icon = { Icon(painterResource(R.drawable.token), null) },
            checked = listenBrainzEnabled,
            onCheckedChange = onListenBrainzEnabledChange,
        )
        PreferenceEntry(
            title = { Text(if (listenBrainzToken.isBlank()) stringResource(R.string.set_listenbrainz_token) else stringResource(R.string.edit_listenbrainz_token)) },
            icon = { Icon(painterResource(R.drawable.token), null) },
            onClick = { showListenBrainzTokenEditor.value = true },
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.integration)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )

    if (showListenBrainzTokenEditor.value) {
        TextFieldDialog(
            initialTextFieldValue = androidx.compose.ui.text.input.TextFieldValue(listenBrainzToken),
            onDone = { data ->
                onListenBrainzTokenChange(data)
                showListenBrainzTokenEditor.value = false
            },
            onDismiss = { showListenBrainzTokenEditor.value = false },
            singleLine = true,
            maxLines = 1,
            isInputValid = {
                it.isNotEmpty()
            },
            extraContent = {
                InfoLabel(text = stringResource(R.string.listenbrainz_scrobbling_description))
            }
        )
    }
}
