package com.betterfilter

import android.annotation.SuppressLint
import android.app.Application
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatDelegate
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.info
import java.util.concurrent.TimeUnit

class App: Application(), AnkoLogger {

    companion object {
        var isAuthenticated = false
        lateinit var instance: App
            private set
    }

    @SuppressLint("CheckResult")
    override fun onCreate() {
        super.onCreate()

        instance = this

        if (defaultSharedPreferences.getBoolean("darkMode", false)) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        Observable.interval(60, TimeUnit.SECONDS)
            .timeInterval()
            .filter{ isAuthenticated }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                info("Resetting authentication")
                isAuthenticated = false
            }
    }
}