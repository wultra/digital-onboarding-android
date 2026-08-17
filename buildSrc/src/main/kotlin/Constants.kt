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

import org.gradle.api.JavaVersion

object Constants {
    object BuildScript {
        // These have to be defined in buildSrc/gradle.properties
        // It's the only way to make them available in buildSrc/build.gradle.kts
        val androidPluginVersion: String by System.getProperties()
        val kotlinVersion: String by System.getProperties()
        val dokkaVersion: String by System.getProperties()
    }

    object Java {
        val sourceCompatibility = JavaVersion.VERSION_17
        val targetCompatibility = JavaVersion.VERSION_17
        const val kotlinJvmTarget = "17"
    }

    object Android {
        const val compileSdkVersion = 36
        const val minSdkVersion = 28
        const val buildToolsVersion = "36.0.0"
    }
}
