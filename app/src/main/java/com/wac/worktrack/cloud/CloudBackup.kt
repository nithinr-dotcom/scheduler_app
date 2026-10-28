package com.wac.worktrack.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.wac.worktrack.core.Prefs
import com.wac.worktrack.data.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Copies the whole local database to Firestore under users/{uid} and back.
 * The phone stays the source of truth; this is a backup for a single user, not live sync.
 * Layout: users/{uid} {chunks: {table: n}, updatedAt}, users/{uid}/tables/{table}_{i} {json}.
 */
object CloudBackup {
    private const val ROWS_PER_DOC = 400

    private fun userDoc(context: Context) =
        FirebaseFirestore.getInstance().collection("users")
            .document(Auth.user(context)?.uid ?: error("Not signed in"))

    /** Firestore waits for the server to confirm a write, which never comes while offline. */
    private const val TIMEOUT_MILLIS = 60_000L

    private suspend fun <T> online(block: suspend () -> T): T =
        withTimeoutOrNull(TIMEOUT_MILLIS) { block() } ?: error("No connection to the cloud — check your internet and try again.")

    /** Returns the number of rows uploaded. */
    suspend fun backup(context: Context): Int = online { backupNow(context) }

    private suspend fun backupNow(context: Context): Int = withContext(Dispatchers.IO) {
        val doc = userDoc(context)
        val oldChunks = (doc.get().await().get("chunks") as? Map<*, *>).orEmpty()
        val chunks = mutableMapOf<String, Int>()
        var rows = 0
        for ((table, all) in Snapshot.dump(context)) {
            rows += all.size
            val parts = all.chunked(ROWS_PER_DOC)
            chunks[table] = parts.size
            parts.forEachIndexed { i, part ->
                doc.collection("tables").document("${table}_$i")
                    .set(mapOf("table" to table, "chunk" to i, "json" to JSONArray(part).toString())).await()
            }
            val old = (oldChunks[table] as? Number)?.toInt() ?: 0
            for (i in parts.size until old) doc.collection("tables").document("${table}_$i").delete().await()
        }
        doc.set(mapOf("chunks" to chunks, "updatedAt" to System.currentTimeMillis(), "schema" to Snapshot.FORMAT)).await()
        Prefs(context).lastBackupMillis = System.currentTimeMillis()
        rows
    }

    /** Replaces all local data with the cloud copy. Returns rows restored, or -1 if there's no backup. */
    suspend fun restore(context: Context): Int = online { restoreNow(context) }

    private suspend fun restoreNow(context: Context): Int = withContext(Dispatchers.IO) {
        val doc = userDoc(context)
        val chunks = doc.get().await().get("chunks") as? Map<*, *> ?: return@withContext -1
        val data = Snapshot.TABLES.associateWith { table ->
            val n = (chunks[table] as? Number)?.toInt() ?: 0
            (0 until n).flatMap { i ->
                val json = doc.collection("tables").document("${table}_$i").get().await().getString("json") ?: "[]"
                val arr = JSONArray(json)
                List(arr.length()) { arr.getJSONObject(it) }
            }
        }
        Snapshot.load(context, data)
    }

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (!Prefs(context).autoBackup) {
            wm.cancelUniqueWork(BackupWorker.NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(BackupWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Auth.user(applicationContext) == null) return Result.success()
        return try {
            CloudBackup.backup(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val NAME = "daily-backup"
    }
}
