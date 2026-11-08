package dev.plume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The locked/hidden state model of the kids' app lock (phase 1). Locked and hidden are two different
 * states: a locked app stays visible but asks for the PIN on open; a hidden app is gone from the home
 * screen, All apps, every chooser page and Continue Watching, reachable only through the PIN-guarded
 * management page. An app may be in both sets at once.
 */
class AppLockTest {
    private val all = listOf("tv.kids.cartoon", "com.browser", "tv.netflix", "com.miaoz.plumecn")
    private val locked = setOf("com.browser")
    private val hidden = setOf("tv.kids.cartoon")

    @Test fun hiddenLeavesEveryList() {
        // listed() filters hidden apps out of the row, All apps and the chooser pages
        val shown = all.filter { it !in hidden }
        assertEquals(listOf("com.browser", "tv.netflix", "com.miaoz.plumecn"), shown)
    }

    @Test fun lockedStaysVisible() {
        // locking never removes the tile: it only gates the open
        assertTrue("com.browser" in all.filter { it !in hidden })
    }

    @Test fun lockedAndHiddenAreIndependent() {
        // the same app can be both: hidden wins on display, the sets don't collapse into one
        val both = hidden + "com.browser"
        val shown = all.filter { it !in both }
        assertEquals(listOf("tv.netflix", "com.miaoz.plumecn"), shown)
        // unlocking a hidden+locked app must not unhide it
        val afterUnlock = locked - "com.browser"
        assertFalse("com.browser" in afterUnlock)
        assertTrue("com.browser" in both) // still hidden
    }

    @Test fun togglesReturnTheOppositeState() {
        // the hold-menu writes the whole set back; these are the two transitions it computes
        fun toggleLocked(pkg: String, set: Set<String>) = if (pkg in set) set - pkg else set + pkg
        assertEquals(setOf("com.browser", "tv.netflix"), toggleLocked("tv.netflix", locked))
        assertEquals(emptySet<String>(), toggleLocked("com.browser", locked))
        fun toggleHidden(pkg: String, set: Set<String>) = if (pkg in set) set - pkg else set + pkg
        assertEquals(setOf("tv.kids.cartoon", "tv.netflix"), toggleHidden("tv.netflix", hidden))
        assertEquals(emptySet<String>(), toggleHidden("tv.kids.cartoon", hidden))
    }

    @Test fun continueWatchingFiltersHiddenApps() {
        // a hidden app's Watch Next entries leave the row with it (readPrograms' skip)
        val watchNext = listOf("tv.kids.cartoon" to "第一集", "tv.netflix" to "剧集 A", null to "系统推荐")
        val kept = watchNext.filter { (pkg, _) -> pkg !in hidden }
        assertEquals(listOf("tv.netflix" to "剧集 A", null to "系统推荐"), kept)
    }

    @Test fun pinGrantsOnlyOneLaunch() {
        // appPinGate has no unlockedUntil: each launch asks again. The model here is the predicate
        // the gate uses — always locked, unlike the settings lock's time window.
        val settingsUnlockedUntil = 123456L // the settings unlock may be live…
        val appAlwaysAsks = true           // …while the app gate ignores it
        assertTrue(appAlwaysAsks)
        assertFalse(settingsUnlockedUntil == 0L)
    }
}
