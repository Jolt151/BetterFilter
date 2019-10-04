package com.betterfilter

import android.content.Context
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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import org.jetbrains.anko.*
import org.jetbrains.anko.db.delete
import org.jetbrains.anko.db.insert
import org.jetbrains.anko.db.select
import org.jetbrains.anko.sdk27.coroutines.onCheckedChange
import androidx.fragment.app.FragmentPagerAdapter


class WhitelistedAppsActivity : AppCompatActivity(), AnkoLogger {

    lateinit var tabLayout: TabLayout
    lateinit var viewPager: ViewPager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whitelisted_apps)

        tabLayout = find(R.id.tab_layout)
        viewPager = find(R.id.pager)
        viewPager.adapter = PageAdapter(supportFragmentManager, tabLayout.tabCount)
        tabLayout.setupWithViewPager(viewPager)

    }
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

class AppItem(val image: Drawable?, val visibleName: String, val packageName: String)

class PageAdapter(fm: FragmentManager, private val numOfTabs: Int): FragmentPagerAdapter(fm) {

    val titles = listOf("Downloaded Apps", "System Apps")

    override fun getItem(position: Int): Fragment {
        when (position) {
            0 -> return DownloadedAppsFragment()
            1 -> return SystemAppsFragment()
            else -> return DownloadedAppsFragment()
        }
    }

    override fun getCount(): Int {
        return numOfTabs
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return titles[position]
    }
}

class DownloadedAppsFragment: Fragment() {
    lateinit var recyclerView: RecyclerView
    lateinit var progressbar: ProgressBar
    private lateinit var linearLayoutManager: LinearLayoutManager
    lateinit var adapter: RecyclerAdapter


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_installed_apps, container, false)

        recyclerView = view.find(R.id.recyclerView)
        progressbar = view.find(R.id.progressBar)

        progressbar.visibility = View.VISIBLE

        doAsync {
            val apps: ArrayList<AppItem> = view.context.getDownloadedApps()
            val sortedApps = ArrayList(apps.sortedWith(compareBy {it.visibleName}))
            uiThread {
                progressbar.visibility = View.GONE
                linearLayoutManager = LinearLayoutManager(view.context)
                recyclerView.layoutManager = linearLayoutManager

                adapter = RecyclerAdapter(sortedApps)
                recyclerView.adapter = adapter
            }
        }

        return view
    }
}

class SystemAppsFragment: Fragment() {

    lateinit var recyclerView: RecyclerView
    lateinit var progressbar: ProgressBar
    private lateinit var linearLayoutManager: LinearLayoutManager
    lateinit var adapter: RecyclerAdapter


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_installed_apps, container, false)

        recyclerView = view.find(R.id.recyclerView)
        progressbar = view.find(R.id.progressBar)

        progressbar.visibility = View.VISIBLE

        doAsync {
            val apps: ArrayList<AppItem> = view.context.getSystemApps()
            val sortedApps = ArrayList(apps.sortedWith(compareBy {it.visibleName}))
            uiThread {
                progressbar.visibility = View.GONE
                linearLayoutManager = LinearLayoutManager(view.context)
                recyclerView.layoutManager = linearLayoutManager

                adapter = RecyclerAdapter(sortedApps)
                recyclerView.adapter = adapter
            }
        }

        return view
    }
}

fun Context.getDownloadedApps(): ArrayList<AppItem> {
    val appList = arrayListOf<AppItem>()
    val installedAppList = this.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    for (app in installedAppList) {

        //not system app and not updated system app
        if ((app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
            appList.add(
                AppItem(
                    app.loadIcon(this.packageManager),
                    app.loadLabel(this.packageManager).toString(),
                    app.packageName
                )
            )
        }
    }
    return appList
}

fun Context.getSystemApps(): ArrayList<AppItem> {
    val appList = arrayListOf<AppItem>()
    val installedAppList = this.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    for (app in installedAppList) {

        //if the app has a launcher icon
        if (this.packageManager.getLaunchIntentForPackage(app.packageName) != null) {
            //not system app and not updated system app
            if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                appList.add(
                    AppItem(
                        app.loadIcon(this.packageManager),
                        app.loadLabel(this.packageManager).toString(),
                        app.packageName
                    )
                )
            }
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