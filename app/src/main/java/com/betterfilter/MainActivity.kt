package com.betterfilter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import android.R.attr.data
import android.opengl.Visibility
import android.view.View
import android.widget.ProgressBar
import com.topjohnwu.superuser.Shell
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.Appcompat
import java.lang.Thread.sleep
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity(), AnkoLogger {

    lateinit var downloadHostsButton: Button
    lateinit var downloadingProgressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

    }
}
