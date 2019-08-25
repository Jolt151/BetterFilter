package com.betterfilter

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class PolicyAdmin : DeviceAdminReceiver() {

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
}