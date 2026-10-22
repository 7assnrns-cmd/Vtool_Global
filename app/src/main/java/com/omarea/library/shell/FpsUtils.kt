package com.omarea.library.shell

import com.omarea.common.shell.KeepShell
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.shell.RootFile.fileExists
import java.util.Locale

class FpsUtils(private val keepShell: KeepShell = KeepShellPublic.secondaryKeepShell) {
    private var fpsFilePath: String? = null
    private var currentFpsValue = 0.0f

    val currentFps: String?
        get() {
            // 1. القراءة عبر SurfaceFlinger (الأدق)
            try {
                val result = keepShell.doCmdSync("dumpsys SurfaceFlinger --latency").trim()
                if (result.isNotEmpty() && !result.contains("Permission denied")) {
                    val lines = result.split("\n")
                    if (lines.size > 2) {
                        val timestamps = mutableListOf<Long>()
                        // السطر الأول هو الـ refresh rate (نتجاهله)، نأخذ العمود الأول من بقية الأسطر
                        for (i in 1 until lines.size) {
                            val parts = lines[i].trim().split(Regex("\\s+"))
                            if (parts.isNotEmpty()) {
                                val ts = parts[0].toLongOrNull() ?: 0L
                                // تجاهل القيم الفارغة أو القيمة القصوى (INT64_MAX)
                                if (ts > 0 && ts != 9223372036854775807L) {
                                    timestamps.add(ts)
                                }
                            }
                        }
                        if (timestamps.size > 1) {
                            val timeDiffNanos = timestamps.last() - timestamps.first()
                            if (timeDiffNanos > 0) {
                                val fps = (timestamps.size - 1).toFloat() * 1_000_000_000f / timeDiffNanos
                                if (fps > 0 && fps < 240) {
                                    currentFpsValue = fps
                                    return String.format(Locale.US, "%.1f", fps)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {}

            // 2. القراءة من مسارات الكرنل (احتياطي)
            if (fpsFilePath == null) {
                val paths = arrayOf(
                    "/sys/class/drm/sde-crtc-0/measured_fps",
                    "/sys/class/graphics/fb0/measured_fps"
                )
                for (path in paths) {
                    if (fileExists(path)) { fpsFilePath = path; break }
                }
            }
            
            if (!fpsFilePath.isNullOrEmpty()) {
                try {
                    val res = keepShell.doCmdSync("cat $fpsFilePath").trim()
                    if (res.isNotEmpty() && res.toFloatOrNull() != null) {
                        currentFpsValue = res.toFloat()
                        return String.format(Locale.US, "%.1f", currentFpsValue)
                    }
                } catch (e: Exception) {}
            }

            return null // يعرض "غير معروف" بدلاً من 0.0
        }

    val fps: Float
        get() {
            currentFps
            return currentFpsValue
        }
}
