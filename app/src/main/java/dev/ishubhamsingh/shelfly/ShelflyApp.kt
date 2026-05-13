package dev.ishubhamsingh.shelfly

import android.app.Application
import androidx.appfunctions.service.AppFunctionConfiguration
import dagger.hilt.android.HiltAndroidApp
import dev.ishubhamsingh.shelfly.appfunctions.ShelflyAppFunctions
import javax.inject.Inject

@HiltAndroidApp
class ShelflyApp : Application(), AppFunctionConfiguration.Provider {

    @Inject lateinit var appFunctions: ShelflyAppFunctions

    override val appFunctionConfiguration: AppFunctionConfiguration
        get() = AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(ShelflyAppFunctions::class.java) { appFunctions }
            .build()
}
