package com.betterfilter

import android.content.Context
import com.betterfilter.Extensions.runOnUiThread
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.anko.*
import java.io.File
import java.io.InputStream

class APIClient(val context: Context): AnkoLogger {

    enum class Status {Success, Failure}

    val client = OkHttpClient()

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

    fun downloadMultipleHostsFiles(urls: List<String>, completionHandler: (Status) -> Unit) {

        val hostsFiles = mutableSetOf<String>()

        doAsync(exceptionHandler = { completionHandler(Status.Failure) }) {
            val brokenUrls = mutableListOf<String>()
            urls.forEachIndexed { index, url ->
                try {
                    val request = Request.Builder()
                        .url(url)
                        .build()
                    val response = client.newCall(request).execute()
                    val inputStream: InputStream =
                        response.body?.byteStream() ?: throw Exception("null body")

                    val file = File(context.filesDir, "net_hosts$index")
                    file.delete()

                    context.openFileOutput("net_hosts$index", Context.MODE_PRIVATE).use {
                        var data = ByteArray(1024)
                        while (inputStream.read(data) != -1) {
                            it.write(data)
                            data = ByteArray(1024)
                        }
                        it.flush()
                    }

                    hostsFiles.add("net_hosts$index")
                } catch (e: Exception) {
                    //todo: notify user that url wasn't working. store broken alerts and show at the end in alertdialog
                    error("couldn't download hosts file from url $url , error was $e")
                    brokenUrls.add(url)
                }

            }

            if (brokenUrls.isNotEmpty()) {
                var message = "The following hosts sources failed to download: \n"
                for (url in brokenUrls) {
                    message += url + "\n"
                }
                uiThread {
                    context.alert(message) {
                        yesButton {  }
                    }.show()
                }

            }
            
            debug("hostsfiles: $hostsFiles")

            with(context.defaultSharedPreferences.edit()) {
                putStringSet("hosts-files", hostsFiles)
                commit()
            }

            completionHandler(Status.Success)
        }

    }

}