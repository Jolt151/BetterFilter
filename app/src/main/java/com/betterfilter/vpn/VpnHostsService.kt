/*
 ** Copyright 2015, Mohamed Naufal
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 *
 * Modified 2019 by Michael Levi to use Kotlin.
 */

package com.betterfilter.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.betterfilter.Constants
import com.betterfilter.R
import com.betterfilter.vpn.VpnConstants.BROADCAST_VPN_STATE
import com.betterfilter.vpn.VpnConstants.VPN_DNS4
import com.betterfilter.vpn.VpnConstants.VPN_DNS6
import com.betterfilter.vpn.util.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.info
import org.jetbrains.anko.warn
import org.jetbrains.anko.error

import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.Selector
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock

class VpnHostsService: VpnService(), AnkoLogger {
    var isRunning: Boolean = false

    lateinit var vpnInterface: ParcelFileDescriptor

    private val pendingIntent: PendingIntent? = null

    private lateinit var deviceToNetworkUDPQueue: ConcurrentLinkedQueue<Packet>
    private lateinit var deviceToNetworkTCPQueue: ConcurrentLinkedQueue<Packet>
    private lateinit var networkToDeviceQueue: ConcurrentLinkedQueue<ByteBuffer>
    private var executorService: ExecutorService? = null

    private var udpSelector: Selector? = null
    private var tcpSelector: Selector? = null
    private var udpSelectorLock: ReentrantLock? = null
    private var tcpSelectorLock: ReentrantLock? = null
    private var isOAndBoot = false


    //for now, use a global var to store if the disconnect is coming from our app or anywhere else
    //there's probably a better way, but my brain is too tired right now
    private var isFromOurButton = false
    private val stopBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getBooleanExtra("isFromOurButton", false)) isFromOurButton = true
            stopVService()
        }

    }

    override fun onCreate() {
        super.onCreate()

        LocalBroadcastManager.getInstance(this).registerReceiver(stopBroadcastReceiver, IntentFilter("stop_vpn"))

        if (isOAndBoot) {
            //android 8.0 boot
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel("vhosts_channel_id", "System", NotificationManager.IMPORTANCE_NONE)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
                val notification = Notification.Builder(this, "vhosts_channel_id")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Virtual Hosts Running")
                    .build()
                startForeground(1, notification)
            }
            isOAndBoot = false
        }

        setupHostFile()

        val builder = Builder()
        builder.addAddress(VpnConstants.VPN_ADDRESS, 32)
        builder.addAddress(VpnConstants.VPN_ADDRESS6, 128)
        debug("use dns:$VPN_DNS4")
        builder.addRoute(VPN_DNS4, 32)
        builder.addRoute(VPN_DNS6, 128)
