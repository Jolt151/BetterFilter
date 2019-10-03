package com.betterfilter

import android.content.Intent
import android.net.VpnService
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.betterfilter.Extensions.startVpn
import com.betterfilter.vpn.VpnHostsService
import com.betterfilter.vpn.vpn.AdVpnService
import com.betterfilter.vpn.vpn.Command

class AutoRestartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_restart)

        if (!intent.getBooleanExtra("isFromOurButton", false)) {
            this.startVpn()
        }
        finish()

    }
}
