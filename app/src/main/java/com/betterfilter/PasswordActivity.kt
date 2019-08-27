package com.betterfilter

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import com.betterfilter.Extensions.sha256
import org.jetbrains.anko.find
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import android.view.KeyEvent.KEYCODE_BACK
import org.jetbrains.anko.toast


class PasswordActivity : AppCompatActivity() {

    companion object {
        const val RESULT_AUTHENTICATED = 200
        const val RESULT_UNAUTHENTICATED = 201
    }


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
                if (intent.getBooleanExtra("finishAffinity", false)) {
                    finishAffinity()
                } else {
                    finish()
                }
            } else {
                passwordEditText.error = "Incorrect Password"
            }

        }
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KEYCODE_BACK) {
            // do something on back.
            if (App.isAuthenticated) setResult(RESULT_AUTHENTICATED)
            else setResult(RESULT_UNAUTHENTICATED)

        }
        return super.onKeyDown(keyCode, event)

    }

    override fun onStop() {
        if (App.isAuthenticated) setResult(RESULT_AUTHENTICATED)
        else setResult(RESULT_UNAUTHENTICATED)
        super.onStop()
    }

    override fun onDestroy() {
        if (App.isAuthenticated) setResult(RESULT_AUTHENTICATED)
        else setResult(RESULT_UNAUTHENTICATED)
        super.onDestroy()
    }
}