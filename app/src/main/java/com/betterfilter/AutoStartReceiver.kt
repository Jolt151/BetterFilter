package com.betterfilter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat.startActivity
import com.betterfilter.vpn.VpnHostsService
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.warn
import java.io.File

class AutoStartReceiver : BroadcastReceiver(), AnkoLogger {
    override fun onReceive(context: Context, intent: Intent) {

        info("booted up!")

        if (File(context.filesDir, "net_hosts").exists()) {
            info("starting vpn from startup...")
            context.startActivity(Intent(context, AutoRestartActivity::class.java))

        } else {
            warn("no hosts file found, not starting vpn from startup")
        }

    }

}