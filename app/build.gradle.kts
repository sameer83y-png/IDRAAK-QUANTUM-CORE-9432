plugins {
alias(libs.plugins.android.application)
alias(libs.plugins.kotlin.android)
alias(libs.plugins.kotlin.compose)
id("com.google.gms.google-services")
}

android {
namespace = "com.idraak.quantum"
compileSdk = 34

defaultConfig {
    applicationId = "com.idraak.quantum"
    minSdk = 28
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"
}

buildFeatures {
    compose = true
}

compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlinOptions {
    jvmTarget = "17"
}

}

dependencies {
implementation(libs.androidx.core.ktx)
implementation(libs.androidx.lifecycle.runtime.ktx)
implementation(libs.androidx.activity.compose)

implementation(platform(libs.androidx.compose.bom))
implementation(libs.androidx.compose.ui)
implementation(libs.androidx.compose.ui.graphics)
implementation(libs.androidx.compose.ui.tooling.preview)
implementation(libs.androidx.compose.material3)

implementation(libs.androidx.navigation.compose)

// Firebase AI Logic - Gemini
implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
implementation("com.google.firebase:firebase-ai")
implementation("com.google.firebase:firebase-appcheck-debug")

}
