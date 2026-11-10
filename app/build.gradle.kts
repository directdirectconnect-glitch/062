plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.example.english1000"; compileSdk = 35
 defaultConfig { applicationId = "com.example.english1000"; minSdk = 26; targetSdk = 35; versionCode = 98; versionName = "0.9.8-preview" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation("junit:junit:4.13.2") }
