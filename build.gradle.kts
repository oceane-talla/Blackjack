// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// Keep generated files outside OneDrive, where inherited permissions block cleanup.
// The directory identifier keeps separate checkouts from sharing build outputs.
val checkoutId = Integer.toHexString(rootDir.absolutePath.hashCode())
val generatedFilesDirectory = File(gradle.gradleUserHomeDir, "blackjack-builds/$checkoutId")

allprojects {
    val moduleDirectory = if (path == ":") "root" else path.removePrefix(":").replace(':', '/')
    layout.buildDirectory.set(generatedFilesDirectory.resolve(moduleDirectory))
}
