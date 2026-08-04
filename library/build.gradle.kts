@file:Suppress("UnstableApiUsage")

/*
 * Copyright 2023 Wultra s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 */

plugins {
    id("com.android.library")
    kotlin("android")
    id("org.jetbrains.dokka")
}

apply<com.wultra.plugin.WultraAndroidReleasePlugin>()

android {

    namespace = "com.wultra.android.digitalonboarding"
    compileSdk = Constants.Android.compileSdkVersion
    buildToolsVersion = Constants.Android.buildToolsVersion

    defaultConfig {
        minSdk = Constants.Android.minSdkVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // since Android Gradle Plugin 4.1.0
        // VERSION_CODE and VERSION_NAME are not generated for libraries
        configIntField("VERSION_CODE", 1)
        configStringField("VERSION_NAME", properties["VERSION_NAME"] as String)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("consumer-proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = Constants.Java.sourceCompatibility
        targetCompatibility = Constants.Java.targetCompatibility
        //noinspection WrongGradleMethod
        kotlinOptions {
            jvmTarget = Constants.Java.kotlinJvmTarget
            suppressWarnings = false
        }
    }

    // Custom ktlint script
    tasks.register("ktlint") {
        logger.lifecycle("ktlint")
        exec {
            commandLine = listOf("./../scripts/lint.sh", "--no-error")
        }
    }

    // Make ktlint run before build
    tasks.getByName("preBuild").dependsOn("ktlint")
}

dependencies {
    // Bundled
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${Constants.BuildScript.kotlinVersion}")
    implementation("androidx.annotation:annotation:1.10.0")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.wultra.android.powerauth:powerauth-networking:2.0.0-SNAPSHOT")
    implementation("androidx.security:security-crypto-ktx:1.1.0")

    // Dependencies
    compileOnly("com.wultra.android.powerauth:powerauth-sdk:2.0.0-SNAPSHOT")

    // Unit tests
    testImplementation("junit:junit:4.13.2")

    // Instrumentation tests
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("com.wultra.android.powerauth:powerauth-sdk:2.0.0-SNAPSHOT")
}
