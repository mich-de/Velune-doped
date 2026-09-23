/*
 * Velune Project
 * Ported from Metrolist (github.com/mostafaalagamy/Metrolist), which is based on
 * NewPipe's poToken WebView implementation (github.com/TeamNewPipe/NewPipe).
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.nikhil.yt.utils.potoken

class PoTokenException(message: String) : Exception(message)

// to be thrown if the WebView provided by the system is broken
class BadWebViewException(message: String) : Exception(message)

fun buildExceptionForJsError(error: String): Exception {
    return if (error.contains("SyntaxError"))
        BadWebViewException(error)
    else
        PoTokenException(error)
}
