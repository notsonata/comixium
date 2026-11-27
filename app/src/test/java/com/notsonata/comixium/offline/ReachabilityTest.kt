package com.notsonata.comixium.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReachabilityTest {
    private val on = Connectivity.Net(online = true, unmetered = true)
    private val off = Connectivity.Net(online = false, unmetered = false)

    @Test fun followsAndroidUntilTheSiteCantBeReached() {
        val r = Reachability()
        r.onSystem(on, newNetwork = true)
        assertTrue(r.state(0).online)
        // A request failed for lack of a connection: offline for a while, even though Android still says online.
        r.suspect(1_000)
        assertFalse(r.state(1_000).online)
        assertFalse(r.state(1_000 + Reachability.SUSPECT_MS - 1).online)
        assertTrue(r.state(1_000 + Reachability.SUSPECT_MS).online)
        // Another failure starts the wait again.
        r.suspect(5_000)
        assertFalse(r.state(1_000 + Reachability.SUSPECT_MS).online)
    }

    @Test fun aNewOrReturningConnectionIsTriedStraightAway() {
        val r = Reachability()
        r.onSystem(on, newNetwork = true)
        r.suspect(0)
        // The same network reporting again (signal, bandwidth) changes nothing.
        r.onSystem(on, newNetwork = false)
        assertFalse(r.state(1).online)
        // Another network.
        r.onSystem(on, newNetwork = true)
        assertTrue(r.state(1).online)
        // Android checked again and found no internet, then it came back.
        r.suspect(2)
        r.onSystem(off, newNetwork = false)
        assertFalse(r.state(3).online)
        r.onSystem(on, newNetwork = false)
        assertTrue(r.state(4).online)
    }

    @Test fun offlineIsOfflineWhateverElse() {
        val r = Reachability()
        r.onSystem(off, newNetwork = true)
        assertFalse(r.state(Long.MAX_VALUE).online)
    }
}
