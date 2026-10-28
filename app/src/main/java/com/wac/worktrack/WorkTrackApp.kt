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
        CloudBackup.schedule(this)
        // Ongoing notifications are cleared on reboot/update; the app process starting brings them back.
        Notifs.quickAdd(this)
        scope.launch {
            DayPlanner.ensure(this@WorkTrackApp)
            Alarms.rescheduleAll(this@WorkTrackApp)
        }
    }
}
