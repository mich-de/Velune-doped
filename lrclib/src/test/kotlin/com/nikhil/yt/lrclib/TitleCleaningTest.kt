package com.nikhil.yt.lrclib

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleCleaningTest {
    @Test
    fun cleanTitle_removesVersionSuffixes() {
        assertEquals("Chi Tene 'O Mare", LrcLib.cleanTitle("Chi Tene 'O Mare (1995 Digital Remaster)"))
        assertEquals("Yesterday", LrcLib.cleanTitle("Yesterday - Remastered 2009"))
        assertEquals("Song", LrcLib.cleanTitle("Song [Mono Version]"))
        assertEquals("Song", LrcLib.cleanTitle("Song - Radio Edit"))
    }

    @Test
    fun cleanTitle_keepsMeaningfulParts() {
        assertEquals("Song (Live)", LrcLib.cleanTitle("Song (Live)"))
        assertEquals("Song (feat. Someone)", LrcLib.cleanTitle("Song (feat. Someone)"))
        assertEquals("Anti-Hero", LrcLib.cleanTitle("Anti-Hero"))
    }
}
