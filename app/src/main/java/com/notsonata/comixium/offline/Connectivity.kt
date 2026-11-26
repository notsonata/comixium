package com.notsonata.comixium.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether the phone is online (a validated connection) and whether that connection is metered. */
class Connectivity(context: Context) {

    data class Net(val online: Boolean, val unmetered: Boolean)

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val state = MutableStateFlow(read())
    val net: StateFlow<Net> = state

    /** Read on WebView threads for every request, so it's a plain volatile copy. */
    @Volatile var online: Boolean = state.value.online
        private set

    init {
        runCatching {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update(from(caps))
                override fun onLost(network: Network) = update(read())
                override fun onAvailable(network: Network) = update(read())
            })
        }
    }

    private fun update(n: Net) {
        online = n.online
        state.value = n
    }

    private fun read(): Net {
        val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return Net(online = cm == null, unmetered = cm == null)
        return from(caps)
    }

    private fun from(caps: NetworkCapabilities) = Net(
        online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )
}
