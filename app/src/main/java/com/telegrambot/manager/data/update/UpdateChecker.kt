package com.telegrambot.manager.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.telegrambot.manager.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * In-app updater against the public GitHub Releases of this project.
 *
 * Flow: check latest release → compare semver with the running version →
 * the UI offers the update → [downloadAndInstall] fetches the APK through
 * the system DownloadManager (survives activity death) and opens the Android
 * package installer via FileProvider once the download finishes.
 */
object UpdateChecker {

    private const val RELEASES_LATEST =
        "https://api.github.com/repos/parkerhell07-eng/TelegramBotManager/releases/latest"

    data class UpdateInfo(
        val version: String,      // e.g. "2.1.0" (no leading v)
        val title: String,
        val notes: String,        // release body (markdown)
        val apkUrl: String,
        val sizeBytes: Long
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Fetches the latest release; null when none exists / network failed. */
    suspend fun latest(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(RELEASES_LATEST)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val tag = json.optString("tag_name", "").removePrefix("v").trim()
                if (tag.isEmpty()) return@withContext null
                val assets = json.optJSONArray("assets") ?: return@withContext null
                var apkUrl = ""
                var size = 0L
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url", "")
                        size = a.optLong("size", 0L)
                        break
                    }
                }
                if (apkUrl.isEmpty()) return@withContext null
                UpdateInfo(
                    version = tag,
                    title = json.optString("name", "").ifBlank { "v$tag" },
                    notes = json.optString("body", ""),
                    apkUrl = apkUrl,
                    sizeBytes = size
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** True when [remote] is strictly newer than the installed version. */
    fun isNewer(remote: String, current: String = BuildConfig.VERSION_NAME): Boolean {
        fun parts(v: String): List<Int> =
            v.removePrefix("v").split("-").first().split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * Convenience used by the home screen: returns an update only when a newer
     * release exists, the user has not skipped that exact version, and the
     * last automatic check is older than [minIntervalMs].
     */
    suspend fun checkDue(
        ctx: Context,
        minIntervalMs: Long = 12 * 60 * 60 * 1000L
    ): UpdateInfo? {
        val settings = com.telegrambot.manager.data.store.SettingsStore.get(ctx)
        val now = System.currentTimeMillis()
        if (now - settings.lastUpdateCheck() < minIntervalMs) return null
        settings.setLastUpdateCheck(now)
        val info = latest() ?: return null
        if (!isNewer(info.version)) return null
        if (settings.skippedVersion() == info.version) return null
        return info
    }

    // ── Download & install ───────────────────────────────────────────────

    fun apkFile(ctx: Context, version: String): File {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(dir, "TelegramBotManager-v$version.apk")
    }

    /** Enqueues the APK download; returns the DownloadManager enqueue id. */
    fun enqueueDownload(ctx: Context, info: UpdateInfo): Long {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val existing = apkFile(ctx, info.version)
        if (existing.exists() && existing.length() == info.sizeBytes && info.sizeBytes > 0) {
            // Fully downloaded earlier (e.g. app restarted mid-flow): install now.
            install(ctx, existing)
            return -1
        }
        val req = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
            setTitle("TelegramBotManager v${info.version}")
            setDescription("در حال دانلود آپدیت…")
            setMimeType("application/vnd.android.package-archive")
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, existing.name)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
        }
        return dm.enqueue(req)
    }

    /** Polls the DownloadManager until the APK is ready, then fires the installer. */
    suspend fun awaitDownloadAndInstall(ctx: Context, id: Long, info: UpdateInfo) {
        if (id == -1L) return // already installed synchronously
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(id)
        var waited = 0L
        while (waited < 15 * 60 * 1000L) {
            dm.query(query)?.use { c ->
                if (c.moveToFirst()) {
                    when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            install(ctx, apkFile(ctx, info.version))
                            return
                        }
                        DownloadManager.STATUS_FAILED -> return
                    }
                }
            }
            delay(2_000)
            waited += 2_000
        }
    }

    /** Opens the Android package installer for [apk]. */
    fun install(ctx: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            // Direct the user to the "install unknown apps" switch once.
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }
}
