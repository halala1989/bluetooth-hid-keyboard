package com.hidble.phonekeyboard

import android.app.Application

class InpatientApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KeyboardRuntime.initialize(this)
    }
}
