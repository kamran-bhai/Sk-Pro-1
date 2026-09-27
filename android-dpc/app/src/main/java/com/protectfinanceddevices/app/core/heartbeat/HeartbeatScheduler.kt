package com.protectfinanceddevices.app.core.heartbeat

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Manages WorkManager lifecycle for periodic background heartbeat synchronization.
 *
 * Adheres strictly to Android power and background execution standards:
 * - Minimum 15-minute periodic interval (OS hard limit)
 * - Required network connection constraint
 * - Exponential backoff retry policy (30 seconds initial)
 * - Avoids continuous battery-draining wake locks
 */
object HeartbeatScheduler {

    private const val TAG = "HeartbeatScheduler"
    const val WORK_NAME_PERIODIC_HEARTBEAT = "com.protectfinanceddevices.work.HEARTBEAT"
    const val TAG_HEARTBEAT_WORK = "DEVICE_HEARTBEAT_WORK"

    /**
     * Schedules the periodic heartbeat task via Android WorkManager.
     */
    fun schedulePeriodicHeartbeat(context: Context) {
        Log.i(TAG, "Registering periodic WorkManager heartbeat schedule (15m interval)...")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<DeviceHeartbeatWorker>(
            15, TimeUnit.MINUTES, // Android minimum periodic interval
            5, TimeUnit.MINUTES   // Flex window
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .addTag(TAG_HEARTBEAT_WORK)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME_PERIODIC_HEARTBEAT,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )

        Log.i(TAG, "Periodic WorkManager heartbeat registered successfully.")
    }

    /**
     * Enqueues an immediate, one-time sync request (e.g., right after onboarding completes or upon user request).
     */
    fun enqueueImmediateHeartbeat(context: Context) {
        Log.i(TAG, "Triggering immediate one-time heartbeat execution...")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<DeviceHeartbeatWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(TAG_HEARTBEAT_WORK)
            .build()

        WorkManager.getInstance(context).enqueue(oneTimeWorkRequest)
    }

    /**
     * Cancels any pending heartbeat jobs (e.g. if device is un-enrolled).
     */
    fun cancelHeartbeat(context: Context) {
        Log.i(TAG, "Cancelling device heartbeat WorkManager schedule.")
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC_HEARTBEAT)
    }
}
