package com.betterfilter

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.util.Log
import android.widget.*
import androidx.core.view.marginTop
import kotlinx.android.synthetic.main.activity_choose_hosts_sources.*
import org.jetbrains.anko.*
import org.jetbrains.anko.design.longSnackbar
import org.jetbrains.anko.design.snackbar
import java.util.*
import kotlin.collections.ArrayList


class ChooseHostsSourcesActivity : AppCompatActivity(), AnkoLogger {

    lateinit var listView: ListView
    lateinit var arrayAdapter: HostsAdapter
    lateinit var hosts: MutableSet<String>
    lateinit var hostsList: ArrayList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_hosts_sources)

        hosts = defaultSharedPreferences.getStringSet(Constants.Prefs.HOSTS_URLS, mutableSetOf()) ?: mutableSetOf()
        hostsList = ArrayList(hosts)
        listView = findViewById(R.id.listview)
        arrayAdapter = HostsAdapter(this, ArrayList(hostsList.sorted()))
        listView.adapter = arrayAdapter

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        getMenuInflater().inflate(R.menu.menu_settings_choose_hosts_sources, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        val id = item?.getItemId()
        if (id == R.id.menu_add_host) {
            lateinit var hostsEditText: EditText
            val alert = this.alert("Add custom source") {
                customView {
                    verticalLayout {
                        hostsEditText = editText {
                            top
                            hint = "Hosts file URL"
                        }.lparams {
                            topMargin = 10
                            width = matchParent
                        }
                    }
                }
                yesButton { }
                noButton {
                    it.dismiss()
                }
            }.show()
            //separate onclicklistener so we could validate the input before dismissing
            alert.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (hostsEditText.text.isNotBlank()) {

                    if (!android.util.Patterns.WEB_URL.matcher(hostsEditText.text).matches()) {
                        hostsEditText.error = "Invalid URL"
                    } else {
                        //add to sharedPreferences
                        val hostsSet: MutableSet<String> = defaultSharedPreferences.getStringSet(Constants.Prefs.HOSTS_URLS, mutableSetOf()) ?: mutableSetOf()
                        hostsSet.add(hostsEditText.text.toString())
                        with(defaultSharedPreferences.edit()) {
                            //for some reason, we need to remove the set and apply first or it doesn't work
                            //possibly something to do with the memory references
                            //see https://stackoverflow.com/questions/17469583/setstring-in-android-sharedpreferences-does-not-save-on-force-close
                            remove(Constants.Prefs.HOSTS_URLS)
                            apply()
                            putStringSet(Constants.Prefs.HOSTS_URLS, hostsSet)
                            apply()
                        }
                        arrayAdapter = HostsAdapter(this.ctx, ArrayList(hostsSet))
                        listView.adapter = arrayAdapter
                        alert.dismiss()
                    }
                }
            }
            return true
        } else if (id == R.id.menu_info_hosts) {
            //show some information about hosts files
        }
        return super.onOptionsItemSelected(item)
    }

    inner class HostsAdapter(private val context: Context,
                        private val dataSource: ArrayList<String>) : BaseAdapter() {

        private val inflater: LayoutInflater
                = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        override fun getCount(): Int {
            return dataSource.size
        }

        override fun getItem(position: Int): String {
            return dataSource[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val rowView = inflater.inflate(R.layout.item_choose_hosts_sources, parent, false)

            val hostsUrl: TextView = rowView.find(R.id.hostUrl)
            hostsUrl.setText(dataSource[position])

            val editUrl: ImageView = rowView.find(R.id.hosts_edit)
            editUrl.setOnClickListener {
                val oldText = hostsUrl.text.toString()
                alert("Edit custom source") {
                    lateinit var hostsEditText: EditText
                    customView {
                        verticalLayout {
                            hostsEditText = editText {
                                setText(hostsUrl.text)
                                top
                                hint = "Hosts file URL"
                            }.lparams {
                                topMargin = 10
                                width = matchParent
                            }
                        }
                    }
                    yesButton {
                        if (hostsEditText.text.isNotBlank()) {
                            //add to sharedPreferences
                            val hostsSet: MutableSet<String> =
                                defaultSharedPreferences.getStringSet(Constants.Prefs.HOSTS_URLS, mutableSetOf())
                                    ?: mutableSetOf()
                            hostsSet.remove(oldText)
                            hostsSet.add(hostsEditText.text.toString())
                            with(defaultSharedPreferences.edit()) {
                                //for some reason, we need to remove the set and apply first or it doesn't work
                                //possibly something to do with the memory references
                                //see https://stackoverflow.com/questions/17469583/setstring-in-android-sharedpreferences-does-not-save-on-force-close
                                remove(Constants.Prefs.HOSTS_URLS)
                                apply()
                                putStringSet(Constants.Prefs.HOSTS_URLS, hostsSet)
                                apply()
                            }
                            arrayAdapter = HostsAdapter(this.ctx, ArrayList(ArrayList(hostsSet).sorted()))
                            listView.adapter = arrayAdapter
                        }
                    }
                    noButton {
                        it.dismiss()
                    }
                }.show()
            }

            val deleteUrl: ImageView = rowView.find(R.id.hosts_delete)
            deleteUrl.setOnClickListener {
                val oldText = hostsUrl.text.toString()

                val hostsSet: MutableSet<String> =
                    defaultSharedPreferences.getStringSet(Constants.Prefs.HOSTS_URLS, mutableSetOf())
                        ?: mutableSetOf()
                hostsSet.remove(oldText)
                with(defaultSharedPreferences.edit()) {
                    //for some reason, we need to remove the set and apply first or it doesn't work
                    //possibly something to do with the memory references
                    //see https://stackoverflow.com/questions/17469583/setstring-in-android-sharedpreferences-does-not-save-on-force-close
                    remove(Constants.Prefs.HOSTS_URLS)
                    apply()
                    putStringSet(Constants.Prefs.HOSTS_URLS, hostsSet)
                    apply()
                }
                val arrayAdapter = HostsAdapter(this.context, ArrayList(ArrayList(hostsSet).sorted()))
                listView.adapter = arrayAdapter

                find<View>(R.id.listview).longSnackbar("Deleted", "Undo") {
                    val hostsSet: MutableSet<String> =
                        defaultSharedPreferences.getStringSet(Constants.Prefs.HOSTS_URLS, mutableSetOf())
                            ?: mutableSetOf()
                    hostsSet.add(oldText)
                    with(defaultSharedPreferences.edit()) {
                        //for some reason, we need to remove the set and apply first or it doesn't work
                        //possibly something to do with the memory references
                        //see https://stackoverflow.com/questions/17469583/setstring-in-android-sharedpreferences-does-not-save-on-force-close
                        remove(Constants.Prefs.HOSTS_URLS)
                        apply()
                        putStringSet(Constants.Prefs.HOSTS_URLS, hostsSet)
                        apply()
                    }
                    val arrayAdapter = HostsAdapter(this.context, ArrayList(ArrayList(hostsSet).sorted()))
                    listView.adapter = arrayAdapter
                }.show()
            }

            return rowView
        }
    }
}
