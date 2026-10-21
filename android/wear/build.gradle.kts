plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "br.italo.italock.wear"
    compileSdk = 36

    defaultConfig {
        // Mesmo applicationId do celular: a Data Layer do Wear OS só entrega mensagens entre apps com o mesmo pacote e assinatura.
        applicationId = "br.italo.italock"
        minSdk = 30
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
        abortOnError = true
        checkReleaseBuilds = true
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    // play-services-wearable puxa um Fragment antigo; a API de resultado do Activity exige 1.3 ou mais.
    implementation("androidx.fragment:fragment:1.8.8")
    implementation("androidx.wear.compose:compose-material:1.5.6")
    implementation("androidx.wear.compose:compose-foundation:1.5.6")
    implementation("androidx.wear.tiles:tiles:1.6.2")
    implementation("androidx.wear.protolayout:protolayout:1.4.2")
    implementation("androidx.wear.protolayout:protolayout-material:1.4.2")
    implementation("androidx.concurrent:concurrent-futures:1.3.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
}
