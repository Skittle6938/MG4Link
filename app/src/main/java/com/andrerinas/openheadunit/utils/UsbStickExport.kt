package com.andrerinas.openheadunit.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

/**
 * Puts exported logs on a USB stick plugged into the car, when there is one. Exporting only to
 * Download/ left nothing on the stick, and the car has no file manager to move it there.
 *
 * Tried in order: the stick's root (the system user may write there with
 * WRITE_MEDIA_STORAGE), the same through /mnt/media_rw, then the app's own folder on the stick,
 * which any app may write to without a permission.
 */
object UsbStickExport {
    private const val TAG = "UsbStickExport"

    /** Mounted removable volumes, as directories. */
    fun stickRoots(context: Context): List<File> {
        if (Build.VERSION.SDK_INT < 24) return emptyList()
        val sm = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        return runCatching {
            sm.storageVolumes.filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }.flatMap { v ->
                val path = if (Build.VERSION.SDK_INT >= 30) v.directory
                else runCatching { v.javaClass.getMethod("getPathFile").invoke(v) as? File }.getOrNull()
                    ?: runCatching { (v.javaClass.getMethod("getPath").invoke(v) as? String)?.let(::File) }.getOrNull()
                listOfNotNull(path, v.uuid?.let { File("/mnt/media_rw/$it") })
            }
        }.getOrElse { emptyList() }
    }

    /** The app's own folders on removable volumes (writable without any permission). */
    private fun appFoldersOnSticks(context: Context): List<File> =
        context.getExternalFilesDirs(null).filterNotNull().filter { dir ->
            runCatching { Environment.isExternalStorageRemovable(dir) }.getOrDefault(false)
        }

    /**
     * Copies [files] (name on the stick to source) into the first stick folder that takes them.
     * Returns that folder, or null when there is no stick or none could be written.
     */
    fun copyToStick(context: Context, files: List<Pair<String, File>>): File? {
        val roots = stickRoots(context)
        // The stick's root first, where the user looks for them; the app's folder only as a fallback.
        val candidates = roots + appFoldersOnSticks(context)
        if (candidates.isEmpty()) {
            AppLog.i("$TAG: no USB stick mounted")
            return null
        }
        for (dir in candidates) {
            val ok = runCatching {
                if (!dir.isDirectory && !dir.mkdirs()) error("cannot create the folder")
                for ((name, source) in files) source.copyTo(File(dir, name), overwrite = true)
            }
            if (ok.isSuccess) {
                AppLog.i("$TAG: ${files.size} log(s) copied to ${dir.absolutePath}")
                return dir
            }
            AppLog.w("$TAG: ${dir.absolutePath} refused the logs (${ok.exceptionOrNull()?.message})")
        }
        return null
    }
}
