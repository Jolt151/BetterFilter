package com.betterfilter.vpn

import android.content.Intent
import android.net.VpnService
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.betterfilter.R

class AutoRestartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_restart)

        if (!intent.getBooleanExtra("isFromOurButton", false)) {
            VpnService.prepare(this)
            val intent = Intent(this, VpnHostsService::class.java)
            startService(intent)
        }
        finish()

    }
}
