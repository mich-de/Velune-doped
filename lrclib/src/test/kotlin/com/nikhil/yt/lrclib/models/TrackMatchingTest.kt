package com.nikhil.yt.lrclib.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMatchingTest {
    private fun track(id: Int, duration: Double) =
        Track(
            id = id,
            trackName = "Song",
            artistName = "Artist",
            duration = duration,
            plainLyrics = null,
            syncedLyrics = "[00:01.00] line",
        )

    @Test
    fun bestMatchingFor_prefersTheClosestDuration() {
        val tracks = listOf(track(1, 170.0), track(2, 160.5), track(3, 165.0))
        assertEquals(2, tracks.bestMatchingFor(159)?.id)
    }

    @Test
    fun bestMatchingFor_acceptsAShortRemasterWhenNothingCloserExists() {
        val tracks = listOf(track(1, 349.0), track(2, 164.9), track(3, 166.5))
        assertEquals(2, tracks.bestMatchingFor(159)?.id)
    }

    @Test
    fun bestMatchingFor_rejectsVersionsThatAreTooDifferent() {
        val tracks = listOf(track(1, 349.0), track(2, 243.7))
        assertNull(tracks.bestMatchingFor(159))
    }
}
