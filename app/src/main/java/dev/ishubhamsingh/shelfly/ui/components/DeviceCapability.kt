package dev.ishubhamsingh.shelfly.ui.components

import android.app.appfunctions.AppFunctionManager as PlatformAppFunctionManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Returns true if the device supports AppFunctions (Android 16+) and the manager is available.
 * Used to gate the "Ask assistant" bottom sheet vs. direct form navigation.
 */
@Composable
fun rememberIsAppFunctionsCapable(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isAppFunctionsCapable() }
}

fun Context.isAppFunctionsCapable(): Boolean {
    if (Build.VERSION.SDK_INT < 36) return false
    return try {
        getSystemService(PlatformAppFunctionManager::class.java) != null
    } catch (_: Exception) {
        false
    }
}

@Composable
fun rememberHasAssistant(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        !Settings.Secure.getString(context.contentResolver, "assistant").isNullOrEmpty()
    }
}
