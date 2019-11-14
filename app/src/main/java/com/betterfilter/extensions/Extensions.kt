package com.betterfilter.extensions

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import com.betterfilter.Constants
import com.betterfilter.database
import com.betterfilter.vpn.AdVpnService
import com.betterfilter.vpn.Command
import org.jetbrains.anko.db.classParser
import org.jetbrains.anko.db.parseList
import org.jetbrains.anko.db.select

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
        categoriesUrls.add("https://raw.githubusercontent.com/Jolt151/just-hosts/master/hosts-ads")
    }
//    if (categories.contains("spotify")) {
//        categoriesUrls.add("https://raw.githubusercontent.com/Jolt151/just-hosts/master/hosts-spotify")
//    }

    return categoriesUrls
}

fun SharedPreferences.getAllHostsUrls(): List<String> {
    val mainUrl = Constants.DEFAULT_HOSTS_URL
    val categoriesUrls = this.getCategoriesUrls()
    val additionalUrls = this.getStringSet(Constants.Prefs.HOSTS_URLS, mutableSetOf()) ?: mutableSetOf()

    val urls = mutableListOf<String>()
    urls.add(mainUrl)
    urls.addAll(categoriesUrls)
    urls.addAll(additionalUrls)

    return urls
}

fun SharedPreferences.getDNSUrls(): List<String> {
    //get cleanbrowsing dns urls based on the content level the user chose
    //https://cleanbrowsing.org/filters

    val urls = mutableListOf<String>()

    if (this.getString("cleanBrowsingLevel", "adult") == "adult") { //todo: get from strings
        urls.add("185.228.168.10") //CB 1
/*        urls.add("185.228.169.11") //CB 2
        urls.add("2a0d:2a00:1::1") //CB IPV6 1
        urls.add("2a0d:2a00:2::1") //CB IPV6 2*/
    } else {
        urls.add("185.228.168.168")
/*        urls.add("185.228.169.168")
        urls.add("2a0d:2a00:1::")
        urls.add("2a0d:2a00:2::")*/
    }

    return urls
}

fun VpnService.Builder.addWhitelistedApps(context: Context) {
    data class AppPackage(val packageName: String)
    context.database.use {
        select(
            "whitelisted_apps",
            "package_name"
        ).exec {
            val whitelistedApps = parseList(classParser<AppPackage>())
            for (white in whitelistedApps) {
                this@addWhitelistedApps.addDisallowedApplication(white.packageName)
            }
        }
    }
}

fun Context.startVpn() {
    VpnService.prepare(this)
    val intent = Intent(this, AdVpnService::class.java)
    intent.putExtra("COMMAND", Command.START.ordinal)
    startService(intent)
}

fun Context.stopVpn() {
    val intent = Intent(this, AdVpnService::class.java)
    intent.putExtra("COMMAND", Command.STOP.ordinal)
    intent.putExtra("isFromOurButton", true)
    startService(intent)
}