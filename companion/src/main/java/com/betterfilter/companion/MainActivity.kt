package com.betterfilter.companion

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.jakewharton.rxbinding3.view.clicks
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.*

class MainActivity : AppCompatActivity() {

    private val disposables = CompositeDisposable()

    private val REQUEST_CODE_ADMIN = 100


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startService(Intent(this, VpnMonitorService::class.java))

        disposables.add(
            deviceAdminButton.clicks()
            .subscribe {

                val activateDeviceAdminIntent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)

                activateDeviceAdminIntent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, PolicyAdmin.getComponentName(this))
                activateDeviceAdminIntent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Stop the filter from being uninstalled")

                startActivityForResult(activateDeviceAdminIntent, REQUEST_CODE_ADMIN)
            })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_ADMIN) {
            //updateDeviceAdminSummary()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
