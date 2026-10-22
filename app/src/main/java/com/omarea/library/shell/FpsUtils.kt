package com.omarea.library.shell

import com.omarea.common.shell.KeepShell
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.shell.RootFile.fileExists
import java.util.Locale

class FpsUtils(private val keepShell: KeepShell = KeepShellPublic.secondaryKeepShell) {
    private var fpsFilePath: String? = null
    private var lastTime = -1L
    private var lastFrames = -1
    private var currentFpsValue = 0.0f

    val currentFps: String?
        get() {
            // 1. المحاولة عبر SurfaceFlinger (الطريقة الأكثر دقة)
            try {
                val result = keepShell.doCmdSync("dumpsys SurfaceFlinger --latency").trim()
                if (result.isNotEmpty() && !result.contains("Permission denied")) {
                    val lines = result.split("\n")
                    if (lines.size > 2) {
                        // السطر الأول هو الـ refresh rate، الأسطر التالية هي أوقات الإطارات
                        val timestamps = mutableListOf<Long>()
                        for (i in 1 until lines.size) {
                            val parts = lines[i].trim().split(Regex("\\s+"))
                            if (parts.size >= 2) {
                                val ts = parts[1].toLongOrNull() ?: 0L
                                if (ts > 0) timestamps.add(ts)
                            }
                        }
                        if (timestamps.size > 1) {
                            val timeDiff = timestamps.last() - timestamps.first()
                            if (timeDiff > 0) {
                                val fps = (timestamps.size - 1).toFloat() * 1_000_000_000f / timeDiff
                                if (fps > 0 && fps < 240) {
                                    currentFpsValue = fps
                                    return String.format(Locale.US, "%.1f", fps)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {}

            // 2. المحاولة عبر مسارات الكرنل
            if (fpsFilePath == null) {
                val paths = arrayOf(
                    "/sys/class/drm/sde-crtc-0/measured_fps",
                    "/sys/class/graphics/fb0/measured_fps",
                    "/sys/class/drm/card0-DSI-1/measured_fps"
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
                    val res = keepShell.doCmdSync("cat $fpsFilePath").trim()
                    if (res.isNotEmpty() && res.toFloatOrNull() != null) {
                        currentFpsValue = res.toFloat()
                        return String.format(Locale.US, "%.1f", currentFpsValue)
                    }
                } catch (e: Exception) {}
            }

            // 3. الطريقة القديمة كحل أخير (SurfaceFlinger 1013)
            try {
                val result = keepShell.doCmdSync("service call SurfaceFlinger 1013").trim()
                if (result != "error" && result.contains("Parcel")) {
                    val index = result.indexOf("(") + 1
                    if (index > 0 && result.length > index + 8) {
                        val frames = Integer.parseInt(result.substring(index, index + 8), 16)
                        val time = System.currentTimeMillis()
                        if (lastTime > 0 && lastFrames > 0 && frames >= lastFrames) {
                            val fps = (frames - lastFrames) * 1000.0f / (time - lastTime)
                            currentFpsValue = fps
                            lastFrames = frames
                            lastTime = time
                            return String.format(Locale.US, "%.1f", fps)
                        }
                        lastFrames = frames
                        lastTime = time
                    }
                }
            } catch (e: Exception) {}

            return null
        }

    val fps: Float
        get() {
            currentFps
            return currentFpsValue
        }
}
