package com.betterfilter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import org.jetbrains.anko.find

class PasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password)

        val passwordEditText: EditText = find(R.id.passwordEditText)
        val enterPasswordButton: Button = find(R.id.enterPasswordButton)

        enterPasswordButton.setOnClickListener {
            if (passwordEditText.text.toString() == "1234") {
                App.isAuthenticated = true
                finish()
            }
        }
    }
}
