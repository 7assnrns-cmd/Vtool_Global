package com.omarea.vtools.utils

import android.view.Choreographer
import kotlin.math.roundToInt

class FpsMonitor(private val onFpsUpdate: (Int) -> Unit) {
    private var lastFrameTimeNanos: Long = 0
    private var frameCount = 0
    private var isRunning = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return
            if (lastFrameTimeNanos == 0L) {
                lastFrameTimeNanos = frameTimeNanos
            }
            val diff = frameTimeNanos - lastFrameTimeNanos
            if (diff > 1_000_000_000L) {
                val fps = (frameCount * 1_000_000_000.0 / diff).roundToInt()
                onFpsUpdate(fps)
                lastFrameTimeNanos = frameTimeNanos
                frameCount = 0
            }
            frameCount++
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (!isRunning) {
            isRunning = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun stop() {
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }
}
