package com.notsonata.comixium.web

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient

/** What Android reports about this app opening a domain's links (Android 12+: Open by default). */
enum class LinkAccess {
    /** Links come to the app (or Android asks with a chooser, before Android 12). */
    ALLOWED,
    /** "Open supported links" is off for the app. */
    SWITCHED_OFF,
    /** The switch is on, but this domain isn't ticked under "Add link". */
    DOMAIN_NOT_ADDED,
}

/** Things a WebView needs from whichever activity is currently showing it. */
interface ActivityServices {
    /** Open the system file picker for an `<input type=file>`; false if it couldn't be shown. */
    fun chooseFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean

    /** The user long-pressed an image on a page. */
    fun onImageLongPress(imageUrl: String, pageTitle: String)

    /** Whether Android hands https links for [host] to this app (Android 12+ asks the user once). */
    fun linkAccess(host: String): LinkAccess

    /** Opens [url] in the user's default browser (a full browser, not a Custom Tab). */
    fun openInBrowser(url: String)

    /** The system screen where the user lets this app open its links ("Open by default"). */
    fun openLinkSettings()
}
