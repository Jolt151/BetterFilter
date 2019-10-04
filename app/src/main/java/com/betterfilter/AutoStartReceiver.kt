package com.betterfilter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.VpnService
import android.os.Build
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
        info("starting vpn...")

        startActivity(context, Intent(context, AutoRestartActivity::class.java).addFlags(FLAG_ACTIVITY_NEW_TASK), null)
    }

}