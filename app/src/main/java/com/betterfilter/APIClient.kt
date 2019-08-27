package com.betterfilter

import android.content.Context
import com.betterfilter.Extensions.runOnUiThread
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.info
import org.jetbrains.anko.uiThread
import java.io.File
import java.io.InputStream

class APIClient(context: Context): AnkoLogger {

    enum class Status {Success, Failure}

    val client = OkHttpClient()
    val context = context

    fun downloadNewHostsFile(url: String, completionHandler: (Status) -> Unit) {

        doAsync(exceptionHandler = {runOnUiThread{completionHandler(Status.Failure)}}) {
            val request = Request.Builder()
                .url(url)
                .build()
            val response = client.newCall(request).execute()
            val inputStream: InputStream = response.body?.byteStream() ?: throw Exception("null body")

            val file = File(context.filesDir, "net_hosts")
            file.delete()
            info(context.filesDir.listFiles())

            context.openFileOutput("net_hosts", Context.MODE_PRIVATE).use {
                var data = ByteArray(1024)
                while (inputStream.read(data) != -1) {
                    it.write(data)
                    data = ByteArray(1024)
                }
                it.flush()
            }

            uiThread {
                completionHandler(Status.Success)
            }
        }

    }

}