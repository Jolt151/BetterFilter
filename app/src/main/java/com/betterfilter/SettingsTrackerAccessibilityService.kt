package com.betterfilter

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import android.content.pm.ActivityInfo
import android.content.ComponentName
import android.util.Log
import java.lang.Exception
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityManager
import io.reactivex.subjects.BehaviorSubject


class SettingsTrackerAccessibilityService: AccessibilityService(), AnkoLogger {

    companion object {
        val isActiveObservable = BehaviorSubject.createDefault(isAccessibilityServiceEnabled(App.instance))

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

            for (enabledService in enabledServices) {
                val enabledServiceInfo = enabledService.resolveInfo.serviceInfo
                if (enabledServiceInfo.packageName.equals(context.packageName) && enabledServiceInfo.name.equals(this::class.java.name)) return true
            }

            return false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        isActiveObservable.onNext(true)

        //Configure these here for compatibility with API 13 and below.
        val config = AccessibilityServiceInfo()
        config.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        config.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

        if (Build.VERSION.SDK_INT >= 16)
        //Just in case this helps
            config.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

        serviceInfo = config
    }
    override fun onInterrupt() {
        info("oninterrupt")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isActiveObservable.onNext(false)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        info("onaccessibilityevent: $event")
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.className != null) {
                val componentName = ComponentName(event.packageName.toString(), event.className.toString())

                val activityInfo = try {packageManager.getActivityInfo(componentName, 0)} catch (e: Exception) { null}
                if (activityInfo != null) {

                    info(componentName.flattenToShortString())
                    info(componentName)

                    //If we're in settings and we get to the page that will let us disable admin apps, or the page to disable the accessibility service,
                    //go to the app instead of letting the user disable our app.
                    if ((event.className == "com.android.settings.SubSettings") && ((event.text[0] == "Device admin apps") || event.text[0] == getString(R.string.accessibility_service_title) )
                        || event.className == "com.android.settings.Settings\$DeviceAdminSettingsActivity") {
                        if (!App.isAuthenticated) {
                            startActivity(Intent(this, PasswordActivity::class.java))
                        }
                    }
                }
            }
        }
    }

}