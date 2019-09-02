package com.betterfilter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ProgressDialog
import android.app.admin.DevicePolicyManager
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
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.betterfilter.Extensions.sha256
import org.jetbrains.anko.*
import android.net.VpnService
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.betterfilter.Extensions.getAllHostsUrls
import com.betterfilter.vpn.VpnHostsService
import org.jetbrains.anko.support.v4.*
import java.io.File
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

    val REQUEST_CODE_ADMIN = 100
    val REQUEST_CODE_ACCESSIBILITY = 101
    val REQUEST_CODE_VPN = 102
    var deviceAdmin: Preference? = null
    var accessibilityServiceButton: Preference? = null

    var downloadingProgressDialog: ProgressDialog? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        val startVpn: Preference? = findPreference("startVpn")
        startVpn?.setOnPreferenceClickListener {
           downloadingProgressDialog = indeterminateProgressDialog(message = "Downloading files", title = "Starting filter")

            val urls = defaultSharedPreferences.getAllHostsUrls()

            APIClient(requireContext()).downloadMultipleHostsFiles(urls, completionHandler = {
                if (it == APIClient.Status.Success) {
                    downloadingProgressDialog?.setMessage("Starting filter...")
                    val intent = VpnService.prepare(requireContext())
                    if (intent != null) startActivityForResult(intent, 1)
                    else onActivityResult(REQUEST_CODE_VPN, AppCompatActivity.RESULT_OK, null)
                } else {
                    downloadingProgressDialog?.dismiss()
                    val hostsFileExists = File(requireContext().filesDir, "net_hosts").exists()
                    toast("Error downloading the hosts files!" + (if (hostsFileExists) {
                        val intent = VpnService.prepare(requireContext())
                        if (intent != null) startActivityForResult(intent, REQUEST_CODE_VPN)
                        else onActivityResult(REQUEST_CODE_VPN, AppCompatActivity.RESULT_OK, null)
                        " Using the cached file..."
                    } else ""))
                }
            })

            true
        }

        val stopVpn: Preference? = findPreference("stopVpn")
        stopVpn?.setOnPreferenceClickListener {
            LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent("stop_vpn").putExtra("isFromOurButton", true))
            true
        }

        val categories: MultiSelectListPreference? = findPreference("categories")
        categories?.setOnPreferenceClickListener {
            true
        }

        val changePassword: Preference? = findPreference("changePassword")

        changePassword?.setOnPreferenceClickListener {
            val view: View = layoutInflater.inflate(R.layout.change_password_dialog, null)
            val passwordEditText: EditText = view.find(R.id.passwordEditText)
            val confirmPasswordEditText: EditText = view.find(R.id.comfirmPasswordEditText)


            //use anko to build layout?
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
            }

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

        accessibilityServiceButton = findPreference("accessibilityService")
        updateAccessibilityServiceSummary()
        accessibilityServiceButton?.setOnPreferenceClickListener {
            startActivityForResult(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), REQUEST_CODE_ACCESSIBILITY)
            updateAccessibilityServiceSummary()
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_ADMIN) {
            updateDeviceAdminSummary()
        } else if (requestCode == REQUEST_CODE_ACCESSIBILITY) {
            updateAccessibilityServiceSummary()
        } else if (requestCode == REQUEST_CODE_VPN) {
            if (resultCode == AppCompatActivity.RESULT_OK) {
                val intent = Intent(requireContext(), VpnHostsService::class.java)
                requireContext().startService(intent)
                downloadingProgressDialog?.dismiss()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun updateDeviceAdminSummary(){
        val componentName = PolicyAdmin.getComponentName(requireContext())
        val devicePolicyManager = this.context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        deviceAdmin?.summary =
            if (devicePolicyManager.isAdminActive(componentName)) "Enabled\nPrevents uninstallation"
            else "Disabled\nEnable to prevent uninstallation"
    }

    fun updateAccessibilityServiceSummary() {
        val isAccessibilityEnabled = isAccessibilityServiceEnabled(requireContext(), SettingsTrackerAccessibilityService::class.java)
        if (isAccessibilityEnabled) accessibilityServiceButton?.summary = "Enabled\nFurther prevents disabling the filter"
        else accessibilityServiceButton?.summary = "Disabled\nEnable to further prevent disabling the filter"
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

class AdvancedFilterSettingsFragment: PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_filter_advanced, rootKey)

        val customHosts: Preference? = findPreference("customHosts")
        customHosts?.setOnPreferenceClickListener {
            startActivity(Intent(activity, ChooseHostsSourcesActivity::class.java))
            true
        }

        val blacklist: Preference? = findPreference("blacklist")
        blacklist?.setOnPreferenceClickListener {
            startActivity(Intent(activity, BlacklistActivity::class.java))
            true
        }

        val whitelist: Preference? = findPreference("whitelist")
        whitelist?.setOnPreferenceClickListener {
            startActivity(Intent(activity, WhitelistActivity:: class.java))
            true
        }
    }
}
