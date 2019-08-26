package com.betterfilter.Extensions

import android.os.Handler
import android.os.Looper

fun <T> T.runOnUiThread(func: () -> Unit) = run {
    if (Looper.getMainLooper() === Looper.myLooper()) {
        func()
    } else Handler(Looper.getMainLooper()).post {
        func()
    }
}