//            builder.addRoute(VPN_ROUTE,0);
//            builder.addRoute(VPN_ROUTE6,0);
        builder.addDnsServer(VPN_DNS4)
        builder.addDnsServer(VPN_DNS6)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val whiteList = arrayOf(
                "com.android.vending",
                "com.google.android.apps.docs",
                "com.google.android.apps.photos",
                "com.google.android.gm",
                "com.google.android.apps.translate",
                "com.whatsapp"
            )
            for (white in whiteList) {
                try {
                    builder.addDisallowedApplication(white)
                } catch (e: PackageManager.NameNotFoundException) {
                   error(e)
                }

            }
        }
        vpnInterface = builder.setSession(getString(R.string.app_name))
            .establish()

        isRunning = true

        try {
            udpSelector = Selector.open()
            tcpSelector = Selector.open()
            deviceToNetworkUDPQueue = ConcurrentLinkedQueue()
            deviceToNetworkTCPQueue = ConcurrentLinkedQueue()
            networkToDeviceQueue = ConcurrentLinkedQueue()
            udpSelectorLock = ReentrantLock()
            tcpSelectorLock = ReentrantLock()
            executorService = Executors.newFixedThreadPool(5)
            executorService?.submit(UDPInput(networkToDeviceQueue, udpSelector, udpSelectorLock))
            executorService?.submit(
                UDPOutput(
                    deviceToNetworkUDPQueue,
                    networkToDeviceQueue,
                    udpSelector,
                    udpSelectorLock,
                    this
                )
            )
            executorService?.submit(TCPInput(networkToDeviceQueue, tcpSelector, tcpSelectorLock))
            executorService?.submit(
                TCPOutput(
                    deviceToNetworkTCPQueue,
                    networkToDeviceQueue,
                    tcpSelector,
                    tcpSelectorLock,
                    this
                )
            )
            executorService?.submit(
                VPNRunnable(vpnInterface.fileDescriptor, deviceToNetworkUDPQueue, deviceToNetworkTCPQueue, networkToDeviceQueue)
            )
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BROADCAST_VPN_STATE).putExtra("running", true))
            info("Started")
        } catch (e: Exception) {
            // TODO: Here and elsewhere, we should explicitly notify the user of any errors
            // and suggest that they stop the service, since we can't do it ourselves
            error("Error starting service $e")
            stopVService()
        }


    }

    private fun setupHostFile() {
        val settings = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        var isLocal = settings.getBoolean(Constants.IS_LOCAL, true)

        //temporarily set islocal to false so we get a file from the web
        isLocal = false

        val uri_path = settings.getString(Constants.HOSTS_URI, null)


        try {
            val inputStream =
                if (isLocal) contentResolver.openInputStream(Uri.parse(uri_path))
                else openFileInput(Constants.NET_HOST_FILE)

            Thread {
                DnsChange.handle_hosts(inputStream)
            }.start()

        } catch (e: Exception) {
            error("error setup host file service $e")
        }

    }

    private fun stopVService() {
        executorService?.shutdownNow()
        isRunning = false
        cleanup()
        stopSelf()
        debug("Stopping")
    }

    private fun cleanup() {
        closeResources(udpSelector, tcpSelector, vpnInterface)

        udpSelectorLock = null
        tcpSelectorLock = null
        deviceToNetworkTCPQueue.clear()
        deviceToNetworkUDPQueue.clear()
        networkToDeviceQueue.clear()
        ByteBufferPool.clear()
    }

    private fun closeResources(vararg resources: Closeable?) {
        for (resource in resources) {
            resource?.close()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        startActivity(Intent(this, AutoRestartActivity::class.java).putExtra("isFromOurButton", isFromOurButton))
        isFromOurButton = false
        return super.onUnbind(intent)

    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stopBroadcastReceiver)
        super.onDestroy()
    }

    inner class VPNRunnable(
        private val vpnFileDescriptor: FileDescriptor,
        private val deviceToNetworkUDPQueue: ConcurrentLinkedQueue<Packet>,
        private val deviceToNetworkTCPQueue: ConcurrentLinkedQueue<Packet>,
        private val networkToDeviceQueue: ConcurrentLinkedQueue<ByteBuffer>
    ) : Runnable {

        override fun run() {
            info("Started")

            val vpnInput = FileInputStream(vpnFileDescriptor).channel
            val vpnOutput = FileOutputStream(vpnFileDescriptor).channel
            try {
                var bufferToNetwork: ByteBuffer? = null
                var dataSent = true
                var dataReceived: Boolean
                while (!Thread.interrupted()) {
                    if (dataSent)
                        bufferToNetwork = ByteBufferPool.acquire()
                    else
                        bufferToNetwork!!.clear()

                    // TODO: Block when not connected
                    val readBytes = vpnInput.read(bufferToNetwork)
                    if (readBytes > 0) {
                        dataSent = true
                        bufferToNetwork!!.flip()
                        val packet = Packet(bufferToNetwork)
                        if (packet.isUDP) {
                            deviceToNetworkUDPQueue.offer(packet)
                        } else if (packet.isTCP) {
                            deviceToNetworkTCPQueue.offer(packet)
                        } else {
                            warn("Unknown packet type")
                            dataSent = false
                        }
                    } else {
                        dataSent = false
                    }
                    val bufferFromNetwork = networkToDeviceQueue.poll()
                    if (bufferFromNetwork != null) {
                        bufferFromNetwork.flip()
                        while (bufferFromNetwork.hasRemaining())
                            try {
                                vpnOutput.write(bufferFromNetwork)
                            } catch (e: Exception) {
                                error(e)
                                break
                            }

                        dataReceived = true
                        ByteBufferPool.release(bufferFromNetwork)
                    } else {
                        dataReceived = false
                    }

                    // TODO: Sleep-looping is not very battery-friendly, consider blocking instead
                    // Confirm if throughput with ConcurrentQueue is really higher compared to BlockingQueue
                    if (!dataSent && !dataReceived)
                        Thread.sleep(11)
                }
            } catch (e: InterruptedException) {
                info( "Stopping")
            } catch (e: IOException) {
               warn(e)
            } finally {
                closeResources(vpnInput, vpnOutput)
            }
        }
    }

}

object VpnConstants {
    const val VPN_ADDRESS = "192.0.2.111"
    const val VPN_ADDRESS6 = "fe80:49b1:7e4f:def2:e91f:95bf:fbb6:1111"
    const val VPN_ROUTE = "0.0.0.0" // Intercept everything
    const val VPN_ROUTE6 = "::" // Intercept everything
    val VPN_DNS4 = "8.8.8.8" //TODO: get from user prefs
    const val VPN_DNS6 = "2001:4860:4860::8888"

    val BROADCAST_VPN_STATE = VpnHostsService::class.java.name + ".VPN_STATE"
    val ACTION_CONNECT = VpnHostsService::class.java.name + ".START"
    val ACTION_DISCONNECT = VpnHostsService::class.java.name + ".STOP"


}