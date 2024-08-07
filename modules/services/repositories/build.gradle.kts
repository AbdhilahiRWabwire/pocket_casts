plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.parcelize)
}

apply(from = "${project.rootDir}/base.gradle")

android {
    namespace = "au.com.shiftyjelly.pocketcasts.repositories"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":modules:services:analytics"))
    implementation(project(":modules:services:crashlogging"))
    implementation(project(":modules:services:deeplink"))
    implementation(project(":modules:services:images"))
    implementation(project(":modules:services:localization"))
    implementation(project(":modules:services:model"))
    implementation(project(":modules:services:preferences"))
    implementation(project(":modules:services:protobuf"))
    implementation(project(":modules:services:servers"))
    implementation(project(":modules:services:utils"))
    testImplementation(project(":modules:services:sharedtest"))
}
