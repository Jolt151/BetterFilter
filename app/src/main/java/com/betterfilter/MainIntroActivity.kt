package com.betterfilter

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.betterfilter.Extensions.sha256
import com.github.paolorotolo.appintro.AppIntro
import com.github.paolorotolo.appintro.ISlidePolicy
import org.jetbrains.anko.support.v4.find
import org.jetbrains.anko.support.v4.toast
import android.R.attr.name
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.core.view.accessibility.AccessibilityManagerCompat.getEnabledAccessibilityServiceList
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityService
import android.provider.Settings
import org.jetbrains.anko.support.v4.startActivityForResult


class MainIntroActivity : AppIntro() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addSlide(WelcomeFragment())
        addSlide(SetDeviceAdminFragment())
        addSlide(EnableAcessibilityServiceFragment())

    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)

        val prefs = this.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putBoolean("firstTimeInitCompleted", true)
            apply()
        }
        startActivity(Intent(this, MainActivity::class.java))
    }
}

class WelcomeFragment: Fragment(), ISlidePolicy {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View? {

        return layoutInflater.inflate(R.layout.intro_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val passwordField: EditText = find(R.id.setPasswordEditText)

        val setPasswordButton: Button = find(R.id.lockButton)
        setPasswordButton.setOnClickListener {
            if (passwordField.text.toString().isBlank()) {
                passwordField.error = "Password cannot be empty"
                return@setOnClickListener
            }

            val sharedPref = this.context?.getSharedPreferences("password", Context.MODE_PRIVATE) ?: return@setOnClickListener
            with(sharedPref.edit()) {
                putString("password-sha256", passwordField.text.toString().sha256())
                commit()
            }
            toast("Password updated")

        }
    }

    override fun isPolicyRespected(): Boolean {
        val hasPassword = this.context?.getSharedPreferences("password", Context.MODE_PRIVATE)?.getString("password-sha256", null) != null
        return hasPassword
    }
    override fun onUserIllegallyRequestedNextPage() {
        toast("You need to set a password before continuing.")
    }

}

class SetDeviceAdminFragment: Fragment() {

    lateinit var setDeviceAdminButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return layoutInflater.inflate(R.layout.intro_become_admin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setDeviceAdminButton = find(R.id.setDeviceAdminButton)

        val componentName = ComponentName(this.context, PolicyAdmin::class.java)
        val devicePolicyManager = this.context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (devicePolicyManager.isAdminActive(componentName)) {
            setDeviceAdminButton.text = "Enabled"
            setDeviceAdminButton.isEnabled = false
        }

        setDeviceAdminButton.setOnClickListener {
            if (!devicePolicyManager.isAdminActive(componentName)) {
                val activateDeviceAdminIntent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)

                activateDeviceAdminIntent.putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(this.context, PolicyAdmin::class.java)
                )

                // It is good practice to include the optional explanation text to
                // explain to user why the application is requesting to be a device
                // administrator. The system will display this message on the activation
                // screen.
                activateDeviceAdminIntent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Stop the filter from being uninstalled")

                startActivityForResult(activateDeviceAdminIntent, 1234)
            } else {
                toast("Already enabled!")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1234) {
            val componentName = ComponentName(this.context, PolicyAdmin::class.java)
            val devicePolicyManager = this.context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (devicePolicyManager.isAdminActive(componentName)) {
                setDeviceAdminButton.text = "Enabled"
                setDeviceAdminButton.isEnabled = false
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}

class EnableAcessibilityServiceFragment: Fragment() {

    lateinit var enableServiceButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return layoutInflater.inflate(R.layout.intro_accessibility, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        enableServiceButton = find(R.id.enableAccessibilityServiceButton)
        val isAccessibilityEnabled = isAccessibilityServiceEnabled(this.context!!, SettingsTrackerAccessibilityService::class.java)

        if (isAccessibilityEnabled) {
            enableServiceButton.setText("Enabled")
            enableServiceButton.isEnabled = false
        }

        enableServiceButton.setOnClickListener {
            startActivityForResult(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), 1234)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1234) {
            val isAccessibilityEnabled = isAccessibilityServiceEnabled(this.context!!, SettingsTrackerAccessibilityService::class.java)

            if (isAccessibilityEnabled) {
                enableServiceButton.setText("Enabled")
                enableServiceButton.isEnabled = false
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices =
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        for (enabledService in enabledServices) {
            val enabledServiceInfo = enabledService.resolveInfo.serviceInfo
            if (enabledServiceInfo.packageName.equals(context.packageName) && enabledServiceInfo.name.equals(service.name)) return true
        }

        return false
    }
}