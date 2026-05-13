package dev.ishubhamsingh.shelfly

import android.app.Application
import androidx.appfunctions.service.AppFunctionConfiguration
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.ishubhamsingh.shelfly.appfunctions.ShelflyAppFunctions
import dev.ishubhamsingh.shelfly.notifications.ExpiryWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class ShelflyApp : Application(),
    AppFunctionConfiguration.Provider,
    Configuration.Provider {

    @Inject lateinit var appFunctions: ShelflyAppFunctions
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val appFunctionConfiguration: AppFunctionConfiguration
        get() = AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(ShelflyAppFunctions::class.java) { appFunctions }
            .build()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleExpiryWorker()
    }

    private fun scheduleExpiryWorker() {
        val request = PeriodicWorkRequestBuilder<ExpiryWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ExpiryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
