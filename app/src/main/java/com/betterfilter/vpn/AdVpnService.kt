/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * Derived from AdBuster:
 * Copyright (C) 2016 Daniel Brodie <dbrodie@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package com.betterfilter.vpn

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.*
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.betterfilter.*
import com.betterfilter.antibypass.AutoRestartActivity
import com.betterfilter.antibypass.VpnMonitorJob
import com.betterfilter.ui.MainActivity

import java.lang.ref.WeakReference

import io.reactivex.subjects.BehaviorSubject
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.db.*
import org.jetbrains.anko.defaultSharedPreferences
import java.net.InetAddress
import java.util.HashSet

class AdVpnService : VpnService(), Handler.Callback, AnkoLogger {


    private val handler = MyHandler(this)
    private var vpnThread: AdVpnThread? =
        AdVpnThread(this) { value ->
            handler.sendMessage(
                handler.obtainMessage(
                    VPN_MSG_STATUS_UPDATE,
                    value,
                    0
                )
            )
        }
    private val connectivityChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.sendMessage(handler.obtainMessage(VPN_MSG_NETWORK_CHANGED, intent))
        }
    }

    private var isFromOurButton = false

    /* The handler may only keep a weak reference around, otherwise it leaks */
    private class MyHandler(callback: Handler.Callback) : Handler() {
        private val callback: WeakReference<Handler.Callback>

        init {
            this.callback = WeakReference(callback)
        }

        override fun handleMessage(msg: Message) {
            val callback = this.callback.get()
            callback?.handleMessage(msg)
            super.handleMessage(msg)
        }
    }

    override fun onCreate() {
        super.onCreate()

        /*
        Start the VpnMonitor Jobs
         */
        val jobScheduler = getSystemService(JobScheduler::class.java)

        //Start the VpnMonitor the first time
        val initBuilder = JobInfo.Builder(0, ComponentName(this, VpnMonitorJob::class.java))
        initBuilder.setMinimumLatency(1000 * 20)
        initBuilder.setPersisted(true)
        jobScheduler.schedule(initBuilder.build())

        //Start the VpnMonitor every 15 minutes, even if the app dies
        val periodicBuilder = JobInfo.Builder(1, ComponentName(this, VpnMonitorJob::class.java))
        periodicBuilder.setPeriodic(1000 * 60 * 15)
        periodicBuilder.setPersisted(true)
        jobScheduler.schedule(periodicBuilder.build())

        val workingMode: String = defaultSharedPreferences.getString("filterMode", "mode_whitelist")
        if (workingMode == "mode_blacklist") workingModeState = WorkingModeState.BLACKLIST
        else workingModeState = WorkingModeState.WHITELIST
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand $intent")

        //isFromOurButton = intent?.getBooleanExtra("isFromOurButton", false) ?: false

        when (intent?.getIntExtra("COMMAND", Command.START.ordinal)) {
            Command.START.ordinal -> {
                startVpn()
            }
            Command.STOP.ordinal -> {
                isFromOurButton = true
                stopVpn()
            }
            Command.PAUSE.ordinal -> {
                pauseVpn()
            }
            Command.RESUME.ordinal -> {
                /*val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancelAll()*/
                // fallthrough
            }
        }

        return Service.START_STICKY
    }

    private fun pauseVpn() {
        stopVpn()
    }

    private fun updateVpnStatus(status: VpnStatus) {
        vpnStatus = status

        val intent = Intent(VPN_UPDATE_STATUS_INTENT)
        intent.putExtra(VPN_UPDATE_STATUS_EXTRA, status)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


    private fun startVpn(/*notificationIntent: PendingIntent?*/) {
        updateVpnStatus(VpnStatus.STARTING)

        registerReceiver(
            connectivityChangedReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        )

        //restartVpnThread()

         vpnThread?.startThread()
    }

    private fun restartVpnThread() {
        vpnThread?.stopThread()
        vpnThread?.startThread()
    }


    private fun stopVpnThread() {
        vpnThread?.stopThread()
    }

/*    private fun waitForNetVpn() {
        stopVpnThread()
        updateVpnStatus(VPN_STATUS_WAITING_FOR_NETWORK)
    }*/

    private fun reconnect() {
        updateVpnStatus(VpnStatus.RECONNECTING)
        restartVpnThread()
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping Service")
        if (vpnThread != null)
            stopVpnThread()
        vpnThread = null
        try {
            unregisterReceiver(connectivityChangedReceiver)
        } catch (e: IllegalArgumentException) {
            Log.i(TAG, "Ignoring exception on unregistering receiver")
        }

        updateVpnStatus(VpnStatus.STOPPED)
        stopSelf()
    }

    override fun onDestroy() {
        Log.i(TAG, "Destroyed, shutting down")
        stopVpnThread()

        val jobScheduler = getSystemService(JobScheduler::class.java)
        jobScheduler.cancelAll()
    }

    override fun onUnbind(intent: Intent): Boolean {
        if (!isFromOurButton) {
            startActivity(
                Intent(this, AutoRestartActivity::class.java)
                    .putExtra("isFromOurButton", isFromOurButton)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        isFromOurButton = false

        return super.onUnbind(intent)
    }

    override fun handleMessage(message: Message?): Boolean {
        if (message == null) {
            return true
        }

        when (message.what) {
            VPN_MSG_STATUS_UPDATE -> {
                when (message.arg1) {
                    0 -> updateVpnStatus(VpnStatus.STARTING)
                    1 -> updateVpnStatus(VpnStatus.RUNNING)
                    2 -> updateVpnStatus(VpnStatus.STOPPING)
                    3 -> {} //waiting for network
                    4 -> updateVpnStatus(VpnStatus.RECONNECTING)
                    5 -> {} //reconnecting network error
                    6 -> updateVpnStatus(VpnStatus.STOPPED)

                }

                //updateVpnStatus(message.arg1)
            }
            VPN_MSG_NETWORK_CHANGED -> //info("ignoring")//TODO: Determine whether we need to restart the vpn on receiving connection so the dns servers are initialized right
                //otherwise, we want to keep the vpn connected at all times.
                //It works on our AOSP device because AOSP doesn't need the addRoute()s for the dns servers
                //We need this for Samsung devices.
                connectivityChanged(message.obj as Intent)
            else -> throw IllegalArgumentException("Invalid message with what = " + message.what)
        }
        return true
    }

    private fun connectivityChanged(intent: Intent) {
        if (intent.getIntExtra(
                ConnectivityManager.EXTRA_NETWORK_TYPE,
                0
            ) == ConnectivityManager.TYPE_VPN
        ) {
            Log.i(TAG, "Ignoring connectivity changed for our own network")
            return
        }

        if (ConnectivityManager.CONNECTIVITY_ACTION != intent.action) {
            Log.e(TAG, "Got bad intent on connectivity changed " + intent.action!!)
        }
        if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
            Log.i(TAG, "Connectivity changed to no connectivity, wait for a network")
            //waitForNetVpn()
        } else {
            Log.i(TAG, "Network changed, seeing if we need to reconnect")

            data class DnsServer(val address: String)
            /*
            Only try to reconnect if we have new DNS servers that aren't currently in use
             */
            val currentDnsServers = getDnsServersWithoutCaching(this)

            Log.i(TAG, "currentDnsServers: ${currentDnsServers.map { it.hostAddress }}" )

            database.use {
                select("in_use_dns_servers", "address").exec {
                    val inUseServers: List<String> = parseList(classParser<DnsServer>()).map { it.address }
                    Log.i(TAG, "inUseServers: $inUseServers" )

                    currentDnsServers.forEach {
                        if (!inUseServers.contains(it.hostAddress)) {
                            reconnect()
                            return@exec
                        }
                    }
                }
            }
        }
    }

    private fun getDnsServersWithoutCaching(context: Context): Set<InetAddress> {

        val out = HashSet<InetAddress>()
        val cm =
            context.getSystemService(VpnService.CONNECTIVITY_SERVICE) as ConnectivityManager
        // Seriously, Android? Seriously?
        val activeInfo = cm.activeNetworkInfo /*?: throw VpnNetworkException(
                "No DNS Server"
            )*/ ?: run {
            return out
        }

        for (nw in cm.allNetworks) {
            val ni = cm.getNetworkInfo(nw)
            if (ni == null || !ni.isConnected || ni.type != activeInfo.type
                || ni.subtype != activeInfo.subtype
            )
                continue
            for (address in cm.getLinkProperties(nw).dnsServers) {
                out.add(address)
            }
        }
        return out
    }

    companion object {

        //initialized in oncreate
        lateinit var workingModeState: WorkingModeState

        val NOTIFICATION_ID_STATE = 10
        val REQUEST_CODE_START = 43
        val REQUEST_CODE_PAUSE = 42
        val VPN_STATUS_STARTING = 0
        val VPN_STATUS_RUNNING = 1
        val VPN_STATUS_STOPPING = 2
        val VPN_STATUS_WAITING_FOR_NETWORK = 3
        val VPN_STATUS_RECONNECTING = 4
        val VPN_STATUS_RECONNECTING_NETWORK_ERROR = 5
        val VPN_STATUS_STOPPED = 6
        val VPN_UPDATE_STATUS_INTENT = "org.jak_linux.dns66.VPN_UPDATE_STATUS"
        val VPN_UPDATE_STATUS_EXTRA = "VPN_STATUS"
        private val VPN_MSG_STATUS_UPDATE = 0
        private val VPN_MSG_NETWORK_CHANGED = 1
        private val TAG = "VpnService"



        // TODO: Temporary Hack til refactor is done
        var vpnStatus = VpnStatus.STOPPED
            private set(value) {
                field = value
                isRunningObservable.onNext(value)
            }

        val isRunningObservable = BehaviorSubject.createDefault(vpnStatus)
    }
}

enum class Command {
    START, STOP, PAUSE, RESUME, RESTART
}

enum class VpnStatus {
    STARTING, RUNNING, RECONNECTING, STOPPING, STOPPED,
}

/**
 * How our vpn is running. We are either allowing everything and blocking sites selectively,
 * or we're blocking everything and allowing apps selectively.
 */
enum class WorkingModeState {
    BLACKLIST, WHITELIST
}
