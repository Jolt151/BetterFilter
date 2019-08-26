package com.betterfilter.vpn

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.lang.Thread.sleep
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel


class MyVpnService : VpnService() {
    private var mThread: Thread? = null
    private lateinit var mInterface: ParcelFileDescriptor
    var builder = Builder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mThread = Thread(Runnable {
            mInterface = builder
                .setSession("MyVPNService")
                .addAddress("10.0.0.2", 32)
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .establish()

            //packets to be sent are queued here
            val input = FileInputStream(mInterface.fileDescriptor)

            //packets received are written here
            val output = FileInputStream(mInterface.fileDescriptor)

            val tunnel = DatagramChannel.open()
            //connect to server

            protect(tunnel.socket())
            tunnel.connect(InetSocketAddress("127.0.0.1", 8087))

            while(true) {

                sleep(10)
            }
        })

        mThread?.start()

        return Service.START_STICKY

    }

    override fun onDestroy() {
        mThread?.interrupt()
        super.onDestroy()
    }
}