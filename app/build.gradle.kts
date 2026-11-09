plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.example.english1000"; compileSdk = 35
 defaultConfig { applicationId = "com.example.english1000"; minSdk = 26; targetSdk = 35; versionCode = 86; versionName = "0.8.6-examples" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
