/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.ui.screens.settings

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.nikhil.yt.LocalPlayerAwareWindowInsets
import com.nikhil.yt.R
import com.nikhil.yt.constants.DeezerArlKey
import com.nikhil.yt.ui.component.IconButton
import com.nikhil.yt.ui.utils.backToMain
import com.nikhil.yt.utils.rememberPreference

/**
 * Loads Deezer's own login page in a WebView -- you log into YOUR OWN account
 * (including any captcha/2FA Deezer shows, since it's the real page). No account
 * creation, no bot-detection evasion: once Deezer sets the `arl` session cookie,
 * it's read via CookieManager and saved, same mechanism LoginScreen.kt already
 * uses for the YouTube Music cookie.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeezerLoginScreen(navController: NavController) {
    var deezerArl by rememberPreference(DeezerArlKey, "")
    var webView: WebView? = null

    AndroidView(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (url == null || !url.contains("deezer.com")) return
                        val cookies = CookieManager.getInstance().getCookie(url) ?: return
                        val arl = cookies.split(";")
                            .map { it.trim() }
                            .firstOrNull { it.startsWith("arl=") }
                            ?.removePrefix("arl=")
                        if (!arl.isNullOrBlank() && arl != deezerArl) {
                            deezerArl = arl
                            if (navController.currentBackStackEntry?.destination?.route == "settings/deezer/login") {
                                navController.navigateUp()
                            }
                        }
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                webView = this
                loadUrl("https://www.deezer.com/login")
            }
        }
    )

    TopAppBar(
        title = { Text(stringResource(R.string.action_login)) },
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

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}
