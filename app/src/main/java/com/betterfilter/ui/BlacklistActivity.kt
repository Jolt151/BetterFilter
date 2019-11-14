package com.betterfilter.ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import android.widget.*
import com.betterfilter.Constants
import com.betterfilter.R
import org.jetbrains.anko.*
import org.jetbrains.anko.design.longSnackbar

class BlacklistActivity : AppCompatActivity() {

    lateinit var listView: ListView
    lateinit var arrayAdapter: BlacklistedAdapter
    lateinit var hosts: MutableSet<String>
    lateinit var hostsList: ArrayList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_hosts_sources)

        hosts = defaultSharedPreferences.getStringSet(Constants.Prefs.BLACKLISTED_URLS, mutableSetOf()) ?: mutableSetOf()
        hostsList = ArrayList(hosts)
        listView = findViewById(R.id.listview)
        arrayAdapter = BlacklistedAdapter(this, ArrayList(hostsList.sorted()))
        listView.adapter = arrayAdapter

    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        getMenuInflater().inflate(R.menu.menu_settings_choose_hosts_sources, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        val id = item?.getItemId()
        if (id == R.id.menu_add_host) {
            this.alert("Add blacklisted URL") {
                lateinit var hostsEditText: EditText
                customView {
                    verticalLayout {
                        hostsEditText = editText {
                            top
                            hint = "Blacklisted URL"
                        }.lparams {
                            topMargin = 10
                            width = matchParent
                        }
                    }
                }
                yesButton {

                    if (hostsEditText.text.isNotBlank()) {
                        //add to sharedPreferences
                        val hostsSet: MutableSet<String> = defaultSharedPreferences.getStringSet(
                            Constants.Prefs.BLACKLISTED_URLS, mutableSetOf()) ?: mutableSetOf()
                        hostsSet.add(hostsEditText.text.toString())
                        with(defaultSharedPreferences.edit()) {
                            //for some reason, we need to remove the set and apply first or it doesn't work
                            //possibly something to do with the memory references
                            //see https://stackoverflow.com/questions/17469583/setstring-in-android-sharedpreferences-does-not-save-on-force-close
                            remove(Constants.Prefs.BLACKLISTED_URLS)
                            apply()
                            putStringSet(Constants.Prefs.BLACKLISTED_URLS, hostsSet)
                            apply()
                        }
                        arrayAdapter = BlacklistedAdapter(this.ctx, ArrayList(hostsSet))
                        listView.adapter = arrayAdapter
                    }
                }
                noButton {
                    it.dismiss()
                }
            }.show()
            return true
        } else if (id == R.id.menu_info_hosts) {
            //show some information about hosts files
        }
        return super.onOptionsItemSelected(item)
    }

    inner class BlacklistedAdapter(private val context: Context,
                             private val dataSource: ArrayList<String>) : BaseAdapter() {

        private val inflater: LayoutInflater =
            context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

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

            val blacklistedUrl: TextView = rowView.find(R.id.hostUrl)
            blacklistedUrl.setText(dataSource[position])

            val editUrl: ImageView = rowView.find(R.id.hosts_edit)
            editUrl.setOnClickListener {
                val oldText = blacklistedUrl.text.toString()
                alert("Edit blacklisted URL") {
                    lateinit var blacklistedUrlEditText: EditText
                    customView {
                        verticalLayout {
                            blacklistedUrlEditText = editText {
                                setText(blacklistedUrl.text)
                                top
                                hint = "Blacklisted URL"
                            }.lparams {
                                topMargin = 10
                                width = matchParent
                            }
                        }
                    }
                    yesButton {
                        if (blacklistedUrlEditText.text.isNotBlank()) {
                            //add to sharedPreferences
                            val hostsSet: MutableSet<String> =
                                defaultSharedPreferences.getStringSet(
                                    Constants.Prefs.BLACKLISTED_URLS,
                                    mutableSetOf()
                                )
                                    ?: mutableSetOf()
                            hostsSet.remove(oldText)
                            hostsSet.add(blacklistedUrlEditText.text.toString())
                            with(defaultSharedPreferences.edit()) {
                                //for some reason, we need to remove the set and apply first or it doesn't work
                                //possibly something to do with the memory references
                                //see https://stackoverflow.com/questions/17469583/setstring-in-android-sharedpreferences-does-not-save-on-force-close
                                remove(Constants.Prefs.BLACKLISTED_URLS)
                                apply()
                                putStringSet(Constants.Prefs.BLACKLISTED_URLS, hostsSet)
                                apply()
                            }
                            arrayAdapter = BlacklistedAdapter(
                                this.ctx,
                                ArrayList(ArrayList(hostsSet).sorted())
                            )
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
                val oldText = blacklistedUrl.text.toString()

                val blacklistedSet: MutableSet<String> =
                    defaultSharedPreferences.getStringSet(Constants.Prefs.BLACKLISTED_URLS, mutableSetOf())
                        ?: mutableSetOf()
                blacklistedSet.remove(oldText)
                with(defaultSharedPreferences.edit()) {
                    //for some reason, we need to remove the set and apply first or it doesn't work
                    //possibly something to do with the memory references
                    //see https://stackoverflow.com/questions/17469583/setstring-in-android-sharedpreferences-does-not-save-on-force-close
                    remove(Constants.Prefs.BLACKLISTED_URLS)
                    apply()
                    putStringSet(Constants.Prefs.BLACKLISTED_URLS, blacklistedSet)
                    apply()
                }
                val arrayAdapter =
                    BlacklistedAdapter(this.context, ArrayList(ArrayList(blacklistedSet).sorted()))
                listView.adapter = arrayAdapter

                find<View>(R.id.listview).longSnackbar("Deleted", "Undo") {
                    val hostsSet: MutableSet<String> =
                        defaultSharedPreferences.getStringSet(Constants.Prefs.BLACKLISTED_URLS, mutableSetOf())
                            ?: mutableSetOf()
                    hostsSet.add(oldText)
                    with(defaultSharedPreferences.edit()) {
                        //for some reason, we need to remove the set and apply first or it doesn't work
                        //possibly something to do with the memory references
                        //see https://stackoverflow.com/questions/17469583/setstring-in-android-sharedpreferences-does-not-save-on-force-close
                        remove(Constants.Prefs.BLACKLISTED_URLS)
                        apply()
                        putStringSet(Constants.Prefs.BLACKLISTED_URLS, hostsSet)
                        apply()
                    }
                    val arrayAdapter =
                        BlacklistedAdapter(this.context, ArrayList(ArrayList(hostsSet).sorted()))
                    listView.adapter = arrayAdapter
                }.show()
            }

            return rowView
        }
    }
}
