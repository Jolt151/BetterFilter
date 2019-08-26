package com.betterfilter

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat.getSystemService
import android.app.admin.DevicePolicyManager
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import androidx.core.content.ContextCompat.getSystemService
import android.app.ActivityManager




class PolicyAdmin : DeviceAdminReceiver(), AnkoLogger {

    override fun onDisabled(context: Context, intent: Intent) {
        // Called when the app is about to be deactivated as a device administrator.
        // Deletes previously stored password policy.
        super.onDisabled(context, intent)
        context.getSharedPreferences("default", Activity.MODE_PRIVATE).edit().apply {
            clear()
            apply()
        }
    }

    override fun onEnabled(context: Context?, intent: Intent?) {
        super.onEnabled(context, intent)
    }

    fun getComponentName(context: Context): ComponentName {
        return ComponentName(context.applicationContext, PolicyAdmin::class.java)
    }

    override fun onDisableRequested(context: Context?, intent: Intent?): CharSequence {
        val deviceManger = context?.getSystemService(
            Context.DEVICE_POLICY_SERVICE
        ) as DevicePolicyManager
        info("isadmin: ${deviceManger.isAdminActive(getComponentName(context))}")
        deviceManger.lockNow()

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses("com.android.settings")
        context.startActivity(Intent(context, MainActivity::class.java))
        info("up to here")
        return "Your warning"
    }

}