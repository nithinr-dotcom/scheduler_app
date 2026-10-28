package com.wac.worktrack.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.wac.worktrack.core.db
import kotlinx.coroutines.tasks.await

/**
 * Location reminders ("when I reach the office"). Each enabled reminder with a place becomes a
 * geofence; Google Play services wakes [GeofenceReceiver] on arrival. Needs "Allow all the time"
 * location access, because the phone is usually in a pocket when you arrive.
 */
object Geofences {
    private const val PREFIX = "r"

    fun hasForeground(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackground(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0, Intent(context, GeofenceReceiver::class.java),
            // Play services fills in the event details, so this one has to be mutable.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    /** Unforced syncs that would register the same fences are skipped for this long. */
    private const val RESYNC_MILLIS = 60 * 60_000L

    private var lastKey: String? = null
    private var lastSyncAt = 0L

    /**
     * Re-registers geofences to match the enabled location reminders. Unless [force], nothing
     * happens if the same set was registered within [RESYNC_MILLIS].
     */
    @SuppressLint("MissingPermission")
    suspend fun sync(context: Context, force: Boolean = true) {
        val granted = hasForeground(context) && hasBackground(context)
        val places = context.db.life().placesNow().associateBy { it.id }
        val wanted = context.db.reminders().locationReminders().mapNotNull { r -> places[r.placeId]?.let { r to it } }
        val key = granted.toString() + wanted.joinToString { (r, p) -> "${r.id}:${p.lat},${p.lng},${p.radiusMeters}" }
        val now = System.currentTimeMillis()
        if (!force && key == lastKey && now - lastSyncAt < RESYNC_MILLIS) return
        lastKey = key
        lastSyncAt = now
        val client = LocationServices.getGeofencingClient(context)
        runCatching { client.removeGeofences(pending(context)).await() }
        if (!granted) return
        val fences = wanted.map { (r, p) ->
            Geofence.Builder()
                .setRequestId(PREFIX + r.id)
                .setCircularRegion(p.lat, p.lng, p.radiusMeters.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }
        if (fences.isEmpty()) return
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // don't fire just because you're already there when saving
            .addGeofences(fences)
            .build()
        runCatching { client.addGeofences(request, pending(context)).await() }
    }

    /** One fresh location fix, for saving "where I am now" as a place. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): android.location.Location? {
        if (!hasForeground(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
            ?: client.lastLocation.await()
    }

    fun reminderId(requestId: String): Long? = requestId.removePrefix(PREFIX).toLongOrNull()
}

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            // Location was switched off, which removes every geofence; add them back once it's on.
            if (event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) launchAsync { Geofences.sync(context) }
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val ids = event.triggeringGeofences.orEmpty().mapNotNull { Geofences.reminderId(it.requestId) }
        launchAsync {
            var changed = false
            for (id in ids) {
                val r = context.db.reminders().get(id) ?: continue
                if (!r.enabled) continue
                Notifs.reminder(context, r)
                // "Every time" reminders stay on; one-off ones switch off after firing.
                if (!r.repeatDaily) {
                    context.db.reminders().upsert(r.copy(enabled = false))
                    changed = true
                }
            }
            if (changed) Geofences.sync(context)
        }
    }
}
