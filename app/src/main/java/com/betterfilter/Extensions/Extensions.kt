package com.betterfilter.Extensions

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.betterfilter.Constants
import org.jetbrains.anko.defaultSharedPreferences

fun <T> T.runOnUiThread(func: () -> Unit) = run {
    if (Looper.getMainLooper() === Looper.myLooper()) {
        func()
    } else Handler(Looper.getMainLooper()).post {
        func()
    }
}

fun SharedPreferences.getCategoriesUrls(): List<String> {
    val categoriesUrls = mutableListOf<String>()
    val categories = this.getStringSet("categories", mutableSetOf()) ?: mutableSetOf()

    if (categories.contains("gambling")){
        categoriesUrls.add("https://raw.githubusercontent.com/Jolt151/just-hosts/master/hosts-gambling")
    }
    if (categories.contains("socialMedia")){
        categoriesUrls.add("https://raw.githubusercontent.com/Jolt151/just-hosts/master/hosts-social")
    }
    if (categories.contains("ads")) {
        categoriesUrls.add("https://github.com/Jolt151/just-hosts/blob/master/hosts-ads")
    }
//    if (categories.contains("spotify")) {
//        categoriesUrls.add("https://raw.githubusercontent.com/Jolt151/just-hosts/master/hosts-spotify")
//    }

    return categoriesUrls
}

fun SharedPreferences.getAllHostsUrls(): List<String> {
    val mainUrl = Constants.DEFAULT_HOSTS_URL
    val categoriesUrls = this.getCategoriesUrls()
    val additionalUrls = this.getStringSet("hosts-urls", mutableSetOf()) ?: mutableSetOf()

    val urls = mutableListOf<String>()
    urls.add(mainUrl)
    urls.addAll(categoriesUrls)
    urls.addAll(additionalUrls)

    return urls
}