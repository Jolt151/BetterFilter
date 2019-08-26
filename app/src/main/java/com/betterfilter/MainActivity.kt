package com.betterfilter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.ProgressBar
import com.topjohnwu.superuser.Shell
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.Appcompat
import xyz.hexene.localvpn.LocalVPN


class MainActivity : AppCompatActivity(), AnkoLogger {

    lateinit var downloadHostsButton: Button
    lateinit var downloadingProgressBar: ProgressBar

    lateinit var adminActivityButton: Button

    lateinit var devicePolicyManager: DevicePolicyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adminActivityButton = find(R.id.adminActivityButton)

        downloadHostsButton = find(R.id.downloadHosts)
        downloadingProgressBar = find(R.id.downloadingProgressBar)

        downloadHostsButton.setOnClickListener {
            info("clicked")

            downloadingProgressBar.visibility = View.VISIBLE

            doAsync(exceptionHandler = {
                error(it)
            }) {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts")
                    .build()
                val response = client.newCall(request).execute()
                val inputStream: InputStream = response.body?.byteStream() ?: throw Exception("null body")

                val file = File(cacheDir, "hosts")
                file.delete()
                info(file.absolutePath)

                var data = ByteArray(1024)
                while (inputStream.read(data) != -1) {
                    file.appendBytes(data)
                    data = ByteArray(1024)
                }

                uiThread {
                    downloadingProgressBar.visibility = View.GONE


                    alert(Appcompat, "Install hosts file? ") {
                        yesButton {
                            doAsync {
                                val dataDir = filesDir.absolutePath
                                info(dataDir)
                                val backupHostsCommand = "cp /system/etc/hosts $dataDir/hosts.bak"
                                val commandToInstallHosts = "cp ${file.absoluteFile} /system/etc/hosts"

                                val backupResult: Shell.Result = Shell.su(backupHostsCommand).exec()
                                info("result: ${backupResult.code}")

                                uiThread { toast("Backed up. Installing new hosts file...")}
                                info(file.absoluteFile)

                                Shell.su("mount -o rw,remount /system").exec()
                                val installResult: Shell.Result = Shell.su(commandToInstallHosts).exec()
                                Shell.su("mount -o ro,remount /system")
                                info(installResult.out)
                                info("result: ${installResult.code}")

                                uiThread { toast("Installed")}


                            }
                        }
                        noButton {

                        }
                    }.show()
                }
            }
        }

        adminActivityButton.setOnClickListener {
            startActivity(Intent(this, AdminConsoleActivity::class.java))
        }

        val becomeDeviceAdminButton: Button = find(R.id.becomeDeviceAdminButton)
        becomeDeviceAdminButton.setOnClickListener {
            val componentName = ComponentName(this, PolicyAdmin::class.java)

            devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (!devicePolicyManager.isAdminActive(componentName)) {
                val activateDeviceAdminIntent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)

                activateDeviceAdminIntent.putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(this, PolicyAdmin::class.java)
                )

                // It is good practice to include the optional explanation text to
                // explain to user why the application is requesting to be a device
                // administrator. The system will display this message on the activation
                // screen.
                activateDeviceAdminIntent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Stop the filter from being uninstalled")

                startActivityForResult(activateDeviceAdminIntent, 1234)
            } else {
                toast("Already a device admin!")
            }
        }
        
        val vpnActivityButton: Button = find(R.id.vpnActivityButton)
        vpnActivityButton.setOnClickListener { 
            startActivity(Intent(this, LocalVPN::class.java))
        }
    }
}
