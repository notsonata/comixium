package com.notsonata.comixium.web

import android.graphics.drawable.Drawable
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

/** A browser app the user can pick for Google sign-in. */
class BrowserApp(val packageName: String, val label: String, val icon: Drawable?)

/** Google's sign-in page waiting for the user to pick one of [apps]. */
class BrowserChoice(val url: String, val apps: List<BrowserApp>)

/** Things a WebView needs from whichever activity is currently showing it. */
interface ActivityServices {
    /** Open the system file picker for an `<input type=file>`; false if it couldn't be shown. */
    fun chooseFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean

    /** The user long-pressed an image on a page. */
    fun onImageLongPress(imageUrl: String, pageTitle: String)

    /** Whether Android hands https links for [host] to this app (Android 12+ asks the user once). */
    fun linkAccess(host: String): LinkAccess

    /**
     * Opens [url] in a browser (a full browser, not a Custom Tab): the default one if set, the only
     * one if there's one, else the user picks from [WebEngine.browserChoice], which lists browsers only.
     */
    fun openInBrowser(url: String)

    /** Opens [url] in the browser app [packageName]. */
    fun openInBrowserApp(url: String, packageName: String)

    /** The system screen where the user lets this app open its links ("Open by default"). */
    fun openLinkSettings()
}
