package com.betterfilter.vpn

import android.net.VpnService
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import org.jetbrains.anko.find
import android.content.Intent
import android.app.Activity
import com.betterfilter.R
import org.jak_linux.dns66.vpn.AdVpnService


class VpnActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vpn)

        val connectButton: Button = find(R.id.connectVpnButton)
        connectButton.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) startActivityForResult(intent, 1)
            else onActivityResult(1, RESULT_OK, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            val intent = Intent(this, AdVpnService::class.java)
            startService(intent)
        }
    }
}
