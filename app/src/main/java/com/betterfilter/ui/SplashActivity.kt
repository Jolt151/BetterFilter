package com.betterfilter.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.preference.PreferenceManager
import com.betterfilter.R
import org.jetbrains.anko.defaultSharedPreferences

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)


        if (defaultSharedPreferences.getBoolean("firstTimeInitCompleted", false)) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, MainIntroActivity::class.java))

            PreferenceManager.setDefaultValues(this, R.xml.settings, false)

        }
    }
}
