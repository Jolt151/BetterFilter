package com.betterfilter

import android.annotation.SuppressLint
import android.app.Application
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import org.jetbrains.anko.AnkoLogger
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