package dev.plume

import org.junit.Assert.assertEquals
import org.junit.Test

class PosterArtTest {
    // ---------- pickArt: which of the two arts the app publishes to load ----------

    @Test fun widePosterWins() {
        // The poster is the bigger artwork; when its shape is wide (0 is 16:9) it leads, thumbnail stays as fallback.
        assertEquals("poster" to "thumb", pickArt("thumb", "poster", 0, 0))
        assertEquals("poster" to "thumb", pickArt("thumb", "poster", 0, 1)) // 3:2 poster
        assertEquals("poster" to "thumb", pickArt("thumb", "poster", 0, 2)) // 4:3 poster
    }

    @Test fun wideThumbnailBeatsTallPoster() {
        // A tall poster (2:3 is 4, a movie poster 5) on the 2:1 card shows as a strip: the wide thumbnail leads.
        assertEquals("thumb" to "poster", pickArt("thumb", "poster", 0, 4))
        assertEquals("thumb" to "poster", pickArt("thumb", "poster", 0, 5))
        assertEquals("thumb" to "poster", pickArt("thumb", "poster", 2, 3)) // 4:3 thumbnail over 1:1 poster
    }

    @Test fun noShapeStillPrefersThePoster() {
        // Ratios of 0 mean unset or 16:9 — both wide. The provider's default makes the poster lead.
        assertEquals("poster" to "thumb", pickArt("thumb", "poster", 0, 0))
    }

    @Test fun missingSides() {
        assertEquals("poster" to null, pickArt(null, "poster", 0, 0))
        assertEquals("thumb" to null, pickArt("thumb", null, 0, 0))
        assertEquals(null to null, pickArt(null, null, 0, 0))
    }

    // ---------- posterSampleSize: decode between the card's width and twice that ----------

    @Test fun smallArtIsNeverShrunk() {
        assertEquals(1, posterSampleSize(300, 888))
        assertEquals(1, posterSampleSize(888, 888))
        assertEquals(1, posterSampleSize(889, 888))
    }

    @Test fun shrinkStopsAtTheCardWidth() {
        // 1920 on an 888 card: /2 is 960 >= 888 so sample 2; /4 is 480 < 888 so it stops there — 480 would
        // stretch. 1920 on a 426 card (1080p) also stops at 4.
        assertEquals(2, posterSampleSize(1920, 888))
        assertEquals(4, posterSampleSize(1920, 426))
        assertEquals(4, posterSampleSize(3840, 888))
        assertEquals(1, posterSampleSize(1920, 961)) // /2 = 960 misses the card: no shrink
    }

    @Test fun degenerateInputs() {
        assertEquals(1, posterSampleSize(0, 0))
        assertEquals(1, posterSampleSize(100, 0))
        assertEquals(1, posterSampleSize(0, 888))
    }
}

class HdCoverUrlTest {
    private val qpic = "http://vcover-hz-pic.puui.qpic.cn/vcover_hz_pic/0/mzc00200zsqz54y/408"

    @Test fun rewritesTencentWidthsToTheOriginal() {
        assertEquals("http://vcover-hz-pic.puui.qpic.cn/vcover_hz_pic/0/mzc00200zsqz54y/0", hdCoverUrl(qpic))
        assertEquals("http://vcover-hz-pic.puui.qpic.cn/vcover_hz_pic/0/mzc00200zsqz54y/0",
            hdCoverUrl("http://vcover-hz-pic.puui.qpic.cn/vcover_hz_pic/0/mzc00200zsqz54y/260"))
    }

    @Test fun anyWidthRewritesToTheOriginal() {
        assertEquals(qpic.replace("/408", "/0"), hdCoverUrl(qpic)) // 408 rewrites
        val zero = "http://vcover-hz-pic.puui.qpic.cn/vcover_hz_pic/0/mzc00200zsqz54y/0"
        assertEquals(zero, hdCoverUrl(zero)) // /0 rewrites to itself: the card gets the same picture
    }

    @Test fun leavesOtherSourcesAlone() {
        assertEquals(null, hdCoverUrl(null))
        assertEquals(null, hdCoverUrl("content://com.example/art/12"))
        assertEquals(null, hdCoverUrl("https://example.com/cover.jpg"))
        assertEquals(null, hdCoverUrl("http://vcover-hz-pic.puui.qpic.cn/vcover_hz_pic/0/mzc00200zsqz54y/orig")) // non-numeric tail
    }
}
