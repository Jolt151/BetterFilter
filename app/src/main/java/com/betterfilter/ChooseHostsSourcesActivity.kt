package com.betterfilter

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.find
import android.widget.Toast
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T




class ChooseHostsSourcesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_hosts_sources)

        //val hosts = defaultSharedPreferences.getStringSet("hosts-urls", setOf())
        //temp array:
        val hosts = arrayListOf<String>("https://raw.githubusercontent.com/FadeMind/hosts.extras/master/add.Risk/hosts", "https://raw.githubusercontent.com/FadeMind/hosts.extras/master/add.Spam/hosts")
        val listView: ListView = findViewById(R.id.listview)
        val arrayAdapter = HostsAdapter(this, ArrayList(hosts))
        listView.adapter = arrayAdapter

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        getMenuInflater().inflate(R.menu.menu_settings_choose_hosts_sources, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        val id = item?.getItemId()

        //noinspection SimplifiableIfStatement
        if (id == R.id.menu_add_host) {
            //add host
            return true
        } else if (id == R.id.menu_info_hosts) {
            //show some information about hosts files
        }
        return super.onOptionsItemSelected(item)
    }

    class HostsAdapter(private val context: Context,
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

            //val editHost: Image

            return rowView
        }
    }
}
