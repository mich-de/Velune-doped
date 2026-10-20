/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.ui.screens.settings

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import androidx.navigation.NavController
import com.nikhil.yt.LocalPlayerAwareWindowInsets
import com.nikhil.yt.R
import com.nikhil.yt.constants.DeezerArlKey
import com.nikhil.yt.ui.component.IconButton
import com.nikhil.yt.ui.utils.backToMain
import com.nikhil.yt.utils.rememberPreference

private const val JS_FLOW = """
(function () {
  function r(len) {
    var c = 'abcdefghijklmnopqrstuvwxyz0123456789';
    var s = '';
    for (var i = 0; i < len; i++) s += c[Math.floor(Math.random() * c.length)];
    return s;
  }
  var email = 'deezerbot' + r(8) + '@outlook.com';
  var password = 'Dz' + r(10) + '!A1';
  var username = 'dzuser' + r(6);

  function gw(method, token, body) {
    return fetch(
      'https://www.deezer.com/ajax/gw-light.php?method=' + method +
      '&input=3&api_version=1.0.0&api_token=' + token +
      '&cid=' + Math.floor(Math.random() * 999999),
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        credentials: 'include'
      }
    ).then(function (r) { return r.json(); });
  }

  gw('deezer.getUserData', '', { APP_NAME: 'Deezer' }).then(function (ud) {
    var token = (ud.results && ud.results.USER_TOKEN) || '';
    if (!token) {
      window.DzGen.onResult(JSON.stringify({ error: 'No API token' }));
      return;
    }
    gw('user.create', token, {
      APP_NAME: 'Deezer',
      EMAIL: email,
      PASSWORD: password,
      BLOG_NAME: username,
      SEX: 'M',
      BIRTHDAY: '1995-06-15',
      JOURNEY_VERSION: 'unlogged_smart_and_login_web_v1'
    }).then(function (res) {
      var arl = (res.results && res.results.arl) || '';
      window.DzGen.onResult(JSON.stringify({
        arl: arl,
        email: email,
        password: password,
        username: username,
        error: arl ? '' : JSON.stringify(res.error || '')
      }));
    });
  });
})();
"""

@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeezerGenerateScreen(navController: NavController) {
    var deezerArl by rememberPreference(DeezerArlKey, "")
    var status by remember { mutableStateOf("Avvio WebView nascosta...") }
    var arlText by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    val genHolder = remember { GenBridge() }

    genHolder.onResult = { raw ->
        arlText = raw
        val result = org.json.JSONObject(raw)
        val arl = result.optString("arl")
        val err = result.optString("error")
        if (arl.isNotEmpty()) {
            deezerArl = arl
            status = "ARL generato: ${arl.take(16)}..."
            done = true
        } else {
            status = "Errore generazione: ${err.take(200)}"
            done = true
        }
    }

    Scaffold(
        contentWindowInsets = LocalPlayerAwareWindowInsets.current,
        topBar = {
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Generazione ARL automatica",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Viene creata un nuovo account Deezer usa-e-getta (come register-final.js di d33z3r) " +
                    "eseguendo le chiamate dentro una WebView reale di deezer.com. Se riesce, l'ARL " +
                    "viene salvato automaticamente.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (!done) {
                    LinearProgressIndicator(
                        progress = { 0.6f },
                        modifier = Modifier.size(width = 20.dp, height = 20.dp)
                    )
                }
                Text(
                    status,
                    modifier = Modifier.padding(start = 12.dp),
                    color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            if (arlText.isNotEmpty()) {
                Text(
                    arlText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Invisible (1dp) WebView hosting the browser context + JS flow.
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                    .size(1.dp),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(Color.TRANSPARENT)
                        isVisible = false
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                        }
                        addJavascriptInterface(genHolder, "DzGen")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                if (url == null || !url.contains("deezer.com")) return
                                status = "Pagina caricata, eseguo user.create..."
                                evaluateJavascript(JS_FLOW, null)
                            }
                        }
                        loadUrl("https://www.deezer.com/us/")
                    }
                }
            )
        }
    }

    BackHandler(enabled = true) {
        navController.navigateUp()
    }
}

class GenBridge {
    var onResult: ((String) -> Unit)? = null

    @JavascriptInterface
    fun onResult(json: String) {
        onResult?.invoke(json)
    }
}