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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.betterfilter.*

import com.betterfilter.vpn.util.FileHelper
import com.betterfilter.vpn.util.NotificationChannels

import java.lang.ref.WeakReference

import io.reactivex.subjects.BehaviorSubject
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.db.*
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
    private val notificationBuilder =
        NotificationCompat.Builder(this, NotificationChannels.SERVICE_RUNNING)
            //.setSmallIcon(R.drawable.ic_state_deny) // TODO: Notification icon
            .setPriority(Notification.PRIORITY_MIN)

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
        //NotificationChannels.onCreate(this);

        /*        notificationBuilder.addAction(R.drawable.ic_pause_black_24dp, getString(R.string.notification_action_pause),
                PendingIntent.getService(this, REQUEST_CODE_PAUSE, new Intent(this, AdVpnService.class)
                                .putExtra("COMMAND", Command.PAUSE.ordinal()), 0));*/

        notificationBuilder.addAction(
            R.drawable.ic_close_red_24dp, getString(R.string.notification_action_pause),
            PendingIntent.getService(
                this,
                REQUEST_CODE_PAUSE, Intent(this, AdVpnService::class.java)
                    .putExtra("COMMAND", Command.PAUSE.ordinal), 0
            )
        )
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
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        /*        notificationManager.notify(NOTIFICATION_ID_STATE, new NotificationCompat.Builder(this, NotificationChannels.SERVICE_PAUSED)
                .setSmallIcon(R.drawable.ic_state_deny) // TODO: Notification icon
                .setPriority(Notification.PRIORITY_LOW)
                .setContentTitle(getString(R.string.notification_paused_title))
                .setContentText(getString(R.string.notification_paused_text))
                .setContentIntent(PendingIntent.getService(this, REQUEST_CODE_START, getResumeIntent(this), PendingIntent.FLAG_ONE_SHOT))
                .build());*/
    }

    private fun updateVpnStatus(status: Int) {
        vpnStatus = status

        val notificationTextId =
            vpnStatusToTextId(status)
        notificationBuilder.setContentText(getString(notificationTextId))

        //if (FileHelper.loadCurrentSettings(getApplicationContext()).showNotification)
        //  startForeground(NOTIFICATION_ID_STATE, notificationBuilder.build());

        val intent = Intent(VPN_UPDATE_STATUS_INTENT)
        intent.putExtra(VPN_UPDATE_STATUS_EXTRA, status)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


    private fun startVpn(/*notificationIntent: PendingIntent?*/) {
/*        notificationBuilder.setContentTitle(getString(R.string.notification_title))
        if (notificationIntent != null)
            notificationBuilder.setContentIntent(notificationIntent)*/
        updateVpnStatus(VPN_STATUS_STARTING)

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

    private fun waitForNetVpn() {
        stopVpnThread()
        updateVpnStatus(VPN_STATUS_WAITING_FOR_NETWORK)
    }

    private fun reconnect() {
        updateVpnStatus(VPN_STATUS_RECONNECTING)
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

        updateVpnStatus(VPN_STATUS_STOPPED)
        stopSelf()
    }

    override fun onDestroy() {
        Log.i(TAG, "Destroyed, shutting down")
        stopVpnThread()
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
            VPN_MSG_STATUS_UPDATE -> updateVpnStatus(message.arg1)
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
        var vpnStatus = VPN_STATUS_STOPPED
            private set(value) {
                field = value
                isRunningObservable.onNext(value == 1)
            }

        val isRunningObservable = BehaviorSubject.createDefault(vpnStatus == 1)

        fun vpnStatusToTextId(status: Int): Int {
            when (status) {
                VPN_STATUS_STARTING -> return R.string.notification_starting
                VPN_STATUS_RUNNING -> return R.string.notification_running
                VPN_STATUS_STOPPING -> return R.string.notification_stopping
                VPN_STATUS_WAITING_FOR_NETWORK -> return R.string.notification_waiting_for_net
                VPN_STATUS_RECONNECTING -> return R.string.notification_reconnecting
                VPN_STATUS_RECONNECTING_NETWORK_ERROR -> return R.string.notification_reconnecting_error
                VPN_STATUS_STOPPED -> return R.string.notification_stopped
                else -> throw IllegalArgumentException("Invalid vpnStatus value ($status)")
            }
        }

        fun checkStartVpnOnBoot(context: Context) {
            Log.i("BOOT", "Checking whether to start ad buster on boot")
            val config = FileHelper.loadCurrentSettings(context)
            if (config == null || !config.autoStart) {
                return
            }
            if (!context.getSharedPreferences("state", Context.MODE_PRIVATE).getBoolean(
                    "isActive",
                    false
                )
            ) {
                return
            }

            if (VpnService.prepare(context) != null) {
                Log.i("BOOT", "VPN preparation not confirmed by user, changing enabled to false")
            }

            Log.i("BOOT", "Starting ad buster from boot")
            //NotificationChannels.onCreate(context);

            val intent = getStartIntent(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config.showNotification) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        private fun getStartIntent(context: Context): Intent {
            val intent = Intent(context, AdVpnService::class.java)
            intent.putExtra("COMMAND", Command.START.ordinal)
            intent.putExtra(
                "NOTIFICATION_INTENT",
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java), 0
                )
            )
            return intent
        }

        private fun getResumeIntent(context: Context): Intent {
            val intent = Intent(context, AdVpnService::class.java)
            intent.putExtra("COMMAND", Command.RESUME.ordinal)
            intent.putExtra(
                "NOTIFICATION_INTENT",
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java), 0
                )
            )
            return intent
        }
    }
}

enum class Command {
    START, STOP, PAUSE, RESUME, RESTART
}

