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
import org.jetbrains.anko.*


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
                val fos = FileOutputStream(file, false)
                fos.write(inputStream.readBytes())


                uiThread {
                    downloadingProgressBar.visibility = View.GONE
                }
            }
        }

    }
}
