package com.betterfilter

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.anko.*
import java.io.InputStream

class APIClient(val context: Context): AnkoLogger {

    enum class Status {Success, Failure}

    private val client = OkHttpClient()

    fun downloadHostsFiles(urls: List<String>, completionHandler: (Status) -> Unit) {
        doAsync(exceptionHandler = { completionHandler(Status.Failure) }) {
            val brokenUrls = mutableListOf<String>()

            urls.forEach { url ->
                try {
                    val request = Request.Builder()
                        .url(url)
                        .build()
                    val response = client.newCall(request).execute()
                    val inputStream: InputStream =
                        response.body?.byteStream() ?: throw Exception("null body")

                    //use hashcode for the filename
                    val fileName = url.hashCode().toString()

                    //write file to disk
                    context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
                        var data = ByteArray(1024)
                        while (inputStream.read(data) != -1) {
                            it.write(data)
                            data = ByteArray(1024)
                        }
                        it.flush()
                    }


                } catch (e: Exception) {
                    error("couldn't download hosts file from url $url , error was $e")
                    brokenUrls.add(url)
                }
            }

            if (brokenUrls.isNotEmpty()) {
                uiThread {
                    context.toast("Some files failed to download.")
                }
            }

            uiThread {
                completionHandler(Status.Success)
            }
        }
    }

}