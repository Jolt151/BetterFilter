package com.betterfilter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
    }
}
