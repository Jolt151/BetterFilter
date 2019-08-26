package com.betterfilter

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import com.betterfilter.vpn.VpnHostsService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.find
import org.jetbrains.anko.info
import java.io.File
import java.io.InputStream

class VpnActivity : AppCompatActivity(), AnkoLogger {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_vpn)

        val stevenBlackCheckBox: CheckBox = find(R.id.stevenBlackHosts)
        val gamblingHostsCheckBox: CheckBox = find(R.id.gamblingHosts)
        val socialHostsCheckBox: CheckBox = find(R.id.socialHosts)

        val vpnButton: Button = find(R.id.vpn)
        vpnButton.setOnClickListener {

            var url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts"
            if (gamblingHostsCheckBox.isChecked) {
                info("gambling is checked")
                url = url.replace("porn", "gambling-porn")
            }
            if (socialHostsCheckBox.isChecked){
                info("social is checked")
                url = url.replace(Regex("porn"), "porn-social")
            }
            info("url: $url")

            doAsync {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .build()
                val response = client.newCall(request).execute()
                val inputStream: InputStream = response.body?.byteStream() ?: throw Exception("null body")

                val file = File(filesDir, "net_hosts")
                file.delete()
                info(filesDir.listFiles())

                openFileOutput("net_hosts", Context.MODE_PRIVATE).use {
                    var data = ByteArray(1024)
                    while (inputStream.read(data) != -1) {
                        it.write(data)
                        data = ByteArray(1024)
                    }
                    it.flush()
                }

                val intent = VpnService.prepare(this@VpnActivity)
                if (intent != null) startActivityForResult(intent, 1)
                else onActivityResult(1, RESULT_OK, null)
            }


        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                val intent = Intent(this, VpnHostsService::class.java)
                startService(intent)
            }
        }

    }
}