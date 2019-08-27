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


class MainActivity : AppCompatActivity(), AnkoLogger {

    lateinit var adminActivityButton: Button

    lateinit var devicePolicyManager: DevicePolicyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adminActivityButton = find(R.id.adminActivityButton)

        adminActivityButton.setOnClickListener {
            startActivity(Intent(this, AdminConsoleActivity::class.java))
        }

        val becomeDeviceAdminButton: Button = find(R.id.becomeDeviceAdminButton)
        becomeDeviceAdminButton.setOnClickListener {
            val componentName = ComponentName(this, PolicyAdmin::class.java)

            devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (!devicePolicyManager.isAdminActive(componentName)) {
                val activateDeviceAdminIntent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)

                activateDeviceAdminIntent.putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(this, PolicyAdmin::class.java)
                )

                // It is good practice to include the optional explanation text to
                // explain to user why the application is requesting to be a device
                // administrator. The system will display this message on the activation
                // screen.
                activateDeviceAdminIntent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Stop the filter from being uninstalled")

                startActivityForResult(activateDeviceAdminIntent, 1234)
            } else {
                toast("Already a device admin!")
            }
        }
        
        val vpnActivityButton: Button = find(R.id.vpnActivityButton)
        vpnActivityButton.setOnClickListener { 
            startActivity(Intent(this, VpnActivity::class.java))
        }

        val startVpnButton: Button = find(R.id.startVpn)
        startVpnButton.setOnClickListener {
            var url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts"

            APIClient(this).downloadNewHostsFile(url, completionHandler = {
                if (it == APIClient.Status.Success) {
                    val intent = VpnService.prepare(this)
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
