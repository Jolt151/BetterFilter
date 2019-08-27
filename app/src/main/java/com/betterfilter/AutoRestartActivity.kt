package com.betterfilter

import android.content.Intent
import android.net.VpnService
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.betterfilter.vpn.VpnHostsService

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
