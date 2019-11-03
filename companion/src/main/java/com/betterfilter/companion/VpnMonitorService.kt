package com.betterfilter.companion

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.info
import java.lang.IllegalArgumentException
import java.util.concurrent.TimeUnit

class VpnMonitorService : Service(), AnkoLogger {

    private val subscriptions = CompositeDisposable()

    override fun onCreate() {
        super.onCreate()

        info("starting service")

        subscriptions.add(Observable.interval(10, TimeUnit.SECONDS)
            .timeInterval()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe {
                val con = createPackageContext("com.betterfilter", 0)
                val latestRunningStatus = try { VpnStatus.valueOf(con.defaultSharedPreferences.getString("latestVpnStatus", "no status")) }
                                            catch (e: IllegalArgumentException) { "no status" }
                if (latestRunningStatus != VpnStatus.RUNNING) {
                    info("latest running status is not running")
                    val intent = Intent().setComponent(ComponentName("com.betterfilter", "com.betterfilter.AutoRestartActivity"))
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } else {
                    info("latest status is running")
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        subscriptions.clear()
        info("service destroyed")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}

enum class VpnStatus {
    STARTING, RUNNING, RECONNECTING, STOPPING, STOPPED,
}
