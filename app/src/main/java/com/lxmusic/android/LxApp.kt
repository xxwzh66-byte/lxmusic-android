package com.lxmusic.android

import android.app.Application
import com.lxmusic.android.data.db.AppDatabase
import com.lxmusic.android.data.settings.SettingsStore

class LxApp : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var settingsStore: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        // J1: capture uncaught exceptions before anything else can fail.
        CrashLogger.install(this)
        instance = this
        db = AppDatabase.get(this)
        settingsStore = SettingsStore.get(this)
    }

    companion object {
        lateinit var instance: LxApp
            private set
    }
}
