package com.betterfilter

import android.app.ProgressDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.betterfilter.Extensions.getAllHostsUrls
import com.betterfilter.Extensions.getCategoriesUrls
import com.betterfilter.vpn.VpnHostsService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.reactivex.disposables.Disposable
import org.jetbrains.anko.*
import org.w3c.dom.Text
import java.io.File


class MainActivity : AppCompatActivity(), AnkoLogger {

    val REQUEST_CODE_VPN = 102
    var downloadingProgressDialog: ProgressDialog? = null

    lateinit var filterStatus: TextView
    lateinit var isRunningDisposable: Disposable

    lateinit var deviceAdminStatus: TextView
    lateinit var deviceAdminStatusDisposable: Disposable

    lateinit var accessibilityServiceStatus: TextView
    lateinit var accessibilityServiceStatusDisposable: Disposable



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        filterStatus = find(R.id.filter_status)
        isRunningDisposable = VpnHostsService.isRunningObservable.subscribe {isRunning ->
            updateUI(isRunning)
        }

        deviceAdminStatus = find(R.id.device_admin_status)
        deviceAdminStatusDisposable = PolicyAdmin.isAdminActiveObservable.subscribe { isActive ->
            updateDeviceAdminStatus(isActive)
        }

        accessibilityServiceStatus = find(R.id.accessibility_service_status)
        accessibilityServiceStatusDisposable = SettingsTrackerAccessibilityService.isActiveObservable.subscribe { isActive ->
            updateAccessibilityServiceStatus(isActive)
        }


        val settingsButton: FloatingActionButton = find(R.id.settingsFAB)
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val fab: FloatingActionButton = find(R.id.startVpnFAB)
        fab.setOnClickListener {
            downloadingProgressDialog = indeterminateProgressDialog(message = "Downloading files", title = "Starting filter")

            val urls = defaultSharedPreferences.getAllHostsUrls()

            APIClient(this).downloadMultipleHostsFiles(urls, completionHandler = {
                if (it == APIClient.Status.Success) {
                    downloadingProgressDialog?.setMessage("Starting filter...")
                    val intent = VpnService.prepare(this)
                    if (intent != null) startActivityForResult(intent, REQUEST_CODE_VPN)
                    else onActivityResult(REQUEST_CODE_VPN, AppCompatActivity.RESULT_OK, null)
                } else {
                    downloadingProgressDialog?.dismiss()
                    val hostsFileExists = File(this.filesDir, "net_hosts").exists()
                    toast(
                        "Error downloading the hosts files!" + (if (hostsFileExists) {
                            val intent = VpnService.prepare(this)
                            if (intent != null) startActivityForResult(intent, REQUEST_CODE_VPN)
                            else onActivityResult(
                                REQUEST_CODE_VPN,
                                AppCompatActivity.RESULT_OK,
                                null
                            )
                            " Using the cached file..."
                        } else "")
                    )
                }
            })
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_VPN) {
            if (resultCode == AppCompatActivity.RESULT_OK) {
                val intent = Intent(this, VpnHostsService::class.java)
                startService(intent)
                downloadingProgressDialog?.dismiss()
            }
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDestroy() {
        isRunningDisposable.dispose()
        deviceAdminStatusDisposable.dispose()
        accessibilityServiceStatusDisposable.dispose()
        super.onDestroy()
    }

    fun getEmoji(unicode: Int): String {
        return String(Character.toChars(unicode))
    }

    fun updateUI(isRunning: Boolean) {
        if (isRunning) {
            filterStatus.setTextColor(Color.parseColor("#1bbf23"))
            filterStatus.text = "Filter is active"
            filterStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_green_24dp, 0, 0, 0)
        } else {
            filterStatus.setTextColor(Color.parseColor("#cf2913"))
            filterStatus.text = "Filter is inactive"
            filterStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_close_red_24dp, 0, 0, 0)

        }
    }

    fun updateDeviceAdminStatus(isActive: Boolean) {
        if (isActive) {
            deviceAdminStatus.setTextColor(Color.parseColor("#1bbf23"))
            deviceAdminStatus.text = "Device administrator is enabled"
            deviceAdminStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_green_24dp, 0, 0, 0)
        } else {
            deviceAdminStatus.setTextColor(Color.parseColor("#cf2913"))
            deviceAdminStatus.text = "Device administrator is disabled"
            deviceAdminStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_close_red_24dp, 0, 0, 0)
        }
    }

    fun updateAccessibilityServiceStatus(isActive: Boolean) {
        if (isActive) {
            accessibilityServiceStatus.setTextColor(Color.parseColor("#1bbf23"))
            accessibilityServiceStatus.text = "Accessibility service is enabled"
            accessibilityServiceStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_green_24dp, 0, 0, 0)
        } else {
            accessibilityServiceStatus.setTextColor(Color.parseColor("#cf2913"))
            accessibilityServiceStatus.text = "Accessibility service is disabled"
            accessibilityServiceStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_close_red_24dp, 0, 0, 0)
        }
    }
}
