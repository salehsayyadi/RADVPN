plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "app.radvpn"
 compileSdk = 35
 defaultConfig { applicationId = "app.radvpn"; minSdk = 26; targetSdk = 35; versionCode = 20000; versionName = "2.0.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildTypes { getByName("release") { isMinifyEnabled = false } }
 sourceSets.getByName("main").assets.srcDir("../../../packages/localization")
 packaging { jniLibs { useLegacyPackaging = false } }
}
dependencies {
 implementation(files("libs/radengine.aar"))
 implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
