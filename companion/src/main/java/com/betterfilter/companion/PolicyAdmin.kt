package com.betterfilter.companion

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
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject


class PolicyAdmin : DeviceAdminReceiver(), AnkoLogger {

    companion object {
        val isAdminActiveObservable: Subject<Boolean> = BehaviorSubject.createDefault(isAdminActive(App.instance))

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, PolicyAdmin::class.java)
        }

        fun isAdminActive(context: Context): Boolean {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return devicePolicyManager.isAdminActive(getComponentName(context))
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        isAdminActiveObservable.onNext(false)

        super.onDisabled(context, intent)
    }

    override fun onEnabled(context: Context?, intent: Intent?) {
        isAdminActiveObservable.onNext(true)
        super.onEnabled(context, intent)
    }

}