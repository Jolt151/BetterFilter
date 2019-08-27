package com.betterfilter

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.betterfilter.Extensions.sha256
import com.github.paolorotolo.appintro.AppIntro
import com.github.paolorotolo.appintro.ISlidePolicy
import org.jetbrains.anko.support.v4.find
import org.jetbrains.anko.support.v4.toast
import org.jetbrains.anko.toast


class MainIntroActivity : AppIntro() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addSlide(WelcomeFragment())

    }
}

class WelcomeFragment: Fragment(), ISlidePolicy {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View? {

        return layoutInflater.inflate(R.layout.intro_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val passwordField: EditText = find(R.id.setPasswordEditText)

        val setPasswordButton: Button = find(R.id.lockButton)
        setPasswordButton.setOnClickListener {
            if (passwordField.text.toString().isBlank()) {
                passwordField.error = "Password cannot be empty"
                return@setOnClickListener
            }

            val sharedPref = this.context?.getSharedPreferences("password", Context.MODE_PRIVATE) ?: return@setOnClickListener
            with(sharedPref.edit()) {
                putString("password-sha256", passwordField.text.toString().sha256())
                commit()
            }
            toast("Password updated")

        }
    }

    override fun isPolicyRespected(): Boolean {
        val hasPassword = this.context?.getSharedPreferences("password", Context.MODE_PRIVATE)?.getString("password-sha256", null) != null
        return hasPassword
    }
    override fun onUserIllegallyRequestedNextPage() {
        toast("You need to set a password before continuing.")
    }

}