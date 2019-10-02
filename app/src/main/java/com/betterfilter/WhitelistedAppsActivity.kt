package com.betterfilter

import android.content.pm.PackageInfo
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_installed_app.view.*
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.widget.ProgressBar
import org.jetbrains.anko.*
import org.jetbrains.anko.db.delete
import org.jetbrains.anko.db.insert
import org.jetbrains.anko.db.select
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
                appList.add(
                    AppItem(
                        app.loadIcon(packageManager),
                        app.loadLabel(packageManager).toString(),
                        app.packageName
                    )
                )
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

    class RecyclerAdapter(private val apps: ArrayList<AppItem>): RecyclerView.Adapter<RecyclerAdapter.AppHolder>(), AnkoLogger {
        class AppHolder(v: View) : RecyclerView.ViewHolder(v), AnkoLogger

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): AppHolder {
            val inflatedView =  LayoutInflater.from(parent.context).inflate(R.layout.item_installed_app, parent, false)
            return AppHolder(inflatedView)
        }

        override fun getItemCount(): Int  = apps.size

        override fun onBindViewHolder(holder: AppHolder, position: Int) {
            val context = holder.itemView.context

            holder.itemView.textView7.text = apps[position].visibleName
            holder.itemView.imageView.image = apps[position].image

            //set the switch position from the db
            context.database.use {
                select(
                    "whitelisted_apps",
                    "package_name", "visible_name"
                )
                    .whereSimple("package_name = ?", apps[position].packageName)
                    .exec {
                        holder.itemView.switch1.isChecked = this.count > 0
                    }
            }

            holder.itemView.switch1.onCheckedChange { _, isChecked ->

                //add the item if the switch is checked on, remove if switched off
                if (isChecked) {
                    context.database.use {
                        insert("whitelisted_apps",
                            "package_name" to apps[position].packageName,
                            "visible_name" to apps[position].packageName)
                    }
                } else {
                    context.database.use {
                        delete("whitelisted_apps",
                            "package_name = {package}", "package" to apps[position].packageName)
                    }
                }
            }
        }

        //apparently oncheckedchange is called even when the item is being recycled, which is ridiculous
        //set the listener to null so we don't remove the item from the db as soon as the view is recycled
        override fun onViewRecycled(holder: AppHolder) {
            holder.itemView.switch1.setOnCheckedChangeListener(null)
            super.onViewRecycled(holder)
        }
    }
}

class AppItem(val image: Drawable?, val visibleName: String, val packageName: String)
