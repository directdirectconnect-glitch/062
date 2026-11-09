plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.example.english1000"; compileSdk = 35
 defaultConfig { applicationId = "com.example.english1000"; minSdk = 26; targetSdk = 35; versionCode = 83; versionName = "0.8.3-fold-preview" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
