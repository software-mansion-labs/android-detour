plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.vanniktech.maven.publish)
}

group = "com.swmansion.detour"
version = "1.0.0"

android {
    namespace = "com.swmansion.detour"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.installreferrer)

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    pom {
        name = "Detour Android SDK"
        description = "SDK for handling deferred deep links on Android."
        url = "https://github.com/software-mansion-labs/android-detour"
        licenses {
            license {
                name = "The MIT License"
                url = "http://www.opensource.org/licenses/mit-license.php"
            }
        }
        scm {
            connection = "scm:git:git://github.com/software-mansion-labs/android-detour.git"
            developerConnection = "scm:git:ssh://github.com/software-mansion-labs/android-detour.git"
            url = "https://github.com/software-mansion-labs/android-detour"
        }
        developers {
            developer {
                id = "swmansion"
                name = "Software Mansion"
                email = "contact@godetour.dev"
            }
        }
    }
}