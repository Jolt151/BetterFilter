package com.betterfilter

import android.app.ProgressDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.betterfilter.vpn.VpnHostsService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.reactivex.disposables.Disposable
import org.jetbrains.anko.*
import java.io.File


class MainActivity : AppCompatActivity(), AnkoLogger {

    val REQUEST_CODE_VPN = 102
    var downloadingProgressDialog: ProgressDialog? = null

    lateinit var filterStatus: TextView
    lateinit var isRunningDisposable: Disposable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        filterStatus = find(R.id.filter_status)
        isRunningDisposable = VpnHostsService.isRunningObservable.subscribe {isRunning ->
            updateUI(isRunning)
        }


        val settingsButton: FloatingActionButton = find(R.id.settingsFAB)
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val fab: FloatingActionButton = find(R.id.startVpnFAB)
        fab.setOnClickListener {
            downloadingProgressDialog = indeterminateProgressDialog(message = "Downloading files", title = "Starting filter")

            val mainUrl = PreferenceManager.getDefaultSharedPreferences(this).getString(
                "hostsURL",
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts"
            )
            val additionalUrls = defaultSharedPreferences.getStringSet("hosts-urls", mutableSetOf())
            val urls = ArrayList<String>(additionalUrls)
            urls.add(mainUrl)
            APIClient(this).downloadMultipleHostsFiles(urls, completionHandler = {
                if (it == APIClient.Status.Success) {
                    downloadingProgressDialog?.setMessage("Starting filter...")
                    val intent = VpnService.prepare(this)
                    if (intent != null) startActivityForResult(intent, 1)
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
        super.onDestroy()
    }

    fun getEmoji(unicode: Int): String {
        return String(Character.toChars(unicode))
    }

    fun updateUI(isRunning: Boolean) {
        if (isRunning) {
            filterStatus.setTextColor(Color.parseColor("#1bbf23"))
            filterStatus.text = "Status: Filter is active"
            filterStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_green_24dp, 0, 0, 0)
        } else {
            filterStatus.setTextColor(Color.parseColor("#cf2913"))
            filterStatus.text = "Status: Filter is inactive"
            filterStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_close_red_24dp, 0, 0, 0)

        }
    }
}
