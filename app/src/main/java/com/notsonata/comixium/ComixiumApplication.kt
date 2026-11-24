package com.notsonata.comixium

import android.app.Application
import com.notsonata.comixium.web.WebEngine

class ComixiumApplication : Application() {
    lateinit var engine: WebEngine
        private set

    override fun onCreate() {
        super.onCreate()
        engine = WebEngine(this)
    }
}
