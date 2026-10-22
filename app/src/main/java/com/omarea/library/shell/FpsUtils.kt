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
            // 1. المحاولة عبر مسارات الكرنل (Kernel Paths)
            if (fpsFilePath == null) {
                val paths = arrayOf(
                    "/sys/class/drm/sde-crtc-0/measured_fps",
                    "/sys/class/drm/sde-crtc-1/measured_fps",
                    "/sys/devices/virtual/graphics/fb0/measured_fps"
                )
                for (path in paths) {
                    if (fileExists(path)) {
                        fpsFilePath = path
                        break
                    }
                }
            }

            if (!fpsFilePath.isNullOrEmpty()) {
                try {
                    val res = keepShell.doCmdSync("su -c 'cat $fpsFilePath'").trim()
                    val fps = res.toFloatOrNull()
                    if (fps != null && fps > 0) {
                        currentFpsValue = fps
                        return String.format(Locale.US, "%.1f", fps)
                    }
                } catch (e: Exception) {}
            }

            // 2. المحاولة عبر SurfaceFlinger --latency (الطريقة الأدق)
            try {
                // استخدام أمر الروت لقراءة البيانات
                val result = keepShell.doCmdSync("su -c 'dumpsys SurfaceFlinger --latency'").trim()
                if (result.isNotEmpty() && !result.contains("Permission denied")) {
                    // فصل السطور
                    val lines = result.split("\n").filter { it.isNotBlank() }
                    if (lines.size > 2) {
                        val timestamps = mutableListOf<Long>()
                        // السطر الأول هو معدل التحديث (نتجاهله)، نأخذ العمود الثاني من بقية الأسطر
                        for (i in 1 until lines.size) {
                            val parts = lines[i].trim().split(Regex("\\s+"))
                            if (parts.size >= 2) {
                                // القيمة في العمود الثاني هي actualPresentTime
                                val ts = parts[1].toLongOrNull() ?: 0L
                                if (ts > 0 && ts != 9223372036854775807L) {
                                    timestamps.add(ts)
                                }
                            }
                        }
                        
                        if (timestamps.size > 1) {
                            val first = timestamps.first()
                            val last = timestamps.last()
                            val durationSec = (last - first).toDouble() / 1_000_000_000.0
                            
                            if (durationSec > 0) {
                                // معادلة ChatGPT: (frameCount - 1) / durationSec
                                val fps = ((timestamps.size - 1) / durationSec).toFloat()
                                if (fps > 0 && fps < 240) {
                                    currentFpsValue = fps
                                    return String.format(Locale.US, "%.1f", fps)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {}

            // في حال فشل كل شيء، إرجاع null لكي لا يظهر 0.0
            return null 
        }

    val fps: Float
        get() {
            currentFps
            return currentFpsValue
        }
}
