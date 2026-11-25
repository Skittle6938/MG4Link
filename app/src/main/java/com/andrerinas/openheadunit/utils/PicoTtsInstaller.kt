package com.andrerinas.openheadunit.utils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.io.File

/**
 * Installs the Pico TTS speech engine that ships inside MG4Link (assets/picotts/picotts.apk) when
 * the car does not have it or has an older one (#71). The MG4 has no speech engine of its own;
 * Pico is a standard one, so other apps (navigation, MG4 Dash…) can use it too.
 *
 * Silent, as the platform-signed car build installs its own updates. Pico becomes the default
 * engine only when none is set (or the one set is gone): a user's own choice is kept.
 */
object PicoTtsInstaller {
    const val PACKAGE = "com.mg4link.picotts"
    private const val ASSET = "picotts/picotts.apk"
    private const val SETTING_DEFAULT_ENGINE = "tts_default_synth"
    const val ACTION_RESULT = "com.allgo.app.androidauto.PICO_TTS_INSTALL_RESULT"
    /** PackageManager.INSTALL_INTERNAL, hidden. */
    private const val INSTALL_INTERNAL = 0x00000010

    @Volatile private var running = false

    /** What to do with the engine on the car, from the bundled and installed versions. */
    enum class Action { NOTHING, INSTALL, REPLACE }

    /**
     * [installedCode] null when not installed. A newer bundled engine signed with another key
     * (each build machine signs it with its own) is removed first: an update would be refused.
     */
    fun actionFor(bundledCode: Long, installedCode: Long?, sameSigner: Boolean): Action = when {
        installedCode == null -> Action.INSTALL
        bundledCode <= installedCode -> Action.NOTHING
        sameSigner -> Action.INSTALL
        else -> Action.REPLACE
    }

    /** Whether to make Pico the default engine: only when no engine is set or the one set is gone. */
    fun shouldBecomeDefault(current: String?, currentInstalled: Boolean): Boolean =
        current.isNullOrBlank() || !currentInstalled

    fun isInstalled(context: Context): Boolean = installedInfo(context.packageManager, PACKAGE) != null

    /** Checks and installs off the calling thread; does nothing without the install permission. */
    fun ensureInstalled(context: Context) {
        val app = context.applicationContext
        if (app.checkSelfPermission("android.permission.INSTALL_PACKAGES") != PackageManager.PERMISSION_GRANTED) return
        if (running) return
        running = true
        Thread({
            try {
                check(app)
            } catch (e: Throwable) {
                AppLog.w("PicoTtsInstaller: ${e.message}")
            } finally {
                running = false
            }
        }, "PicoTtsInstaller").start()
    }

    private fun check(context: Context) {
        val pm = context.packageManager
        val apk = File(context.cacheDir, "picotts.apk")
        context.assets.open(ASSET).use { input -> apk.outputStream().use { input.copyTo(it) } }
        try {
            val bundled = archiveInfo(pm, apk) ?: run {
                AppLog.w("PicoTtsInstaller: bundled engine unreadable")
                return
            }
            val installed = installedInfo(pm, PACKAGE)
            val action = actionFor(bundled.longVersionCode, installed?.longVersionCode,
                installed != null && signers(bundled) == signers(installed))
            AppLog.i("PicoTtsInstaller: bundled ${bundled.longVersionCode}, installed ${installed?.longVersionCode ?: "none"} -> $action")
            when (action) {
                Action.NOTHING -> setDefaultIfNone(context)
                Action.INSTALL -> install(context, apk)
                Action.REPLACE -> {
                    uninstall(context)
                    install(context, apk)
                }
            }
        } finally {
            apk.delete()
        }
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(PACKAGE)
        params.setSize(apk.length())
        // As OtaInstaller: without INSTALL_INTERNAL (pm install -f) the MG4 refuses a non-system
        // app on the internal storage ("Not allowed to install non-system apps on internal storage").
        params.setInstallLocation(PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY)
        try {
            val flags = PackageInstaller.SessionParams::class.java.getField("installFlags")
            flags.setInt(params, flags.getInt(params) or INSTALL_INTERNAL)
        } catch (e: Throwable) {
            AppLog.w("PicoTtsInstaller: INSTALL_INTERNAL flag: ${e.message}")
        }
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        var session: PackageInstaller.Session? = installer.openSession(sessionId)
        try {
            apk.inputStream().use { input ->
                session!!.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session!!.fsync(out)
                }
            }
            session!!.commit(resultSender(context, sessionId))
            session = null // the commit owns it
        } finally {
            session?.abandon()
        }
    }

    private fun uninstall(context: Context) {
        AppLog.i("PicoTtsInstaller: removing the engine signed with another key")
        context.packageManager.packageInstaller.uninstall(PACKAGE, resultSender(context, 0))
        // The install that follows must not race the removal.
        for (i in 0 until 50) {
            if (installedInfo(context.packageManager, PACKAGE) == null) return
            Thread.sleep(100)
        }
    }

    private fun resultSender(context: Context, requestCode: Int) = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(context, PicoTtsInstallReceiver::class.java).setAction(ACTION_RESULT),
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
    ).intentSender

    fun setDefaultIfNone(context: Context) {
        val resolver = context.contentResolver
        val current = Settings.Secure.getString(resolver, SETTING_DEFAULT_ENGINE)
        val currentInstalled = !current.isNullOrBlank() && installedInfo(context.packageManager, current) != null
        if (!shouldBecomeDefault(current, currentInstalled) || current == PACKAGE) return
        try {
            Settings.Secure.putString(resolver, SETTING_DEFAULT_ENGINE, PACKAGE)
            AppLog.i("PicoTtsInstaller: Pico TTS is now the default speech engine (was ${current ?: "none"})")
        } catch (e: SecurityException) {
            AppLog.w("PicoTtsInstaller: cannot set the default engine: ${e.message}")
        }
    }

    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? =
        pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)

    private fun installedInfo(pm: PackageManager, pkg: String): PackageInfo? = try {
        pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun signers(info: PackageInfo): Set<String> =
        info.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
}

/** Logs the result of a Pico TTS install and makes it the default engine when none is set. */
class PicoTtsInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        AppLog.i("PicoTtsInstaller: result status=$status ${message.orEmpty()}")
        if (status == PackageInstaller.STATUS_SUCCESS) PicoTtsInstaller.setDefaultIfNone(context)
    }
}
