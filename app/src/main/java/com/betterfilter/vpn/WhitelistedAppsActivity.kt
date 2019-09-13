package com.betterfilter.vpn

import android.content.pm.PackageInfo
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.betterfilter.R
import kotlinx.android.synthetic.main.item_installed_app.view.*
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.widget.ProgressBar
import com.betterfilter.Constants
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk27.coroutines.onCheckedChange


class WhitelistedAppsActivity : AppCompatActivity(), AnkoLogger {

    private lateinit var linearLayoutManager: LinearLayoutManager
    lateinit var adapter: RecyclerAdapter
    lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whitelisted_apps)
        recyclerView = find(R.id.recyclerView)

        val progressbar: ProgressBar = find(R.id.progressBar)
        progressbar.visibility = View.VISIBLE

        doAsync {
            val apps: ArrayList<AppItem> = getInstalledApps()
            val sortedApps = ArrayList(apps.sortedWith(compareBy {it.visibleName}))
            uiThread {
                progressbar.visibility = View.GONE
                linearLayoutManager = LinearLayoutManager(it)
                recyclerView.layoutManager = linearLayoutManager

                adapter = RecyclerAdapter(sortedApps)
                recyclerView.adapter = adapter
            }
        }

    }

    fun getInstalledApps(): ArrayList<AppItem> {
        val appList = arrayListOf<AppItem>()
        val installedAppList = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installedAppList) {
            if ((app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                appList.add(AppItem(app.loadIcon(packageManager), app.loadLabel(packageManager).toString(), app.packageName))
            }
        }
        return appList
    }

    /**
     * Return whether the given PackageInfo represents a system package or not.
     * User-installed packages (Market or otherwise) should not be denoted as
     * system packages.
     *
     */
    private fun PackageInfo.isSystemPackage(): Boolean {
        return this.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
    }

    class RecyclerAdapter(private val apps: ArrayList<AppItem>): RecyclerView.Adapter<RecyclerAdapter.AppHolder>() {
        class AppHolder(v: View) : RecyclerView.ViewHolder(v), View.OnClickListener, AnkoLogger {
            init {
                v.setOnClickListener(this)
            }

            override fun onClick(p0: View?) {
                info("item clicked")
            }
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): RecyclerAdapter.AppHolder {
            val inflatedView =  LayoutInflater.from(parent.context).inflate(R.layout.item_installed_app, parent, false)
            return AppHolder(inflatedView)
        }

        override fun getItemCount(): Int  = apps.size

        override fun onBindViewHolder(holder: RecyclerAdapter.AppHolder, position: Int) {
            val defaultSharedPreferences = holder.itemView.context.defaultSharedPreferences
            val checkedApps = defaultSharedPreferences.getStringSet(Constants.Prefs.WHITELISTED_APPS, mutableSetOf())

            holder.itemView.textView7.text = apps[position].visibleName
            holder.itemView.imageView.image = apps[position].image
            holder.itemView.switch1.isChecked = checkedApps?.contains(apps[position].packageName) ?: false

            holder.itemView.switch1.onCheckedChange { buttonView, isChecked ->
                val latestCheckedApps = defaultSharedPreferences.getStringSet(Constants.Prefs.WHITELISTED_APPS, mutableSetOf())
                if (isChecked) latestCheckedApps?.add(apps[position].packageName)
                else latestCheckedApps?.remove(apps[position].packageName)
                with(holder.itemView.context.defaultSharedPreferences.edit()) {
                    remove(Constants.Prefs.WHITELISTED_APPS)
                    apply()
                    putStringSet(Constants.Prefs.WHITELISTED_APPS, latestCheckedApps)
                    apply()
                }
            }
        }
    }
}

class AppItem(val image: Drawable?, val visibleName: String, val packageName: String)
