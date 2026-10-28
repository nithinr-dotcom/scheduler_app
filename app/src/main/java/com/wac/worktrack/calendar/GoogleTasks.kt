package com.wac.worktrack.calendar

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset

/**
 * Read-only Google Tasks (the "Tasks"/"Reminders" you create inside Google Calendar).
 * The API only exposes the due *date*, so these are shown as all-day items.
 */
object GoogleTasks {
    private const val SCOPE = "https://www.googleapis.com/auth/tasks.readonly"
    private const val API = "https://tasks.googleapis.com/tasks/v1"
    private const val CACHE_MILLIS = 5 * 60_000L

    sealed interface Auth {
        data class Token(val value: String) : Auth
        data class NeedsConsent(val intent: PendingIntent) : Auth
        data class Failed(val message: String) : Auth
    }

    private val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    private var cache: Pair<Long, List<CalendarItem>>? = null

    suspend fun authorize(context: Context): Auth = try {
        toAuth(Identity.getAuthorizationClient(context).authorize(request).await())
    } catch (e: Exception) {
        Auth.Failed(e.message ?: "Authorization failed")
    }

    /** Result of the consent screen launched from [Auth.NeedsConsent]. */
    fun fromConsentResult(context: Context, data: Intent?): Auth = try {
        toAuth(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    } catch (e: Exception) {
        Auth.Failed(e.message ?: "Authorization failed")
    }

    private fun toAuth(result: AuthorizationResult): Auth {
        val pi = result.pendingIntent
        val token = result.accessToken
        return when {
            result.hasResolution() && pi != null -> Auth.NeedsConsent(pi)
            token != null -> Auth.Token(token)
            else -> Auth.Failed("No access token")
        }
    }

    /** Open tasks due between [from] and [to], plus overdue ones (shown on [from]). Empty on any error. */
    suspend fun due(context: Context, from: Long, to: Long): List<CalendarItem> {
        val all = cached() ?: run {
            val token = (authorize(context) as? Auth.Token)?.value ?: return emptyList()
            runCatching { fetchOpen(token) }.getOrNull()?.also { cache = System.currentTimeMillis() to it }
                ?: return emptyList()
        }
        return all.mapNotNull { item ->
            when {
                item.day in from..to -> item
                item.day < from -> item.copy(title = "${item.title} (overdue)", day = from, key = item.key + "-od$from")
                else -> null
            }
        }
    }

    fun clearCache() {
        cache = null
    }

    private fun cached(): List<CalendarItem>? =
        cache?.takeIf { System.currentTimeMillis() - it.first < CACHE_MILLIS }?.second

    private suspend fun fetchOpen(token: String): List<CalendarItem> = withContext(Dispatchers.IO) {
        val out = mutableListOf<CalendarItem>()
        val lists = get("$API/users/@me/lists?maxResults=100", token).optJSONArray("items")
        for (i in 0 until (lists?.length() ?: 0)) {
            val list = lists!!.getJSONObject(i)
            val listId = URLEncoder.encode(list.getString("id"), "UTF-8")
            val listTitle = list.optString("title", "Google Tasks")
            var pageToken: String? = null
            do {
                val page = get(
                    "$API/lists/$listId/tasks?showCompleted=false&showHidden=false&maxResults=100" +
                        (pageToken?.let { "&pageToken=$it" } ?: ""),
                    token,
                )
                val items = page.optJSONArray("items")
                for (j in 0 until (items?.length() ?: 0)) {
                    val t = items!!.getJSONObject(j)
                    val due = t.optString("due").takeIf { it.isNotBlank() } ?: continue
                    // "due" is midnight UTC of the chosen date.
                    val day = Instant.parse(due).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    out += CalendarItem(
                        key = "t" + t.getString("id"),
                        title = t.optString("title").ifBlank { "(No title)" },
                        day = day, minutes = null, endMinutes = null,
                        kind = CalendarItem.Kind.GOOGLE_TASK, source = listTitle,
                    )
                }
                pageToken = page.optString("nextPageToken").takeIf { it.isNotBlank() }
            } while (pageToken != null)
        }
        out
    }

    private fun get(url: String, token: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            if (conn.responseCode !in 200..299) error("Google Tasks HTTP ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
