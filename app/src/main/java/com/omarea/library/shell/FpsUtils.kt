package com.omarea.library.shell

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.roundToInt

object FpsUtils {
    // قراءة الفريمات الحديثة باستخدام SurfaceFlinger (تتطلب روت)
    fun getFps(): Float {
        try {
            val process = Runtime.getRuntime().exec("su -c dumpsys SurfaceFlinger --latency")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line = reader.readLine()
            var frameCount = 0
            val startTime = System.currentTimeMillis()
            
            while (line != null && frameCount < 100) {
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size == 3 && parts[1] != "0" && parts[1] != "9223372036854775807") {
                    frameCount++
                }
                line = reader.readLine()
            }
            val endTime = System.currentTimeMillis()
            process.waitFor()
            
            val timeDiff = (endTime - startTime) / 1000.0
            if (timeDiff > 0 && frameCount > 0) {
                // حساب تقريبي للفريمات
                val fps = (frameCount / timeDiff).toFloat()
                return if (fps > 120f) 60f else fps // حماية من القيم الوهمية
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 60.0f // القيمة الافتراضية في حال فشل الروت أو القراءة
    }
}
