package com.betterfilter

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getSystemService
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.betterfilter.Extensions.sha256
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.toast
import androidx.core.content.ContextCompat.getSystemService
import android.app.admin.DeviceAdminReceiver
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.text.InputType
import androidx.core.view.marginTop
import org.jetbrains.anko.support.v4.alert
import java.lang.Thread.sleep


class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, MySettingsFragment())
            .commit()
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment)
        fragment.arguments = args
        fragment.setTargetFragment(caller, 0)
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, fragment)
            .addToBackStack(null)
            .commit()
        return true
    }
}

class MySettingsFragment : PreferenceFragmentCompat(), AnkoLogger {

    val REQUEST_CODE_ADMIN = 1234
    var deviceAdmin: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        val categories: MultiSelectListPreference? = findPreference("categories")
        categories?.setOnPreferenceClickListener {
            updateStoredHostsURL()
            true
        }

        val changePassword: Preference? = findPreference("changePassword")

        changePassword?.setOnPreferenceClickListener {
/*            val view: View = layoutInflater.inflate(R.layout.change_password_dialog, null)
            val passwordEditText: EditText = view.find(R.id.passwordEditText)
            val confirmPasswordEditText: EditText = view.find(R.id.comfirmPasswordEditText)*/


            //use anko to build layout?

            requireContext().alert {
                customView {
                    lateinit var passwordEditText: EditText
                    lateinit var confirmPasswordEditText: EditText
                    verticalLayout {
                        passwordEditText = editText {
                            inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
                            hint = "Password"
                        }.lparams(width = matchParent) {
                            topMargin = dip(16)
                        }
                        confirmPasswordEditText = editText {
                            inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
                            hint = "Confirm Password"
                        }.lparams(width = matchParent) {
                            topMargin = dip(16)
                        }

                    }
                    yesButton {
                        val isValid =
                            if (passwordEditText.text.toString().isEmpty()) {
                                passwordEditText.error = "Password cannot be empty"
                                false
                            } else if (passwordEditText.text.toString() != confirmPasswordEditText.text.toString()) {
                                confirmPasswordEditText.error = "Passwords must match!"
                                false
                            } else true

                        if (isValid) {
                            val sharedPref = requireContext().getSharedPreferences(
                                "password",
                                Context.MODE_PRIVATE
                            )
                            with(sharedPref.edit()) {
                                putString(
                                    "password-sha256",
                                    passwordEditText.text.toString().sha256()
                                )
                                commit()
                            }
                            toast("Password updated")
                        }
                    }
                    noButton {  }
                }
            }.show()
/*
            val alertDialogBuilder = AlertDialog.Builder(requireContext())
            alertDialogBuilder.setView(view)
                .setPositiveButton("OK") { _, _ -> }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            val alertDialog = alertDialogBuilder.show()
            val positiveButton: Button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
            passwordEditText.requestFocus()
            alertDialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

            positiveButton.setOnClickListener {
                val isValid =
                    if (passwordEditText.text.toString().isEmpty()) {
                        passwordEditText.error = "Password cannot be empty"
                        false
                    } else if (passwordEditText.text.toString() != confirmPasswordEditText.text.toString()) {
                        confirmPasswordEditText.error = "Passwords must match!"
                        false
                    } else true

                if (isValid) {
                    val sharedPref = requireContext().getSharedPreferences("password", Context.MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putString("password-sha256", passwordEditText.text.toString().sha256())
                        commit()
                    }
                    toast("Password updated")

                    alertDialog.dismiss()
                }
            }*/

            true
        }

        val componentName = PolicyAdmin.getComponentName(requireContext())
        val devicePolicyManager =
            this.context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        deviceAdmin = findPreference("deviceAdmin")
        updateDeviceAdminSummary()

        deviceAdmin?.setOnPreferenceClickListener {

            if (devicePolicyManager.isAdminActive(componentName)) {

                requireContext().alert("This setting stops the filter from being uninstalled. Are you sure you want to disable this?") {
                    yesButton {
                        val dpm =
                            context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                        dpm.removeActiveAdmin(componentName)
                        //there's a delay to remove the active admin, so use a delay before updating
                        doAsync {
                            sleep(50)
                            uiThread {
                                updateDeviceAdminSummary()
                            }
                        }

                    }
                    noButton { }
                }.show()


            } else {

                val activateDeviceAdminIntent =
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)

                activateDeviceAdminIntent.putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    componentName
                )
                activateDeviceAdminIntent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Stop the filter from being uninstalled"
                )

                startActivityForResult(activateDeviceAdminIntent, REQUEST_CODE_ADMIN)
            }
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_ADMIN) {
            updateDeviceAdminSummary()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun updateStoredHostsURL() {

        val prefs = PreferenceManager.getDefaultSharedPreferences(this.context)
        val categories = prefs.getStringSet("categories", mutableSetOf()) ?: mutableSetOf()

        var url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts"

        if (categories.contains("gambling")){
            info("gambling is checked")
            url = url.replace("porn", "gambling-porn")
        }
        if (categories.contains("socialMedia")){
            info("social is checked")
            url = url.replace("porn", "porn-social")
        }

        with(prefs.edit()) {
            putString("hostsURL", url)
            apply()
        }
    }

    fun updateDeviceAdminSummary(){
        val componentName = PolicyAdmin.getComponentName(requireContext())
        val devicePolicyManager = this.context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        deviceAdmin?.summary =
            if (devicePolicyManager.isAdminActive(componentName)) "Enabled\nPrevents uninstallation"
            else "Disabled\nEnable to prevent uninstallation"
    }
}

class AdvancedFilterSettingsFragment: PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_filter_advanced, rootKey)
    }
}
