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
 * In-app self-update for this fork. Two release mirrors are tried in order — Gitee first
 * (stable direct downloads inside China), then the GitHub fork (for users who can reach it).
 * Each release ships two fixed-name assets: latest.json ({versionCode, versionName}) and
 * mzilo-latest.apk. No API, no token, no rate limit on either host.
 *
 * Android 12 TV shows the system install confirmation when the session commits; the user
 * presses OK once and the app restarts into the new version. Nothing is silent by design.
 */
object Updater {
    /** Release mirrors, tried in order. Keep both in sync when publishing. */
    val SOURCES = listOf(
        "https://gitee.com/mzilo/mzilo-releases/releases/download/latest",
        "https://github.com/alf533-real/Plume_Launcher/releases/latest/download",
    )

    const val ACTION_RESULT = "dev.plume.INSTALL_RESULT"

    private var base: String? = null // the mirror that answered last time; tried first

    /** A newer release as (versionCode, versionName), or null when up to date / unreachable. */
    fun check(currentCode: Long): Pair<Long, String>? {
        for (src in orderedSources()) {
            val json = http("$src/latest.json") ?: continue
            val o = try { JSONObject(json) } catch (e: Exception) { continue }
            val code = o.optLong("versionCode", 0)
            val name = o.optString("versionName", "")
            if (code > currentCode && name.isNotEmpty()) {
                base = src
                return code to name
            }
            return null // a mirror answered with no newer version: trust it, stop
        }
        return null
    }

    private fun orderedSources(): List<String> =
        base?.let { listOf(it) + (SOURCES - it) } ?: SOURCES

    /** Downloads the latest APK into [file]. Tries every mirror in order. */
    fun download(file: File): Boolean {
        for (src in orderedSources()) {
            val c = try {
                URL("$src/mzilo-latest.apk").openConnection() as HttpURLConnection
            } catch (e: Exception) { continue }
            val ok = try {
                c.connectTimeout = 8000
                c.readTimeout = 120000
                c.instanceFollowRedirects = true
                val good = c.responseCode < 300
                if (good) file.outputStream().use { out -> c.inputStream.use { it.copyTo(out) } }
                // The apk is well over 100 KB; anything smaller is an error page, not a build.
                good && file.length() > 100_000
            } catch (e: Exception) {
                false
            } finally {
                c.disconnect()
            }
            if (ok) {
                base = src
                return true
            }
            file.delete()
        }
        return false
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
        }
    }

    private fun http(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 10000
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
