package com.betterfilter.ui

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
import com.betterfilter.extensions.sha256
import com.github.paolorotolo.appintro.AppIntro
import com.github.paolorotolo.appintro.ISlidePolicy
import org.jetbrains.anko.support.v4.find
import org.jetbrains.anko.support.v4.toast
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityService
import android.provider.Settings
import android.widget.RadioButton
import org.jetbrains.anko.find
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import androidx.core.app.ActivityCompat
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import com.betterfilter.Constants
import com.betterfilter.R
import com.betterfilter.antibypass.PolicyAdmin
import com.betterfilter.antibypass.SettingsTrackerAccessibilityService
import org.jetbrains.anko.defaultSharedPreferences


class MainIntroActivity : AppIntro() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addSlide(WelcomeFragment())
        addSlide(SetFilterLevelFragment())
        addSlide(SetDeviceAdminFragment())
        addSlide(EnableAcessibilityServiceFragment())

        showSkipButton(false)
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)

        with(defaultSharedPreferences.edit()) {
            putBoolean("firstTimeInitCompleted", true)
            apply()
        }
        //Go to main activity and remove the intro from the backstack
        //https://stackoverflow.com/questions/14112219/android-remove-activity-from-back-stack/57079661
        val nextScreen = Intent(this, MainActivity::class.java)
        nextScreen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(nextScreen)
        ActivityCompat.finishAffinity(this)
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
        val confirmPasswordEditText: EditText = find(R.id.confirmPasswordEditText)

        val setPasswordButton: Button = find(R.id.lockButton)
        setPasswordButton.setOnClickListener {
            val isValid =
                if (passwordField.text.toString().isEmpty()) {
                    passwordField.error = "Password cannot be empty"
                    false
                } else if (passwordField.text.toString() != confirmPasswordEditText.text.toString()) {
                    confirmPasswordEditText.error = "Passwords must match!"
                    false
                } else true

            if (isValid) {
                val sharedPref =
                    requireContext().getSharedPreferences(Constants.Prefs.PASSWORD_FILE, Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putString(Constants.Prefs.PASSWORD, passwordField.text.toString().sha256())
                    commit()
                }
                toast("Password updated")
            }
        }
    }

    override fun isPolicyRespected(): Boolean {
        val hasPassword = this.context?.getSharedPreferences(Constants.Prefs.PASSWORD_FILE, Context.MODE_PRIVATE)?.getString(
            Constants.Prefs.PASSWORD, null) != null
        return hasPassword
    }
    override fun onUserIllegallyRequestedNextPage() {
        toast("You need to set a password before continuing.")
    }

}

class SetFilterLevelFragment: Fragment(), ISlidePolicy {

    lateinit var adultRadioButton: RadioButton
    lateinit var familyRadioButton: RadioButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return layoutInflater.inflate(R.layout.intro_filter_level, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adultRadioButton = view.find(R.id.radioButton)
        familyRadioButton = view.find(R.id.radioButton2)
    }

    override fun isPolicyRespected(): Boolean {

        if (adultRadioButton.isChecked) {
            with (defaultSharedPreferences.edit()) {
                putString("cleanBrowsingLevel", "adult")
                apply()
            }
            return true
        } else if (familyRadioButton.isChecked) {
            with (defaultSharedPreferences.edit()) {
                putString("cleanBrowsingLevel", "family")
                apply()
            }
            return true
        }
        return false
    }

    override fun onUserIllegallyRequestedNextPage() { }
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