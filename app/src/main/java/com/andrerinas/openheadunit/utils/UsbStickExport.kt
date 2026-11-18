package com.andrerinas.openheadunit.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

/**
 * Puts an exported log on a USB stick plugged into the car, when there is one. Exporting only to
 * Download/ left nothing on the stick, and the car has no file manager to move it there.
 *
 * The stick's root first (MG4Link runs as the system user, which may write there), then the
 * app's own folder on the stick, which any app may write to without a permission.
 */
object UsbStickExport {
    private const val TAG = "UsbStickExport"

    /** Mounted removable volumes, as directories. */
    fun stickRoots(context: Context): List<File> {
        if (Build.VERSION.SDK_INT < 24) return emptyList()
        val sm = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        return runCatching {
            sm.storageVolumes.filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }.mapNotNull { v ->
                if (Build.VERSION.SDK_INT >= 30) v.directory
                else runCatching { v.javaClass.getMethod("getPathFile").invoke(v) as? File }.getOrNull()
                    ?: runCatching { (v.javaClass.getMethod("getPath").invoke(v) as? String)?.let(::File) }.getOrNull()
            }
        }.getOrElse { emptyList() }
    }

    /** The app's own folders on removable volumes (writable without any permission). */
    private fun appFoldersOnSticks(context: Context): List<File> =
        context.getExternalFilesDirs(null).filterNotNull().filter { dir ->
            runCatching { Environment.isExternalStorageRemovable(dir) }.getOrDefault(false)
        }

    /** Copies [source] to the first stick that takes it; null when there is no stick or none can. */
    fun copyToStick(context: Context, source: File, name: String): File? {
        val candidates = stickRoots(context) + appFoldersOnSticks(context)
        for (dir in candidates) {
            val target = File(dir, name)
            val ok = runCatching { source.copyTo(target, overwrite = true) }
            if (ok.isSuccess) {
                AppLog.i("$TAG: log copied to ${target.absolutePath}")
                return target
            }
            AppLog.w("$TAG: ${dir.absolutePath} refused the log (${ok.exceptionOrNull()?.message})")
        }
        if (candidates.isEmpty()) AppLog.i("$TAG: no USB stick mounted")
        return null
    }
}
