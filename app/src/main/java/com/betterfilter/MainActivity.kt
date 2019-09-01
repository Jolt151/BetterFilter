package com.betterfilter

import android.annotation.SuppressLint
import android.app.ProgressDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.betterfilter.vpn.VpnHostsService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.reactivex.disposables.Disposable
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import org.jetbrains.anko.support.v4.indeterminateProgressDialog
import org.jetbrains.anko.support.v4.toast
import org.w3c.dom.Text
import java.io.File


class MainActivity : AppCompatActivity(), AnkoLogger {

    val REQUEST_CODE_VPN = 102
    var downloadingProgressDialog: ProgressDialog? = null

    lateinit var textView7: TextView
    lateinit var isRunningDisposable: Disposable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView7 = find(R.id.textView7)
        isRunningDisposable = VpnHostsService.isRunningObservable.subscribe {isRunning ->
            updateUI(isRunning)
        }


        val settingsButton: Button = find(R.id.settingsButton)
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val fab: FloatingActionButton = find(R.id.floatingActionButton)
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
            textView7.setTextColor(Color.parseColor("#1bbf23"))
            textView7.text = "Status: Filter is active"
            textView7.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_green_24dp, 0, 0, 0)
        } else {
            textView7.setTextColor(Color.parseColor("#cf2913"))
            textView7.text = "Status: Filter is inactive"
            textView7.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_close_red_24dp, 0, 0, 0)

        }
    }
}
