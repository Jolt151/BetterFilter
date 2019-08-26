package com.betterfilter

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import com.betterfilter.Extensions.sha256
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.find
import org.jetbrains.anko.info
import org.jetbrains.anko.toast


class AdminConsoleActivity : AppCompatActivity(), AnkoLogger {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        if (!App.isAuthenticated) {
            startActivity(
                Intent(this, PasswordActivity::class.java).putExtra("finishAffinity", false)
            )
        }

        val updatePasswordEditText: EditText = find(R.id.updatePasswordEditText)

        val updatePasswordButton: Button = find(R.id.updatePasswordButton)
        updatePasswordButton.setOnClickListener {
            if (updatePasswordEditText.text.toString().isBlank()) {
                updatePasswordEditText.error = "Password cannot be empty"
                return@setOnClickListener
            }

            val sharedPref = this.getSharedPreferences("password", Context.MODE_PRIVATE) ?: return@setOnClickListener
            with(sharedPref.edit()) {
                putString("password-sha256", updatePasswordEditText.text.toString().sha256())
                apply()
            }
            toast("Password updated")

        }
    }
}
