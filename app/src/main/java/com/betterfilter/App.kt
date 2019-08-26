package com.betterfilter

import android.annotation.SuppressLint
import android.app.Application
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class App: Application() {

    companion object {
        var isAuthenticated = false
    }

    @SuppressLint("CheckResult")
    override fun onCreate() {
        super.onCreate()

        Observable.interval(30, TimeUnit.SECONDS)
            .timeInterval()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                isAuthenticated = false
            }
    }
}