package com.betterfilter.antibypass

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import com.betterfilter.vpn.AdVpnService
import com.betterfilter.vpn.VpnStatus
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info

class VpnMonitorJob : JobService(), AnkoLogger {

    override fun onStartJob(parameters: JobParameters): Boolean {
        info("job started")

        val isRunning = AdVpnService.isRunningObservable.value
        if (isRunning != VpnStatus.RUNNING && isRunning != VpnStatus.STARTING) {
            info("starting vpn")
            startActivity(Intent(this, AutoRestartActivity::class.java).addFlags(FLAG_ACTIVITY_NEW_TASK))
        } else {
            info("already running, not starting vpn")
        }

        val builder = JobInfo.Builder(2, ComponentName(this, VpnMonitorJob::class.java))
        builder.setMinimumLatency(1000 * 60)
        val jobScheduler = getSystemService(JobScheduler::class.java)
        jobScheduler.schedule(builder.build())


        jobFinished(parameters, false)
        return true
    }

    override fun onStopJob(parameters: JobParameters): Boolean {
        info("job stopped")
        return true
    }

}
