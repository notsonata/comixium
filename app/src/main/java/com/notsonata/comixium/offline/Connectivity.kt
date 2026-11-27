package com.notsonata.comixium.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the phone is online (a validated connection) and whether that connection is metered.
 * Android's own answer can lag when the connection drops while the app is open (a Wi-Fi with no
 * internet stays "validated" until Android checks again), so a site request that fails for lack of
 * a connection ([noteUnreachable]) also counts as offline for a little while, and asks Android to
 * check again.
 */
class Connectivity(context: Context) {

    data class Net(val online: Boolean, val unmetered: Boolean)

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val reach = Reachability()
    private val state: MutableStateFlow<Net>
    val net: StateFlow<Net> get() = state

    /** Read on WebView threads for every request, so it's a plain volatile copy. */
    @Volatile var online: Boolean = false
        private set

    /** Called on the main thread whenever [online] changes. */
    var onChange: ((Net) -> Unit)? = null

    /** The network Android's callbacks last spoke about (callback thread only). */
    private var current: Network? = null
    private var lastReport = 0L

    init {
        reach.system = read()
        state = MutableStateFlow(reach.state(SystemClock.elapsedRealtime()))
        online = state.value.online
        // The callbacks carry the state: asking ConnectivityManager again from inside one can give the
        // network that is just going away.
        runCatching {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val changed = network != current
                    current = network
                    system(from(caps), newNetwork = changed)
                }

                override fun onLost(network: Network) {
                    if (current != null && network != current) return
                    current = null
                    system(Net(online = false, unmetered = false), newNetwork = true)
                }
            })
        }
    }

    /** A site request failed for lack of a connection (any thread). */
    fun noteUnreachable() {
        main.post {
            val now = SystemClock.elapsedRealtime()
            reach.suspect(now)
            publish()
            main.postDelayed(::publish, Reachability.SUSPECT_MS + 50)
            // Ask Android to check the connection again (at most every half minute).
            if (reach.system.online && now - lastReport > REPORT_EVERY_MS) {
                lastReport = now
                runCatching { cm?.activeNetwork?.let { cm.reportNetworkConnectivity(it, false) } }
            }
        }
    }

    private fun system(n: Net, newNetwork: Boolean) {
        main.post {
            reach.onSystem(n, newNetwork)
            publish()
        }
    }

    private fun publish() {
        val n = reach.state(SystemClock.elapsedRealtime())
        online = n.online
        if (state.value != n) {
            state.value = n
            onChange?.invoke(n)
        }
    }

    private fun read(): Net {
        val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return Net(online = cm == null, unmetered = cm == null)
        return from(caps)
    }

    private fun from(caps: NetworkCapabilities) = Net(
        online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )

    private companion object {
        const val REPORT_EVERY_MS = 30_000L
    }
}

/**
 * What the app goes by: Android's state, except offline for [SUSPECT_MS] after the site couldn't be
 * reached. A new connection (or the old one coming back) is tried straight away. Plain Kotlin, for tests.
 */
internal class Reachability {
    var system = Connectivity.Net(online = false, unmetered = false)
    private var suspectUntil = 0L

    fun suspect(now: Long) {
        suspectUntil = now + SUSPECT_MS
    }

    fun onSystem(n: Connectivity.Net, newNetwork: Boolean) {
        if (newNetwork || (n.online && !system.online)) suspectUntil = 0
        system = n
    }

    fun state(now: Long): Connectivity.Net = if (now < suspectUntil) system.copy(online = false) else system

    companion object {
        const val SUSPECT_MS = 20_000L
    }
}
