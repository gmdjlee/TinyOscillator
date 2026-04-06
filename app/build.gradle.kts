plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.tinyoscillator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tinyoscillator"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        checkReleaseBuilds = false
    }
}

tasks.withType<Test> {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    jvmArgs("-Xmx1g", "-XX:+UseParallelGC")
    outputs.cacheIf { true }
    // JVM 포크 재사용: 200개 테스트마다 새 JVM (메모리 누수 방지 + 기동 비용 절감)
    setForkEvery(200)
}

// ── 빠른 테스트 태스크: @Category(FastTest::class) 클래스만 실행 ──
afterEvaluate {
    tasks.findByName("testDebugUnitTest")?.let { baseTest ->
        tasks.register<Test>("testFast") {
            description = "Run only @Category(FastTest::class) tests (pure JVM, < 30s)"
            group = "verification"
            testClassesDirs = (baseTest as Test).testClassesDirs
            classpath = baseTest.classpath
            useJUnit {
                includeCategories("com.tinyoscillator.core.testing.annotations.FastTest")
            }
            jvmArgs("-Xmx1g", "-XX:+UseParallelGC")
            maxParallelForks = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            setForkEvery(200)
            outputs.cacheIf { true }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // === Network (OkHttp + Jsoup) ===
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")

    // === JSON (Kotlinx Serialization) ===
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // === Coroutines ===
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // === Android / Compose ===
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-text-google-fonts")

    // === Navigation ===
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // === Room ===
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // === Hilt ===
    implementation("com.google.dagger:hilt-android:2.54")
    ksp("com.google.dagger:hilt-compiler:2.54")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // === WorkManager ===
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // === kotlin_krx ===
    implementation("com.krxkt:krxkt:1.0.0-SNAPSHOT")

    // === MPAndroidChart ===
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // === Security (Encrypted Storage) ===
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // === DataStore (Preferences) ===
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // === Browser (Custom Tabs fallback for WebView) ===
    implementation("androidx.browser:browser:1.8.0")

    // === Logging ===
    implementation("com.jakewharton.timber:timber:5.0.1")

    // === Debug ===
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    // === Testing ===
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.5.0")
}
