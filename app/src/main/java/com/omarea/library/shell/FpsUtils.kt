package com.omarea.library.shell

import com.omarea.common.shell.KeepShell
import com.omarea.common.shell.KeepShellPublic
import java.util.Locale

class FpsUtils(private val keepShell: KeepShell = KeepShellPublic.secondaryKeepShell) {
    
    private var lastLayer: String? = null
    private var lastLayerCheck = 0L
    private var currentFpsValue = 0.0f

    private val NO_FRAME = 9223372036854775807L
    private val WHITESPACE_REGEX = Regex("\\s+")

    val currentFps: String?
        get() {
            // 1. محاولة قراءة الـ FPS عبر DroidPerf (Latency)
            val latencyFps = tryLatency()
            if (latencyFps > 0) {
                currentFpsValue = latencyFps
                return String.format(Locale.US, "%.1f", latencyFps)
            }

            // 2. محاولة قراءة الـ FPS عبر TimeStats (لأندرويد 14+)
            val timeStatsFps = tryTimeStats()
            if (timeStatsFps > 0) {
                currentFpsValue = timeStatsFps
                return String.format(Locale.US, "%.1f", timeStatsFps)
            }

            // 3. المحاولة عبر مسارات الكرنل (احتياطي)
            val kernelFps = tryKernel()
            if (kernelFps > 0) {
                currentFpsValue = kernelFps
                return String.format(Locale.US, "%.1f", kernelFps)
            }

            return null
        }

    val fps: Float
        get() {
            currentFps
            return currentFpsValue
        }

    // ═══════════════════════════════════════════════════════
    // دوال مساعدة مبنية على كود DroidPerf
    // ═══════════════════════════════════════════════════════

    private fun findLayer(): String? {
        // تحديث اسم الطبقة كل 3 ثواني فقط لتجنب استهلاك المعالج
        val now = System.currentTimeMillis()
        if (lastLayer != null && (now - lastLayerCheck) < 3000) {
            return lastLayer
        }

        try {
            val res = keepShell.doCmdSync("dumpsys SurfaceFlinger --list").trim()
            if (res.isEmpty()) return null

            val lines = res.split("\n").map { it.trim() }.filter { line ->
                line.isNotEmpty() &&
                !line.contains("Background for") &&
                !line.contains("ActivityRecord") &&
                !line.contains("Dim layer") &&
                !line.contains("leash") &&
                !line.contains("SnapshotStartingWindow") &&
                !line.contains("Overlay") &&
                !line.contains("Toast") &&
                !line.contains("InputMethod") &&
                !line.contains("NavigationBar") &&
                !line.contains("StatusBar")
            }

            val blastSurfaceView = lines.lastOrNull { it.contains("SurfaceView") && it.contains("BLAST") }
            val surfaceView = lines.lastOrNull { it.contains("SurfaceView") }
            val blast = lines.lastOrNull { it.contains("BLAST") || it.contains("BBQ") }
            val appWindow = lines.lastOrNull { it.contains("/") }
            
            val chosen = blastSurfaceView ?: surfaceView ?: blast ?: appWindow ?: lines.lastOrNull()
            
            lastLayer = chosen?.trim()
            lastLayerCheck = now
            return lastLayer
        } catch (e: Exception) {
            return null
        }
    }

    private fun tryLatency(): Float {
        val layer = findLayer() ?: return 0f
        try {
            val cmd = "dumpsys SurfaceFlinger --latency '$layer'"
            val result = keepShell.doCmdSync(cmd).trim()
            if (result.isEmpty() || result.contains("Permission denied")) return 0f

            val lines = result.split("\n")
            val presentTimes = ArrayList<Long>()
            
            // السطر الأول هو معدل التحديث، نتخطاه ونبدأ من السطر الثاني
            for (i in 1 until lines.size) {
                val parts = lines[i].trim().split(WHITESPACE_REGEX)
                if (parts.size >= 3) {
                    val actual = parts[1].toLongOrNull() ?: continue
                    if (actual > 0 && actual != NO_FRAME) {
                        presentTimes.add(actual)
                    }
                }
            }

            if (presentTimes.size < 2) return 0f

            val first = presentTimes.minOrNull() ?: return 0f
            val last = presentTimes.maxOrNull() ?: return 0f
            val spanNs = last - first
            if (spanNs <= 0) return 0f

            val spanSec = spanNs / 1_000_000_000.0
            val fps = (presentTimes.size - 1) / spanSec
            
            return if (fps.isFinite() && fps > 0 && fps < 240) fps.toFloat() else 0f
        } catch (e: Exception) {
            return 0f
        }
    }

    private fun tryTimeStats(): Float {
        try {
            // تشغيل تجميع الإحصائيات (إذا لم تكن مفعلة)
            keepShell.doCmdSync("dumpsys SurfaceFlinger --timestats -enable")
            // سحب البيانات ومسحها لبدء جلسة جديدة
            val res = keepShell.doCmdSync("dumpsys SurfaceFlinger --timestats -dump").trim()
            keepShell.doCmdSync("dumpsys SurfaceFlinger --timestats -clear")
            
            if (res.isEmpty()) return 0f

            var currentLayer: String? = null
            val candidates = ArrayList<Float>()

            for (raw in res.split("\n")) {
                val line = raw.trim()
                if (line.startsWith("layerName")) {
                    currentLayer = line.substringAfter('=', "").trim().takeIf { it != "none" }
                } else if (line.startsWith("averageFPS")) {
                    val fps = line.substringAfter('=', "").trim().toFloatOrNull()
                    if (fps != null && fps.isFinite() && fps >= 0.0f && currentLayer != null) {
                        if (currentLayer.contains("SurfaceView") || currentLayer.contains("BLAST") || currentLayer.contains("BBQ")) {
                            candidates.add(fps)
                        }
                    }
                    currentLayer = null
                }
            }

            return candidates.maxOrNull() ?: 0f
        } catch (e: Exception) {
            return 0f
        }
    }

    private fun tryKernel(): Float {
        val paths = arrayOf(
            "/sys/class/drm/sde-crtc-0/measured_fps",
            "/sys/class/drm/sde-crtc-1/measured_fps",
            "/sys/devices/virtual/graphics/fb0/measured_fps"
        )
        for (path in paths) {
            try {
                val res = keepShell.doCmdSync("cat $path 2>/dev/null").trim()
                val fps = res.toFloatOrNull()
                if (fps != null && fps > 0) return fps
            } catch (e: Exception) {}
        }
        return 0f
    }
}
