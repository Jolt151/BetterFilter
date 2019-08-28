package com.betterfilter

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.betterfilter.Extensions.sha256
import org.jetbrains.anko.find
import org.jetbrains.anko.support.v4.toast


class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, MySettingsFragment())
            .commit()
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment)
        fragment.arguments = args
        fragment.setTargetFragment(caller, 0)
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, fragment)
            .addToBackStack(null)
            .commit()
        return true
    }
}

class MySettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        val categories: MultiSelectListPreference? = findPreference("categories")
//        categories?.setOnPreferenceClickListener {
//
//        }

        val changePassword: Preference? = findPreference("changePassword")

        changePassword?.setOnPreferenceClickListener {
            val view: View = layoutInflater.inflate(R.layout.change_password_dialog, null)
            val passwordEditText: EditText = view.find(R.id.passwordEditText)
            val confirmPasswordEditText: EditText = view.find(R.id.comfirmPasswordEditText)

            val alertDialogBuilder = AlertDialog.Builder(this.context!!)
            alertDialogBuilder.setView(view)
                .setPositiveButton("OK") { _, _ -> }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            val alertDialog = alertDialogBuilder.show()
            val positiveButton: Button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
            passwordEditText.requestFocus()
            alertDialog?.window?.setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

            positiveButton.setOnClickListener {
                val isValid =
                if (passwordEditText.text.toString().isEmpty()) {
                    passwordEditText.error = "Password cannot be empty"
                    false
                } else if (passwordEditText.text.toString() != confirmPasswordEditText.text.toString()) {
                    confirmPasswordEditText.error = "Passwords must match!"
                    false
                } else true

                if(isValid) {
                    val sharedPref = this.context?.getSharedPreferences("password", Context.MODE_PRIVATE) ?: return@setOnClickListener
                    with(sharedPref.edit()) {
                        putString("password-sha256", passwordEditText.text.toString().sha256())
                        commit()
                    }
                    toast("Password updated")
                    
                    alertDialog.dismiss()
                }
            }

            true
        }

    }
}

class AdvancedFilterSettingsFragment: PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_filter_advanced, rootKey)
    }
}
