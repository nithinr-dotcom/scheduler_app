package com.wac.worktrack

import android.app.Application
import com.wac.worktrack.cloud.CloudBackup
import com.wac.worktrack.core.DayPlanner
import com.wac.worktrack.notify.Alarms
import com.wac.worktrack.notify.Notifs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WorkTrackApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifs.createChannels(this)
        // Ongoing notifications are cleared on reboot/update; the app process starting brings them back.
        Notifs.quickAdd(this)
        // Alarms, widgets, SMS and the QS tile all start the process, often several times a day, so
        // only cheap work happens here. Boot, update and the midnight rollover reschedule
        // everything; this only catches a day the rollover missed.
        scope.launch {
            CloudBackup.schedule(this@WorkTrackApp)
            if (!DayPlanner.planned(this@WorkTrackApp)) {
                DayPlanner.ensure(this@WorkTrackApp)
                Alarms.rescheduleAll(this@WorkTrackApp)
            }
        }
    }
}
