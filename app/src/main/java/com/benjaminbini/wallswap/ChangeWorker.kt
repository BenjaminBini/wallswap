package com.benjaminbini.wallswap

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Puts a new photo on the active screen when one is requested, queued or due. */
class ChangeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        return try {
            val active = activeScreen(applicationContext)
            val requested = inputData.getBoolean(KEY_CHANGE_ACTIVE, false)
            if (prefs.isQueued(active)) {
                changeWallpaper(applicationContext, prefs, active, prefs.queuedPhoto(active))
            } else if (requested || Schedule.isDue(prefs, active)) {
                changeWallpaper(applicationContext, prefs, active, chosen = null)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("WallSwap", "Wallpaper change failed", e)
            prefs.lastError = e.message ?: e.javaClass.simpleName
            Result.failure()
        }
    }

    companion object {
        const val KEY_CHANGE_ACTIVE = "change_active"
    }
}

object Schedule {
    private const val PERIODIC_WORK = "periodic-check"
    const val CHECK_NOW_WORK = "check-now"
    private const val CHECK_MINUTES = 15L
    private const val FLEX_MINUTES = 5L

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Checks run every 15 minutes; a screen changes once its interval has elapsed. */
    fun isDue(prefs: Prefs, screen: Screen): Boolean {
        val interval = prefs.intervalMinutes
        if (interval == 0 || !prefs.autoChange(screen)) return false
        val elapsed = System.currentTimeMillis() - prefs.lastChanged(screen)
        return elapsed >= TimeUnit.MINUTES.toMillis(interval - FLEX_MINUTES)
    }

    /** Runs the periodic check while a screen auto-changes or has a queued change. */
    fun update(context: Context) {
        val prefs = Prefs(context)
        val workManager = WorkManager.getInstance(context)
        val auto = prefs.intervalMinutes > 0 && Screen.entries.any(prefs::autoChange)
        if (!auto && Screen.entries.none(prefs::isQueued)) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<ChangeWorker>(
            CHECK_MINUTES, TimeUnit.MINUTES, FLEX_MINUTES, TimeUnit.MINUTES,
        ).setConstraints(network).build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * Queues [photo], or a random photo when null, for [screen]: set right away if the screen is
     * in use, otherwise as soon as it is.
     */
    fun requestChange(context: Context, screen: Screen, photo: Photo?) {
        Prefs(context).queue(screen, photo)
        update(context)
        checkNow(context, changeActive = false)
    }

    /** New photo on whichever screen is in use. */
    fun changeActiveNow(context: Context) = checkNow(context, changeActive = true)

    private fun checkNow(context: Context, changeActive: Boolean) {
        val request = OneTimeWorkRequestBuilder<ChangeWorker>()
            .setInputData(Data.Builder().putBoolean(ChangeWorker.KEY_CHANGE_ACTIVE, changeActive).build())
            .setConstraints(network)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(CHECK_NOW_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
