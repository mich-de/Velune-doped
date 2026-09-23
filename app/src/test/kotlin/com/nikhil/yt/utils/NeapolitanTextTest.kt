package com.nikhil.yt.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeapolitanTextTest {
    private val neapolitan =
        listOf(
            "Nun te scurdà ca te voglio bene assaje",
            "Quanno fa notte e 'o mare se fa scuro",
            "Stasera jammo a mangià 'na pizza a Mergellina",
            "Aggio perzo 'a capa pe' te",
        )

    private val italian =
        listOf(
            "Stasera la città è piena di luci",
            "Non so perché ti penso ancora",
            "Camminiamo insieme sotto la pioggia",
            "Ogni volta che ridi mi sento meglio",
        )

    @Test
    fun looksNeapolitan_detectsNeapolitanLyrics() {
        assertTrue(NeapolitanText.looksNeapolitan(neapolitan))
    }

    @Test
    fun looksNeapolitan_ignoresItalianAndEnglishLyrics() {
        assertFalse(NeapolitanText.looksNeapolitan(italian))
        assertFalse(NeapolitanText.looksNeapolitan(listOf("Na na na, hey", "I love you baby", "Na na na")))
    }

    @Test
    fun looksNeapolitan_needsMoreThanOneStrayWord() {
        assertFalse(NeapolitanText.looksNeapolitan(italian + "Comme te chiamme?"))
    }

    @Test
    fun normalize_rewritesCommonWordsAndKeepsCapitals() {
        assertEquals("Ho perso la testa per te", NeapolitanText.normalize("Aggio perzo 'a capa pe' te"))
        assertEquals("Quando fa notte e il mare se fa scuro", NeapolitanText.normalize("Quanno fa notte e 'o mare se fa scuro"))
    }

    @Test
    fun normalize_splitsGluedArticlesAndHandlesDoubledConsonants() {
        assertEquals("Vieni per la strada", NeapolitanText.normalize("Vieni pe'a strada"))
        assertEquals("vede la luna", NeapolitanText.normalize("vede'a luna"))
        assertEquals("Lo sa pure mamma", NeapolitanText.normalize("'O ssape pure mammà"))
        assertEquals("L'acqua brucia tanto", NeapolitanText.normalize("L'acqua coce assaje"))
        assertEquals("l'amore", NeapolitanText.normalize("l'amore"))
    }

    @Test
    fun normalize_handlesTypographicApostrophesAndLeavesFillersAlone() {
        assertEquals("Il cuore mio", NeapolitanText.normalize("’O core mio"))
        assertEquals("Na na na", NeapolitanText.normalize("Na na na"))
    }
}
