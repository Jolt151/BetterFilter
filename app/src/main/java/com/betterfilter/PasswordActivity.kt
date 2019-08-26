package com.betterfilter

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import com.betterfilter.Extensions.sha256
import org.jetbrains.anko.find
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class PasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password)

        val passwordEditText: EditText = find(R.id.passwordEditText)
        val enterPasswordButton: Button = find(R.id.enterPasswordButton)

        enterPasswordButton.setOnClickListener {

            val sharedPref = this.getSharedPreferences("password", Context.MODE_PRIVATE) ?: return@setOnClickListener
            val hashedPassword = sharedPref.getString("password-sha256", "1234".sha256())

            if (passwordEditText.text.toString().sha256() == hashedPassword) {
                App.isAuthenticated = true
                if (intent.getBooleanExtra("finishAffinity", true)) {
                    finishAffinity()
                } else {
                    finish()
                }
            } else {
                passwordEditText.error = "Incorrect Password"
            }

        }
    }
}