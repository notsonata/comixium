package com.notsonata.comixium.web

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient

/** Things a WebView needs from whichever activity is currently showing it. */
interface ActivityServices {
    /** Open the system file picker for an `<input type=file>`; false if it couldn't be shown. */
    fun chooseFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean

    /** The user long-pressed an image on a page. */
    fun onImageLongPress(imageUrl: String, pageTitle: String)
}
