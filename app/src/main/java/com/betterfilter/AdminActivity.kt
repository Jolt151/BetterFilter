package com.betterfilter

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN
import android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME
import android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME
import android.os.Build
import android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE
import android.app.Activity
import android.app.PendingIntent.getActivity


class AdminActivity : AppCompatActivity() {

    lateinit var devicePolicyManager: DevicePolicyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

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
//            activateDeviceAdminIntent.putExtra(
//                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
//                resources.getString(R.string.device_admin_activation_message)
//            )

            startActivityForResult(activateDeviceAdminIntent, 1234)
        } else {
            //devicePolicyManager.setAlwaysOnVpnPackage(componentName, "org.blokada.alarm", true)
            //provisionManagedProfile()
        }
    }

    /**
     * Initiates the managed profile provisioning. If we already have a managed profile set up on
     * this device, we will get an error dialog in the following provisioning phase.
     */
    private fun provisionManagedProfile() {
        val intent = Intent(ACTION_PROVISION_MANAGED_PROFILE)
        if (Build.VERSION.SDK_INT >= 24) {
            intent.putExtra(
                EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                PolicyAdmin().getComponentName(this)
            )
        } else {

            intent.putExtra(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME,
                applicationContext.packageName
            )
            intent.putExtra(EXTRA_DEVICE_ADMIN, PolicyAdmin().getComponentName(this))
        }
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, 12345)
            finish()
        } else {
            Toast.makeText(
                this, "Device provisioning is not enabled. Stopping.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
