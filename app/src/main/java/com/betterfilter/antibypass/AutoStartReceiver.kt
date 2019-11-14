package com.betterfilter.antibypass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.core.content.ContextCompat.startActivity
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info

class AutoStartReceiver : BroadcastReceiver(), AnkoLogger {
    override fun onReceive(context: Context, intent: Intent) {

        info("booted up!")
        info("starting vpn...")

        startActivity(context, Intent(context, AutoRestartActivity::class.java).addFlags(FLAG_ACTIVITY_NEW_TASK), null)
    }

}