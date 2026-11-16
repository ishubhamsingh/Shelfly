plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied anywhere (AGP 9 has built-in Kotlin). Declared only to pin KGP to the
    // catalog's Kotlin version (AGP 9.1 bundles 2.2.10) so it matches kotlin.compose.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
