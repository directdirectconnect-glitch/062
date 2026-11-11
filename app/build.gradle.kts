plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.example.english1000"; compileSdk = 35
 defaultConfig { applicationId = "com.example.english1000"; minSdk = 26; targetSdk = 35; versionCode = 98; versionName = "0.9.8-preview" }
 // Separate fresh QA package; never overwrites the installed .s20test or Fold app.
 buildTypes {
   create("s20qa") {
     initWith(getByName("debug"))
     applicationIdSuffix = ".s20qa"
     versionNameSuffix = "-s20qa"
     isDebuggable = true
     signingConfig = signingConfigs.getByName("debug")
   }
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    testImplementation("junit:junit:4.13.2")
}
