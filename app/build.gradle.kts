plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.example.english1000"; compileSdk = 35
 defaultConfig { applicationId = "com.example.english1000"; minSdk = 26; targetSdk = 35; versionCode = 124; versionName = "1.0.24" }
 buildTypes { getByName("debug") { applicationIdSuffix = ".offlinevoskqa"; versionNameSuffix = "-offline-vosk-qa" } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    testImplementation("junit:junit:4.13.2")
}

val verifyExcelDictionary by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verify that Android dictionary assets match the Excel master."
    val projectRoot = rootProject.projectDir
    val workbook = projectRoot.resolve("data/EnglishWords-5126-priority.xlsx")
    val importer = projectRoot.resolve("scripts/import_excel_dictionary.py")
    val androidAssets = projectDir.resolve("src/main/assets")
    val defaultPython = if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3"
    commandLine(
        providers.gradleProperty("englishWordsPython").getOrElse(defaultPython),
        importer.absolutePath, workbook.absolutePath,
        "--assets", androidAssets.absolutePath,
        "--output", androidAssets.absolutePath, "--check",
    )
}

tasks.named("preBuild") {
    dependsOn(verifyExcelDictionary)
}
