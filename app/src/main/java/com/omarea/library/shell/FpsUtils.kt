package com.omarea.library.shell

import com.omarea.common.shell.KeepShell
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.shell.RootFile.fileExists

class FpsUtils(private val keepShell: KeepShell = KeepShellPublic.secondaryKeepShell) {
    private var fpsFilePath: String? = null
    private var useSurfaceFlinger = true
    private var currentFpsValue = 0.0f

    val currentFps: String?
        get() {
            // 1. المحاولة عبر SurfaceFlinger --latency (الأدق لأندرويد 9+)
            if (useSurfaceFlinger) {
                try {
                    val result = keepShell.doCmdSync("su -c 'dumpsys SurfaceFlinger --latency'").trim()
                    if (result.isNotEmpty() && !result.contains("Permission denied")) {
                        val lines = result.split("\n")
                        var frameCount = 0
                        var firstTime = 0L
                        var lastTime = 0L
                        // السطر الأول هو معدل التحديث (مثلاً 16ms لشاشة 60Hz)
                        for (i in 1 until lines.size) {
                            val parts = lines[i].trim().split(Regex("\\s+"))
                            if (parts.size >= 2) {
                                val vsync = parts[1].toLongOrNull() ?: 0L
                                if (vsync > 0) {
                                    if (firstTime == 0L) firstTime = vsync
                                    lastTime = vsync
                                    frameCount++
                                }
                            }
                        }
                        if (frameCount > 1 && lastTime > firstTime) {
                            val timeDiffNanos = lastTime - firstTime
                            val fps = (frameCount - 1) * 1_000_000_000.0f / timeDiffNanos
                            if (fps > 0 && fps < 240) {
                                currentFpsValue = fps
                                return String.format("%.1f", fps)
                            }
                        }
                    }
                } catch (e: Exception) {
                    useSurfaceFlinger = false
                }
            }

            // 2. المحاولة عبر مسارات الكرنل (Kernel)
            if (fpsFilePath == null) {
                val paths = arrayOf(
                    "/sys/class/drm/sde-crtc-0/measured_fps",
                    "/sys/class/graphics/fb0/measured_fps"
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
                    if (res.isNotEmpty() && res.toFloatOrNull() != null) {
                        currentFpsValue = res.toFloat()
                        return String.format("%.1f", currentFpsValue)
                    }
                } catch (e: Exception) {}
            }

            return null // إرجاع null لكي تظهر "غير معروف" بدلاً من رقم ثابت خاطئ
        }

    val fps: Float
        get() {
            currentFps
            return currentFpsValue
        }
}
