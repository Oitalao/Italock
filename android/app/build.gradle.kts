plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "br.italo.italock"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.italo.italock"
        minSdk = 23
        targetSdk = 36
        versionCode = 13
        versionName = "1.8.0"
    }

    // Chave oficial do Ítalock: fica fora do repositório. Sem ela, o build usa a chave de depuração do Android
    // (o APK funciona, mas não instala por cima do APK oficial).
    val chaveOficial = rootProject.file("italock.keystore")
    signingConfigs {
        if (chaveOficial.exists()) create("pessoal") {
            storeFile = chaveOficial
            storePassword = providers.gradleProperty("ITALOCK_STORE_PASS").getOrElse("")
            keyAlias = "italock"
            keyPassword = providers.gradleProperty("ITALOCK_STORE_PASS").getOrElse("")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (chaveOficial.exists()) signingConfigs.getByName("pessoal") else signingConfigs.getByName("debug")
        }
        debug { if (chaveOficial.exists()) signingConfig = signingConfigs.getByName("pessoal") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
    lint {
        // Rodar no Android 6+ é requisito: qualquer API nova sem checagem de versão quebra o build.
        error += "NewApi"
        abortOnError = true
        checkReleaseBuilds = true
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    // Tema AppCompat nas telas que pedem biometria (Android 6–8.0 abre janela AppCompat).
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    implementation("androidx.lifecycle:lifecycle-service:2.9.1")
    // zxing:core 3.4+ usa APIs do Java 8 que só existem a partir do Android 7; a 3.3.0 roda no Android 6.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = false }
    implementation("com.google.zxing:core:3.3.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Relógio Wear OS (Data Layer). Sem Google Play Services no aparelho, o recurso some (WearBridge.available).
    implementation("com.google.android.gms:play-services-wearable:20.0.1")

    testImplementation("junit:junit:4.13.2")
    // org.json de verdade nos testes (o do android.jar dos testes é só casca).
    testImplementation("org.json:json:20240303")
}
