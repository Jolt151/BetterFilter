package com.betterfilter.antibypass

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.betterfilter.R
import com.betterfilter.extensions.startVpn

class AutoRestartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_restart)

        if (!intent.getBooleanExtra("isFromOurButton", false)) {
            this.startVpn()
        }
        finish()

    }
}
