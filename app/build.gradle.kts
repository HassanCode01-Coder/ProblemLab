import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
}

// Lecture des clés dans localproperties (ce fichier ne doit jamais être envoyé sur GitHub)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

// Retire les espaces et les guillemets éventuels autour de la valeur
fun localProp(name: String): String =
    (localProperties.getProperty(name) ?: "").trim().trim('"').trim()

val supabaseUrl = localProp("SUPABASE_URL")
val supabaseKey = localProp("SUPABASE_KEY")

if (supabaseUrl.isBlank() || supabaseKey.isBlank()) {
    throw GradleException(
        "SUPABASE_URL et SUPABASE_KEY doivent être définis dans le fichier local.properties, à la racine du projet."
    )
}

android {
    namespace = "com.hassandev.problemlab"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hassandev.problemlab"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Les clés sont injectées dans BuildConfig au moment de la compilation
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_KEY", "\"$supabaseKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform("io.github.jan-tennert.supabase:bom:3.6.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-okhttp:3.1.3")
    implementation("br.com.devsrsouza.compose.icons:font-awesome:1.1.1")
    implementation("androidx.compose.material:material-icons-core")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.github.jan-tennert.supabase:functions-kt")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}