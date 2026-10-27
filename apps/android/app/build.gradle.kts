plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "app.radvpn"
 compileSdk = 35
 buildFeatures { buildConfig = true }
 defaultConfig { applicationId = "app.radvpn"; minSdk = 26; targetSdk = 35; versionCode = 20002; versionName = "2.0.1"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildTypes { getByName("release") { isMinifyEnabled = false } }
 sourceSets.getByName("main").assets.srcDir("../../../packages/localization")
 packaging { jniLibs { useLegacyPackaging = false } }
}
dependencies {
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("junit:junit:4.13.2")
 implementation(files("libs/radengine.aar"))
 implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}

apply(from = "notices.gradle.kts")
