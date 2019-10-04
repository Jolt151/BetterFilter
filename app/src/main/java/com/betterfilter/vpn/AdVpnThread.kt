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


import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.preference.PreferenceManager
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log

import com.betterfilter.App
import com.betterfilter.Extensions.*
import com.betterfilter.R
import com.betterfilter.cursorToString
import com.betterfilter.database
import com.betterfilter.vpn.util.Configuration
import com.betterfilter.vpn.util.DnsPacketProxy
import com.betterfilter.vpn.util.FileHelper
import okhttp3.internal.and
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.db.*
import org.jetbrains.anko.info

import org.pcap4j.packet.IpPacket

import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.SocketException
import java.net.UnknownHostException
import java.util.ArrayList
import java.util.Arrays
import java.util.HashSet
import java.util.LinkedList
import kotlin.experimental.or


class AdVpnThread(private val vpnService: VpnService, private val notify: (Int) -> (Unit)) :
    Runnable, DnsPacketProxy.EventLoop, AnkoLogger {
    /* Upstream DNS servers, indexed by our IP */
    val upstreamDnsServers = ArrayList<InetAddress>()
    /* Data to be written to the device */
    private val deviceWrites = LinkedList<ByteArray>()
    // HashMap that keeps an upper limit of packets
    private val dnsIn = WospList()
    // The object where we actually handle packets.
    private val dnsPacketProxy = DnsPacketProxy(this)
    // Watch dog that checks our connection is alive.
    private val vpnWatchDog = VpnWatchdog()

    private var thread: Thread? = null
    private var mBlockFd: FileDescriptor? = null
    private var mInterruptFd: FileDescriptor? = null
    /**
     * Number of iterations since we last cleared the pcap4j cache
     */
    private val pcap4jFactoryClearCacheCounter = 0

    fun startThread() {
        Log.i(TAG, "Starting Vpn Thread")
        thread = Thread(this, "AdVpnThread")
        thread?.start()
        Log.i(TAG, "Vpn Thread started")
    }

    fun stopThread() {
        Log.i(TAG, "Stopping Vpn Thread")
        thread?.interrupt()

        mInterruptFd =
            FileHelper.closeOrWarn(mInterruptFd, TAG, "stopThread: Could not close interruptFd")
        try {
            thread?.join(2000)
        } catch (e: InterruptedException) {
            Log.w(TAG, "stopThread: Interrupted while joining thread", e)
        }

        if (thread?.isAlive == true) {
            Log.w(TAG, "stopThread: Could not kill VPN thread, it is still alive")
        } else {
            thread = null
            Log.i(TAG, "Vpn Thread stopped")
        }
    }

    @Synchronized
    override fun run() {
        Log.i(TAG, "Starting")

        // Load the block list
        try {
            dnsPacketProxy.initialize(vpnService, upstreamDnsServers)
            vpnWatchDog.initialize(FileHelper.loadCurrentSettings(vpnService).watchDog)
        } catch (e: InterruptedException) {
            return
        }

        notify(AdVpnService.VPN_STATUS_STARTING)

        var retryTimeout = MIN_RETRY_TIME
        // Try connecting the vpn continuously
        while (true) {
            var connectTimeMillis: Long = 0
            try {
                connectTimeMillis = System.currentTimeMillis()
                // If the function returns, that means it was interrupted
                runVpn()

                Log.i(TAG, "Told to stop")
                notify(AdVpnService.VPN_STATUS_STOPPING)
                break
            } catch (e: InterruptedException) {
                break
            } catch (e: VpnNetworkException) {
                // We want to filter out VpnNetworkException from out crash analytics as these
                // are exceptions that we expect to happen from network errors
                Log.w(TAG, "Network exception in vpn thread, ignoring and reconnecting", e)
                // If an exception was thrown, show to the user and try again
                //notify(AdVpnService.VPN_STATUS_RECONNECTING_NETWORK_ERROR)
            } catch (e: Exception) {
                Log.e(TAG, "Network exception in vpn thread, reconnecting", e)
                //ExceptionHandler.saveException(e, Thread.currentThread(), null);
                notify(AdVpnService.VPN_STATUS_RECONNECTING_NETWORK_ERROR)
            }

            if (System.currentTimeMillis() - connectTimeMillis >= RETRY_RESET_SEC * 1000) {
                Log.i(TAG, "Resetting timeout")
                retryTimeout = MIN_RETRY_TIME
            }

            // ...wait and try again
            Log.i(TAG, "Retrying to connect in " + retryTimeout + "seconds...")
            try {
                Thread.sleep(retryTimeout.toLong() * 1000)
            } catch (e: InterruptedException) {
                break
            }

            if (retryTimeout < MAX_RETRY_TIME)
                retryTimeout *= 2
        }

        notify(AdVpnService.VPN_STATUS_STOPPED)
        Log.i(TAG, "Exiting")
    }

    @Throws(
        InterruptedException::class,
        ErrnoException::class,
        IOException::class,
        VpnNetworkException::class
    )
    private fun runVpn() {
        // Allocate the buffer for a single packet.
        val packet = ByteArray(32767)

        // A pipe we can interrupt the poll() call with by closing the interruptFd end
        val pipes = Os.pipe()
        mInterruptFd = pipes[0]
        mBlockFd = pipes[1]

        // Authenticate and configure the virtual network interface.
        try {
            configure().use { pfd ->
                // Read and write views of the tun device
                val inputStream = FileInputStream(pfd.fileDescriptor)
                val outFd = FileOutputStream(pfd.fileDescriptor)

                // Now we are connected. Set the flag and show the message.
                notify(AdVpnService.VPN_STATUS_RUNNING)

                // We keep forwarding packets till something goes wrong.
                while (doOne(inputStream, outFd, packet))
                ;
            }
        } finally {
            mBlockFd = FileHelper.closeOrWarn(mBlockFd,
                TAG, "runVpn: Could not close blockFd")
        }
    }

    @Throws(
        IOException::class,
        ErrnoException::class,
        InterruptedException::class,
        VpnNetworkException::class
    )
    private fun doOne(
        inputStream: FileInputStream,
        outFd: FileOutputStream,
        packet: ByteArray
    ): Boolean {
        val deviceFd = StructPollfd()
        deviceFd.fd = inputStream.fd
        deviceFd.events = OsConstants.POLLIN.toShort()
        val blockFd = StructPollfd()
        blockFd.fd = mBlockFd
        blockFd.events = (OsConstants.POLLHUP or OsConstants.POLLERR).toShort()

        if (!deviceWrites.isEmpty())
            deviceFd.events = deviceFd.events or OsConstants.POLLOUT.toShort()

        val polls = arrayOfNulls<StructPollfd>(2 + dnsIn.size())
        polls[0] = deviceFd
        polls[1] = blockFd
        run {
            var i = -1
            for (wosp in dnsIn) {
                i++
                polls[2 + i] = StructPollfd()
                val pollFd = polls[2 + i]
                pollFd?.fd = ParcelFileDescriptor.fromDatagramSocket(wosp.socket).fileDescriptor
                pollFd?.events = OsConstants.POLLIN.toShort()
            }
        }

        Log.d(TAG, "doOne: Polling " + polls.size + " file descriptors")
        val result = FileHelper.poll(polls, vpnWatchDog.getPollTimeout())
        if (result == 0) {
            vpnWatchDog.handleTimeout()
            return true
        }
        if (blockFd.revents.toInt() != 0) {
            Log.i(TAG, "Told to stop VPN")
            return false
        }
        // Need to do this before reading from the device, otherwise a new insertion there could
        // invalidate one of the sockets we want to read from either due to size or time out
        // constraints
        run {
            var i = -1
            val iter = dnsIn.iterator()
            while (iter.hasNext()) {
                i++
                val wosp = iter.next()
                if ((polls[i+2]?.revents?.and(OsConstants.POLLIN)) != 0) {
                    Log.d(TAG, "Read from DNS socket" + wosp.socket)
                    iter.remove()
                    handleRawDnsResponse(wosp.packet, wosp.socket)
                    wosp.socket.close()
                }
            }
        }
        if (deviceFd.revents and OsConstants.POLLOUT != 0) {
            Log.d(TAG, "Write to device")
            writeToDevice(outFd)
        }
        if (deviceFd.revents and OsConstants.POLLIN != 0) {
            Log.d(TAG, "Read from device")
            readPacketFromDevice(inputStream, packet)
        }

        return true
    }

    @Throws(VpnNetworkException::class)
    private fun writeToDevice(outFd: FileOutputStream) {
        try {
            outFd.write(deviceWrites.poll())
        } catch (e: IOException) {
            // TODO: Make this more specific, only for: "File descriptor closed"
            throw VpnNetworkException("Outgoing VPN output stream closed")
        }

    }

    @Throws(VpnNetworkException::class, SocketException::class)
    private fun readPacketFromDevice(inputStream: FileInputStream, packet: ByteArray) {
        // Read the outgoing packet from the input stream.
        val length: Int

        try {
            length = inputStream.read(packet)
        } catch (e: IOException) {
            throw VpnNetworkException("Cannot read from device", e)
        }


        if (length == 0) {
            // TODO: Possibly change to exception
            Log.w(TAG, "Got empty packet!")
            return
        }

        val readPacket = Arrays.copyOfRange(packet, 0, length)

        vpnWatchDog.handlePacket(readPacket)
        dnsPacketProxy.handleDnsRequest(readPacket)
    }

    @Throws(VpnNetworkException::class)
    override fun forwardPacket(outPacket: DatagramPacket, parsedPacket: IpPacket?) {
        var dnsSocket: DatagramSocket? = null
        try {
            // Packets to be sent to the real DNS server will need to be protected from the VPN
            dnsSocket = DatagramSocket()

            vpnService.protect(dnsSocket)

            dnsSocket.send(outPacket)

            if (parsedPacket != null)
                dnsIn.add(
                    WaitingOnSocketPacket(
                        dnsSocket,
                        parsedPacket
                    )
                )
            else
                FileHelper.closeOrWarn(
                    dnsSocket,
                    TAG,
                    "handleDnsRequest: Cannot close socket in error"
                )
        } catch (e: IOException) {
            FileHelper.closeOrWarn<DatagramSocket>(
                dnsSocket,
                TAG,
                "handleDnsRequest: Cannot close socket in error"
            )
            if (e.cause is ErrnoException) {
                val errnoExc = e.cause as ErrnoException


                /*
                We don't want to bring the vpn down if the network is unreachable (no connection or whatever reason)
                Better that we just continue running.
                 */
/*                if (errnoExc.errno == OsConstants.ENETUNREACH || errnoExc.errno == OsConstants.EPERM) {
                    throw VpnNetworkException(
                        "Cannot send message:",
                        e
                    )
                }*/
            }
            Log.w(TAG, "handleDnsRequest: Could not send packet to upstream", e)
            return
        }

    }

    @Throws(IOException::class)
    private fun handleRawDnsResponse(parsedPacket: IpPacket, dnsSocket: DatagramSocket) {
        val datagramData = ByteArray(1024)
        val replyPacket = DatagramPacket(datagramData, datagramData.size)
        dnsSocket.receive(replyPacket)
        dnsPacketProxy.handleDnsResponse(parsedPacket, datagramData)
    }

    override fun queueDeviceWrite(ipOutPacket: IpPacket) {
        deviceWrites.add(ipOutPacket.rawData)
    }

    @Throws(UnknownHostException::class)
    fun newDNSServer(
        builder: VpnService.Builder,
        format: String?,
        ipv6Template: ByteArray?,
        addr: InetAddress
    ) {
        // Optimally we'd allow either one, but the forwarder checks if upstream size is empty, so
        // we really need to acquire both an ipv6 and an ipv4 subnet.
        if (addr is Inet6Address && ipv6Template == null) {
            Log.i(TAG, "newDNSServer: Ignoring DNS server $addr")
        } else if (addr is Inet4Address && format == null) {
            Log.i(TAG, "newDNSServer: Ignoring DNS server $addr")
        } else if (addr is Inet4Address) {
            upstreamDnsServers.add(addr)
            val alias = String.format(format!!, upstreamDnsServers.size + 1)
            Log.i(TAG, "configure: Adding DNS Server $addr as $alias")
            builder.addDnsServer(alias)
            //builder.addRoute(alias, 32)
            vpnWatchDog.setTarget(InetAddress.getByName(alias))
        } else if (addr is Inet6Address) {
            upstreamDnsServers.add(addr)
            ipv6Template?.set(ipv6Template.size - 1, (upstreamDnsServers.size + 1).toByte())
            val i6addr = Inet6Address.getByAddress(ipv6Template)
            Log.i(TAG, "configure: Adding DNS Server $addr as $i6addr")
            builder.addDnsServer(i6addr)
            vpnWatchDog.setTarget(i6addr)
        }
    }

    fun configurePackages(builder: VpnService.Builder) {
        val defaultWhitelistedApps = listOf("com.android.vending",
            "com.google.android.apps.docs",
            "com.google.android.apps.photos",
            "com.google.android.apps.translate",
            "com.whatsapp",
            "com.betterfilter"
        )


        for (white in defaultWhitelistedApps) {
            try {
                builder.addDisallowedApplication(white)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.i(TAG, "package not found: $white")
            }

        }

        builder.addWhitelistedApps(App.instance)
    }

    @Throws(VpnNetworkException::class)
    private fun configure(): ParcelFileDescriptor {
        Log.i(TAG, "Configuring$this")

        val config = FileHelper.loadCurrentSettings(vpnService)

        // Configure a builder while parsing the parameters.
        val builder = vpnService.Builder()

        /*
          builder.addRoute() specifies what traffic we should intercept.
          In our case, since we only want to intercept DNS traffic, we get the current dns servers and intercept that traffic.
          This way, all DNS traffic gets rerouted to us.
         */
        val existingDnsServers =
            getDnsServers(vpnService)
        for (i in existingDnsServers) {
            info("inserting $i")
            if (i is Inet6Address) builder.addRoute(i, 128)
            else builder.addRoute(i, 32)
        }

        /*
            Get the DNS servers we want to forward the traffic to and add those to the builder.
            Later, ALL traffic we intercept gets forwarded to these servers.
            This is why it's important to only intercept DNS traffic - we don't want to forward any other traffic to the DNS server.
         */
        //Get DNS urls from our settings
        val dnsServers = HashSet<InetAddress>()
        val dnsStrings = PreferenceManager.getDefaultSharedPreferences(vpnService).getDNSUrls()
        for (dns in dnsStrings) {
            try {
                dnsServers.add(InetAddress.getByName(dns))
            } catch (ignored: Exception) {
            }

        }

        Log.i(TAG, "Got DNS servers = $dnsServers")

        var format: String? = null

        // Determine a prefix we can use. These are all reserved prefixes for example
        // use, so it's possible they might be blocked.
        for (prefix in arrayOf("192.0.2", "198.51.100", "203.0.113")) {
            try {
                builder.addAddress("$prefix.1", 24)
            } catch (e: IllegalArgumentException) {
                continue
            }

            format = "$prefix.%d"
            break
        }

        // For fancy reasons, this is the 2001:db8::/120 subnet of the /32 subnet reserved for
        // documentation purposes. We should do this differently. Anyone have a free /120 subnet
        // for us to use?
        var ipv6Template: ByteArray? =
            byteArrayOf(32, 1, 13, (184 and 0xFF).toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

        if (hasIpV6Servers(config, dnsServers)) {
            try {
                val addr = Inet6Address.getByAddress(ipv6Template)
                Log.d(TAG, "configure: Adding IPv6 address$addr")
                builder.addAddress(addr, 120)
            } catch (e: Exception) {
                e.printStackTrace()

                ipv6Template = null
            }

        } else {
            ipv6Template = null
        }

        if (format == null) {
            Log.w(TAG, "configure: Could not find a prefix to use, directly using DNS servers")
            builder.addAddress("192.168.50.1", 24)
        }

        // Add configured DNS servers
        upstreamDnsServers.clear()

        // Add all knows DNS servers
        for (addr in dnsServers) {
            try {
                newDNSServer(builder, format, ipv6Template, addr)
            } catch (e: Exception) {
                Log.e(TAG, "configure: Cannot add server:", e)
            }

        }

        builder.setBlocking(true)

        // Allow applications to bypass the VPN
        //builder.allowBypass();

        // Explictly allow both families, so we do not block
        // traffic for ones without DNS servers (issue 129).
        builder.allowFamily(OsConstants.AF_INET)
        builder.allowFamily(OsConstants.AF_INET6)

        configurePackages(builder)

        // Create a new interface using the builder and save the parameters.
        val pfd = builder
            .setSession(this.vpnService.getString(R.string.app_name)).establish()
        Log.i(TAG, "Configured")
        return pfd
    }

    fun hasIpV6Servers(config: Configuration, dnsServers: Set<InetAddress>): Boolean {
        if (!config.ipV6Support)
            return false

        if (config.dnsServers.enabled) {
            for (item in config.dnsServers.items) {
                if (item.state == Configuration.Item.STATE_ALLOW && item.location.contains(":"))
                    return true
            }
        }
        for (inetAddress in dnsServers) {
            if (inetAddress is Inet6Address)
                return true
        }

        return false
    }

    interface Notify {
        fun run(value: Int)
    }

    internal class VpnNetworkException : Exception {
        constructor(s: String) : super(s) {}

        constructor(s: String, t: Throwable) : super(s, t) {}

    }

    /**
     * Helper class holding a socket, the packet we are waiting the answer for, and a time
     */
    private class WaitingOnSocketPacket internal constructor(
        internal val socket: DatagramSocket,
        internal val packet: IpPacket
    ) {
        private val time: Long

        init {
            this.time = System.currentTimeMillis()
        }

        internal fun ageSeconds(): Long {
            return (System.currentTimeMillis() - time) / 1000
        }
    }

    /**
     * Queue of WaitingOnSocketPacket, bound on time and space.
     */
    private class WospList : Iterable<WaitingOnSocketPacket> {
        private val list = LinkedList<WaitingOnSocketPacket>()

        internal fun add(wosp: WaitingOnSocketPacket) {
            if (list.size > DNS_MAXIMUM_WAITING) {
                Log.d(TAG, "Dropping socket due to space constraints: " + list.element().socket)
                list.element().socket.close()
                list.remove()
            }
            while (!list.isEmpty() && list.element().ageSeconds() > DNS_TIMEOUT_SEC) {
                Log.d(TAG, "Timeout on socket " + list.element().socket)
                list.element().socket.close()
                list.remove()
            }
            list.add(wosp)
        }

        override fun iterator(): MutableIterator<WaitingOnSocketPacket> {
            return list.iterator()
        }

        internal fun size(): Int {
            return list.size
        }

    }

    companion object {
        private val TAG = "AdVpnThread"
        private val MIN_RETRY_TIME = 5
        private val MAX_RETRY_TIME = 2 * 60
        /* If we had a successful connection for that long, reset retry timeout */
        private val RETRY_RESET_SEC: Long = 60
        /* Maximum number of responses we want to wait for */
        private val DNS_MAXIMUM_WAITING = 1024
        private val DNS_TIMEOUT_SEC: Long = 10

        @Throws(VpnNetworkException::class)
        private fun getDnsServers(context: Context): Set<InetAddress> {
            val out = HashSet<InetAddress>()

            data class DnsServer(val address: String)


            context.database.use {
                delete("in_use_dns_servers")
            }

            val cm =
                context.getSystemService(VpnService.CONNECTIVITY_SERVICE) as ConnectivityManager
            // Seriously, Android? Seriously?
            val activeInfo = cm.activeNetworkInfo /*?: throw VpnNetworkException(
                "No DNS Server"
            )*/ ?: run {
                Log.i(TAG, out.toString())

                /*
           If there's no internet connection, then we can't get the DNS servers that we need to add routes for.
           Therefore, we always use any cached dns servers we might have.
           When changing networks, we'll check if our cache is outdated and if the dns is already configured to use all the DNS's
           If not, we'll restart the vpn.
            */

                /*
                Get cached dns servers
                 */
                context.database.use {
                    select("cached_dns_servers", "address").exec {

                        Log.d(TAG, cursorToString())
                        val addresses = parseList(classParser<DnsServer>())

                        if (addresses.isNotEmpty()) {
                            addresses.forEach {
                                out.add(InetAddress.getByName(it.address))

                                context.database.use {
                                    insert("in_use_dns_servers",
                                        "address" to it.address)
                                }
                            }
                        } else {
                            out.add(InetAddress.getByName("8.8.8.8"))
                            out.add(InetAddress.getByName("8.8.4.4"))

                            context.database.use {
                                insert("in_use_dns_servers",
                                    "address" to "8.8.8.8")
                                insert("in_use_dns_servers",
                                    "address" to "8.8.4.4")
                            }
                        }
                    }
                }

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


/*
This is so we don't have to restart the vpn when switching networks, but it seems to duplicate a lot of dns requests and slow down loading..
 */
/*
                    //Also add the cached DNS servers
                    context.database.use {
                        select("cached_dns_servers", "address").exec {
                            val addresses = parseList(classParser<DnsServer>())
                            addresses.forEach {
                                //add and mark in use if we successfully added
                                if (out.add(InetAddress.getByName(it.address))) {
                                    context.database.use {
                                        insert("in_use_dns_servers",
                                            "address" to it.address)
                                    }
                                }
                            }
                        }
                    }*/


                    /*
                    Cache the DNS servers so we can use them the next time to start the vpn
                    */
                    context.database.use {
                        insert(
                            "cached_dns_servers",
                            "address" to address.hostAddress
                        )
                    }
                    context.database.use {
                        insert("in_use_dns_servers",
                            "address" to address.hostAddress)
                    }
                }
            }
            return out
        }
    }

}
