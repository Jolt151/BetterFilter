package com.betterfilter

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import android.widget.*
import org.jetbrains.anko.*
import org.jetbrains.anko.design.longSnackbar

class WhitelistActivity : AppCompatActivity() {

    lateinit var listView: ListView
    lateinit var arrayAdapter: WhitelistedAdapter
    lateinit var hosts: MutableSet<String>
    lateinit var hostsList: ArrayList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_hosts_sources)

        hosts = defaultSharedPreferences.getStringSet("whitelisted-urls", mutableSetOf()) ?: mutableSetOf()
        hostsList = ArrayList(hosts)
        listView = findViewById(R.id.listview)
        arrayAdapter = WhitelistedAdapter(this, ArrayList(hostsList.sorted()))
        listView.adapter = arrayAdapter

    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        getMenuInflater().inflate(R.menu.menu_settings_choose_hosts_sources, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        val id = item?.getItemId()
        if (id == R.id.menu_add_host) {
            this.alert("Add whitelisted URL") {
                lateinit var hostsEditText: EditText
                customView {
                    verticalLayout {
                        hostsEditText = editText {
                            top
                            hint = "Whitelisted URL"
                        }.lparams {
                            topMargin = 10
                            width = matchParent
                        }
                    }
                }
                yesButton {

                    if (hostsEditText.text.isNotBlank()) {
                        //add to sharedPreferences
                        val hostsSet: MutableSet<String> = defaultSharedPreferences.getStringSet("whitelisted-urls", mutableSetOf()) ?: mutableSetOf()
                        hostsSet.add(hostsEditText.text.toString())
                        with(defaultSharedPreferences.edit()) {
                            //for some reason, we need to remove the set and apply first or it doesn't work
                            //possibly something to do with the memory references
                            //see https://stackoverflow.com/questions/17469583/setstring-in-android-sharedpreferences-does-not-save-on-force-close
                            remove("whitelisted-urls")
                            apply()
                            putStringSet("whitelisted-urls", hostsSet)
                            apply()
                        }
                        arrayAdapter = WhitelistedAdapter(this.ctx, ArrayList(hostsSet))
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

    inner class WhitelistedAdapter(private val context: Context,
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

            val whitelistedUrl: TextView = rowView.find(R.id.hostUrl)
            whitelistedUrl.setText(dataSource[position])

            val editUrl: ImageView = rowView.find(R.id.hosts_edit)
            editUrl.setOnClickListener {
                val oldText = whitelistedUrl.text.toString()
                alert("Edit whitelisted URL") {
                    lateinit var whitelistedUrlEditText: EditText
                    customView {
                        verticalLayout {
                            whitelistedUrlEditText = editText {
                                setText(whitelistedUrl.text)
                                top
                                hint = "Whitelisted URL"
                            }.lparams {
                                topMargin = 10
                                width = matchParent
                            }
                        }
                    }
                    yesButton {
                        if (whitelistedUrlEditText.text.isNotBlank()) {
                            //add to sharedPreferences
                            val hostsSet: MutableSet<String> =
                                defaultSharedPreferences.getStringSet(
                                    "whitelisted-urls",
                                    mutableSetOf()
                                )
                                    ?: mutableSetOf()
                            hostsSet.remove(oldText)
                            hostsSet.add(whitelistedUrlEditText.text.toString())
                            with(defaultSharedPreferences.edit()) {
                                //for some reason, we need to remove the set and apply first or it doesn't work
                                //possibly something to do with the memory references
                                //see https://stackoverflow.com/questions/17469583/setstring-in-android-sharedpreferences-does-not-save-on-force-close
                                remove("whitelisted-urls")
                                apply()
                                putStringSet("whitelisted-urls", hostsSet)
                                apply()
                            }
                            arrayAdapter = WhitelistedAdapter(
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
                val oldText = whitelistedUrl.text.toString()

                val whitelistedSet: MutableSet<String> =
                    defaultSharedPreferences.getStringSet("whitelisted-urls", mutableSetOf())
                        ?: mutableSetOf()
                whitelistedSet.remove(oldText)
                with(defaultSharedPreferences.edit()) {
                    //for some reason, we need to remove the set and apply first or it doesn't work
                    //possibly something to do with the memory references
                    //see https://stackoverflow.com/questions/17469583/setstring-in-android-sharedpreferences-does-not-save-on-force-close
                    remove("whitelisted-urls")
                    apply()
                    putStringSet("whitelisted-urls", whitelistedSet)
                    apply()
                }
                val arrayAdapter =
                    WhitelistedAdapter(this.context, ArrayList(ArrayList(whitelistedSet).sorted()))
                listView.adapter = arrayAdapter

                find<View>(R.id.listview).longSnackbar("Deleted", "Undo") {
                    val hostsSet: MutableSet<String> =
                        defaultSharedPreferences.getStringSet("whitelisted-urls", mutableSetOf())
                            ?: mutableSetOf()
                    hostsSet.add(oldText)
                    with(defaultSharedPreferences.edit()) {
                        //for some reason, we need to remove the set and apply first or it doesn't work
                        //possibly something to do with the memory references
                        //see https://stackoverflow.com/questions/17469583/setstring-in-android-sharedpreferences-does-not-save-on-force-close
                        remove("whitelisted-urls")
                        apply()
                        putStringSet("whitelisted-urls", hostsSet)
                        apply()
                    }
                    val arrayAdapter =
                        WhitelistedAdapter(this.context, ArrayList(ArrayList(hostsSet).sorted()))
                    listView.adapter = arrayAdapter
                }.show()
            }

            return rowView
        }
    }
}
