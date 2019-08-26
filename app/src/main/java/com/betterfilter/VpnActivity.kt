package com.betterfilter

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import com.betterfilter.vpn.APIClient
import com.betterfilter.vpn.VpnHostsService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.anko.*
import java.io.File
import java.io.InputStream

class VpnActivity : AppCompatActivity(), AnkoLogger {

    val apiClient = APIClient(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_vpn)

        val stevenBlackCheckBox: CheckBox = find(R.id.stevenBlackHosts)
        val gamblingHostsCheckBox: CheckBox = find(R.id.gamblingHosts)
        val socialHostsCheckBox: CheckBox = find(R.id.socialHosts)

        val vpnButton: Button = find(R.id.vpn)
        vpnButton.setOnClickListener {

            var url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts"
            if (gamblingHostsCheckBox.isChecked) {
                info("gambling is checked")
                url = url.replace("porn", "gambling-porn")
            }
            if (socialHostsCheckBox.isChecked){
                info("social is checked")
                url = url.replace(Regex("porn"), "porn-social")
            }
            info("url: $url")

            apiClient.downloadNewHostsFile(url, completionHandler = {
                if (it == APIClient.Status.Success) {
                    val intent = VpnService.prepare(this@VpnActivity)
                    if (intent != null) startActivityForResult(intent, 1)
                    else onActivityResult(1, RESULT_OK, null)
                } else {
                    toast("Error downloading the hosts files!")
                }
            })


        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                val intent = Intent(this, VpnHostsService::class.java)
                startService(intent)
            }
        }

    }
}