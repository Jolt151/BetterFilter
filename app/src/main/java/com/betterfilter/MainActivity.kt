package com.betterfilter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.find
import org.jetbrains.anko.error
import org.jetbrains.anko.info
import java.io.*
import android.R.attr.data





class MainActivity : AppCompatActivity(), AnkoLogger {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val downloadHostsButton: Button = find(R.id.downloadHosts)
        downloadHostsButton.setOnClickListener {
            info("clicked")

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
                val fos = FileOutputStream(file)
                fos.write(inputStream.readBytes())

            }
        }

    }
}
