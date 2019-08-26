package com.betterfilter.vpn

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import java.net.DatagramSocket
import java.net.DatagramSocketImpl
import java.net.Socket

class MyVpnService : VpnService() {
    override fun onBind(intent: Intent?): IBinder? {
        prepare(this)
        protect(Socket())
        //DatagramSocket().connect()
        return super.onBind(intent)
    }

}