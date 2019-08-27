package com.betterfilter

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.betterfilter.PasswordActivity.Companion.RESULT_AUTHENTICATED
import com.betterfilter.PasswordActivity.Companion.RESULT_UNAUTHENTICATED
import com.betterfilter.vpn.VpnHostsService
import com.jakewharton.rxbinding3.widget.checkedChanges
import org.jetbrains.anko.*

class VpnActivity : AppCompatActivity(), AnkoLogger {

    val apiClient = APIClient(this)

    val REQUEST_CODE_LOGIN = 100

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_vpn)

        if (!App.isAuthenticated) startActivityForResult(Intent(this, PasswordActivity::class.java), REQUEST_CODE_LOGIN)

        val stevenBlackCheckBox: CheckBox = find(R.id.stevenBlackHosts)
        val gamblingHostsCheckBox: CheckBox = find(R.id.gamblingHosts)
        val socialHostsCheckBox: CheckBox = find(R.id.socialHosts)

        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        gamblingHostsCheckBox.isChecked = prefs.getBoolean("gambling", false)
        socialHostsCheckBox.isChecked = prefs.getBoolean("social", false)

        gamblingHostsCheckBox.checkedChanges()
            .subscribe {
                with(prefs.edit()) {
                    putBoolean("gambling", it)
                    commit()
                }
                updateStoredHostsURL()
            }

        socialHostsCheckBox.checkedChanges()
            .subscribe {
                with(prefs.edit()) {
                    putBoolean("social", it)
                    commit()
                }
                updateStoredHostsURL()
            }

        val vpnButton: Button = find(R.id.vpn)
        vpnButton.setOnClickListener {

            val url = prefs.getString("hostsURL", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts")
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


        val stopVpnButton: Button = find(R.id.stopVpn)
        stopVpnButton.setOnClickListener {
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("stop_vpn").putExtra("isFromOurButton", true))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                val intent = Intent(this, VpnHostsService::class.java)
                startService(intent)
            }
        }
        else if (requestCode == REQUEST_CODE_LOGIN) {
            if (resultCode == RESULT_AUTHENTICATED) {
                //we're good.
            } else if (resultCode == RESULT_UNAUTHENTICATED) {
                //not authenticated, close the activity
                finish()
            }
        }

    }

    fun updateStoredHostsURL() {

        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)

        var url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts"

        if (prefs.getBoolean("gambling", false)) {
            info("gambling is checked")
            url = url.replace("porn", "gambling-porn")
        }
        if (prefs.getBoolean("social", false)){
            info("social is checked")
            url = url.replace(Regex("porn"), "porn-social")
        }

        with(prefs.edit()) {
            putString("hostsURL", url)
            commit()
        }
    }
}