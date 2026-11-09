package dev.plume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app self-update for this fork. The release source is the fork's own GitHub Releases page:
 * every release ships two fixed-name assets — latest.json ({versionCode, versionName}) and
 * mzilo-latest.apk. "releases/latest/download/..." follows a redirect straight to the asset,
 * so there is no GitHub API call, no rate limit and no token.
 *
 * Android 12 TV shows the system install confirmation when the session commits; the user
 * presses OK once and the app restarts into the new version. Nothing is silent by design.
 */
object Updater {
    /** Owner repo of the published releases. */
    const val BASE = "https://github.com/alf533-real/Plume_Launcher/releases/latest/download"

    const val ACTION_RESULT = "dev.plume.INSTALL_RESULT"

    /** A newer release as (versionCode, versionName), or null when up to date / unreachable. */
    fun check(currentCode: Long): Pair<Long, String>? = try {
        val json = http("$BASE/latest.json") ?: return null
        val o = JSONObject(json)
        val code = o.optLong("versionCode", 0)
        val name = o.optString("versionName", "")
        if (code > currentCode && name.isNotEmpty()) code to name else null
    } catch (e: Exception) {
        android.util.Log.w("Updater", "check failed", e)
        null
    }

    /** Downloads the latest APK into [file]. Returns false on any failure. */
    fun download(file: File): Boolean = try {
        val c = URL("$BASE/mzilo-latest.apk").openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 60000
        c.instanceFollowRedirects = true
        val ok = c.responseCode < 300
        if (ok) file.outputStream().use { out -> c.inputStream.use { it.copyTo(out) } }
        c.disconnect()
        // The apk is well over 100 KB; anything smaller is an error page, not a build.
        ok && file.length() > 100_000
    } catch (e: Exception) {
        android.util.Log.w("Updater", "download failed", e)
        file.delete()
        false
    }

    /**
     * Queues the downloaded APK with the system PackageInstaller. The confirmation dialog is the
     * platform's own; [UpdateResultReceiver] reports how it ended (the process dies on success).
     */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val sessionId = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        installer.openSession(sessionId).use { session ->
            session.openWrite("mzilo.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val statusReceiver = android.app.PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(ACTION_RESULT).setPackage(context.packageName),
                android.app.PendingIntent.FLAG_MUTABLE,
            )
            session.commit(statusReceiver.intentSender)
            android.util.Log.w("Updater", "committed session " + sessionId)
        }
    }

    private fun http(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 20000
        c.instanceFollowRedirects = true
        if (c.responseCode < 300) c.inputStream.use { it.readBytes().decodeToString() } else null
    } catch (e: Exception) {
        null
    }
}

/** System install-session verdict. Success kills this process mid-broadcast, so keep it light. */
class UpdateResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        android.util.Log.w("Updater", "install status " + status)
        when (status) {
            // The installer is ready but wants the user's OK: it hands us the confirm dialog as an
            // intent we must launch ourselves. Without this the update silently never asks.
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirm)
                    } catch (e: Exception) {
                        android.util.Log.w("Updater", "confirm launch failed", e)
                    }
                }
            }
            PackageInstaller.STATUS_FAILURE_BLOCKED, PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_INVALID, PackageInstaller.STATUS_FAILURE_CONFLICT ->
                android.widget.Toast.makeText(context, context.getString(R.string.update_install_failed), android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
