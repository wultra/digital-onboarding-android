plugins{
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
}

// we cannot load from buildSrc/src/main/kotlin/Versions.kt
// we can only load from gradle.properties
val androidPluginVersion: String by System.getProperties()
val kotlinVersion: String by System.getProperties()

dependencies {
    implementation("com.android.tools.build", "gradle", androidPluginVersion)
    implementation(kotlin("gradle-plugin", kotlinVersion))
}