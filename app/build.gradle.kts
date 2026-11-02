plugins { id("com.android.application") }
android {
    namespace = "com.sade.launcher"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.sade.launcher"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
