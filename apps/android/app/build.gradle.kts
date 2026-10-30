plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "app.radvpn"
 compileSdk = 35
 buildFeatures { buildConfig = true }
 defaultConfig { applicationId = "app.radvpn"; minSdk = 26; targetSdk = 35; versionCode = 30003; versionName = "3.0.3"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"; manifestPlaceholders["appLabel"] = "RADVPN" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 signingConfigs {
  getByName("debug") {
   val keyPath = System.getenv("RADVPN_ANDROID_KEYSTORE_PATH")
   if (!keyPath.isNullOrBlank()) {
    storeFile = file(keyPath)
    storePassword = System.getenv("RADVPN_ANDROID_STORE_PASSWORD")
    keyAlias = System.getenv("RADVPN_ANDROID_KEY_ALIAS")
    keyPassword = System.getenv("RADVPN_ANDROID_KEY_PASSWORD")
   }
  }
 }
 val releaseKey = System.getenv("RADVPN_ANDROID_KEYSTORE_PATH")
 if (!releaseKey.isNullOrBlank()) {
  signingConfigs.create("persistentRelease") {
   storeFile = file(releaseKey)
   storePassword = System.getenv("RADVPN_ANDROID_STORE_PASSWORD")
   keyAlias = System.getenv("RADVPN_ANDROID_KEY_ALIAS")
   keyPassword = System.getenv("RADVPN_ANDROID_KEY_PASSWORD")
  }
 }
 buildTypes {
  create("testing") {
   initWith(getByName("debug"))
   applicationIdSuffix = ".testing"
   versionNameSuffix = "-testing"
   manifestPlaceholders["appLabel"] = "RADVPN Test 3.0.3"
   // Separate install only: never a replacement key or identity for app.radvpn.
   signingConfig = signingConfigs.getByName("debug")
   matchingFallbacks += listOf("debug")
  }
  getByName("release") {
  isMinifyEnabled = false
  if (!releaseKey.isNullOrBlank()) signingConfig = signingConfigs.getByName("persistentRelease")
 } }
 gradle.taskGraph.whenReady {
  if (allTasks.any { it.name in listOf("assembleRelease", "bundleRelease", "packageRelease") } && releaseKey.isNullOrBlank())
   throw GradleException("Original compatible RADVPN signing keystore is required; no replacement key is generated.")
 }
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
