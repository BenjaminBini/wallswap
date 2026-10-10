package com.benjaminbini.wallswap

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Below this hinge angle the phone is closed and the cover screen is the one in use. */
private const val FOLDED_MAX_DEGREES = 30f

/** Height / width of the Galaxy Z Flip cover screen (948 x 1048). */
private const val COVER_ASPECT = 1048f / 948f

/**
 * The screen in use right now: the cover screen when the phone is folded, the interior one otherwise.
 * Android only delivers sensor events to foreground apps, which [HingeAccessService] makes us.
 */
suspend fun activeScreen(context: Context): Screen {
    val sensors = context.getSystemService(SensorManager::class.java)
    // A phone without a hinge has a single screen: the interior one.
    val hinge = sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) ?: return Screen.INTERIOR
    val degrees = withTimeoutOrNull(3_000) { readOnce(sensors, hinge) }
        ?: error("The hinge sensor did not report the fold state")
    return if (degrees < FOLDED_MAX_DEGREES) Screen.COVER else Screen.INTERIOR
}

/** Pixel size the photo is cropped to for [screen], based on the interior (default) display. */
fun wallpaperSize(context: Context, screen: Screen): Pair<Int, Int> {
    val mode = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).mode
    val width = minOf(mode.physicalWidth, mode.physicalHeight)
    val height = when (screen) {
        Screen.INTERIOR -> maxOf(mode.physicalWidth, mode.physicalHeight)
        Screen.COVER -> (width * COVER_ASPECT).toInt()
    }
    return width to height
}

private suspend fun readOnce(sensors: SensorManager, sensor: Sensor): Float =
    suspendCancellableCoroutine { continuation ->
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                sensors.unregisterListener(this)
                if (continuation.isActive) continuation.resume(event.values[0])
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        // On-change sensors report their current value as soon as a listener registers.
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        continuation.invokeOnCancellation { sensors.unregisterListener(listener) }
    }
