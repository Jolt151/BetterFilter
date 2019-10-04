package com.betterfilter

import android.app.ProgressDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.widget.TextView
import com.betterfilter.Extensions.getAllHostsUrls
import com.betterfilter.Extensions.startVpn
import com.betterfilter.vpn.AdVpnService
import com.betterfilter.vpn.VpnStatus
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jakewharton.rxbinding3.view.clicks
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.withLatestFrom
import org.jetbrains.anko.*
import java.io.File
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity(), AnkoLogger {

    val REQUEST_CODE_VPN = 102
    var downloadingProgressDialog: ProgressDialog? = null

    val subscriptions = CompositeDisposable()

    lateinit var filterStatus: TextView
    lateinit var deviceAdminStatus: TextView
    lateinit var accessibilityServiceStatus: TextView



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        filterStatus = find(R.id.filter_status)
/*        isRunningDisposable = VpnHostsService.isRunningObservable.subscribe {isRunning ->
            updateUI(isRunning)
        }*/
        subscriptions.add(AdVpnService.isRunningObservable.subscribe { isRunning ->
            updateUI(isRunning)
        })

        deviceAdminStatus = find(R.id.device_admin_status)
        subscriptions.add(PolicyAdmin.isAdminActiveObservable.subscribe { isActive ->
            updateDeviceAdminStatus(isActive)
        })

        accessibilityServiceStatus = find(R.id.accessibility_service_status)
        subscriptions.add(SettingsTrackerAccessibilityService.isActiveObservable.subscribe { isActive ->
            updateAccessibilityServiceStatus(isActive)
        })


        val settingsButton: FloatingActionButton = find(R.id.settingsFAB)
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val fab: FloatingActionButton = find(R.id.startVpnFAB)

        subscriptions.add(fab.clicks()
            .debounce(500, TimeUnit.MILLISECONDS)
            .withLatestFrom(AdVpnService.isRunningObservable)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                val status = it.second

                if (status == VpnStatus.STARTING || status == VpnStatus.RUNNING) return@subscribe

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
            })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_VPN) {
            if (resultCode == AppCompatActivity.RESULT_OK) {
                //val intent = Intent(this, VpnHostsService::class.java)
                this.startVpn()
                downloadingProgressDialog?.dismiss()
            }
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDestroy() {
/*        isRunningDisposable.dispose()
        deviceAdminStatusDisposable.dispose()
        accessibilityServiceStatusDisposable.dispose()*/
        subscriptions.clear()
        super.onDestroy()
    }

    fun getEmoji(unicode: Int): String {
        return String(Character.toChars(unicode))
    }


    fun updateUI(status: VpnStatus) {
        when (status) {
            VpnStatus.STARTING -> {
                //We can display a loading message as the status
            }
            VpnStatus.RUNNING -> {
                filterStatus.setTextColor(Color.parseColor("#1bbf23"))
                filterStatus.text = "Filter is active"
                filterStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_green_24dp, 0, 0, 0)
            }
            VpnStatus.STOPPING -> {
                //Lets display a stopping message
            }
            VpnStatus.STOPPED -> {
                filterStatus.setTextColor(Color.parseColor("#cf2913"))
                filterStatus.text = "Filter is inactive"
                filterStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_close_red_24dp, 0, 0, 0)
            }
            VpnStatus.RECONNECTING -> {
                //display a reconnecting message
            }
        }
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
