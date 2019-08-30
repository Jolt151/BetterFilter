package com.betterfilter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.betterfilter.vpn.VpnHostsService
import org.jetbrains.anko.*
import java.io.File


class MainActivity : AppCompatActivity(), AnkoLogger {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val settingsButton: Button = find(R.id.settingsButton)
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val startVpnButton: Button = find(R.id.startVpn)
        startVpnButton.setOnClickListener {


            APIClient(this).downloadMultipleHostsFiles(listOf("https://raw.githubusercontent.com/FadeMind/hosts.extras/master/add.Spam/hosts"), completionHandler = {
                if (it == APIClient.Status.Success) {
                    val intent = VpnService.prepare(this)
                    if (intent != null) startActivityForResult(intent, 1)
                    else onActivityResult(1, RESULT_OK, null)
                } else {
                    toast("error")

                    /*val hostsFileExists = File(filesDir, "net_hosts").exists()
                    toast("Error downloading the hosts files!" + (if (hostsFileExists) {
                        val intent = VpnService.prepare(this)
                        if (intent != null) startActivityForResult(intent, 1)
                        else onActivityResult(1, RESULT_OK, null)
                        " Using the cached file..."
                    } else ""))*/
                }
            })


            /*            var url = getSharedPreferences("hosts", Context.MODE_PRIVATE).getString("hostsURL", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts")

            APIClient(this).downloadNewHostsFile(url, completionHandler = {
                if (it == APIClient.Status.Success) {
                    val intent = VpnService.prepare(this)
                    if (intent != null) startActivityForResult(intent, 1)
                    else onActivityResult(1, RESULT_OK, null)
                } else {
                    val hostsFileExists = File(filesDir, "net_hosts").exists()
                    toast("Error downloading the hosts files!" + (if (hostsFileExists) {
                        val intent = VpnService.prepare(this)
                        if (intent != null) startActivityForResult(intent, 1)
                        else onActivityResult(1, RESULT_OK, null)
                        " Using the cached file..."
                    } else ""))
                }
            })*/
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                val intent = Intent(this, VpnHostsService::class.java)
                startService(intent)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
