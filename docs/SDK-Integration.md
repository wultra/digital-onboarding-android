# SDK Integration

## Requirements

- __Android 9__ (API level 28)+
- [PowerAuth Mobile SDK](https://github.com/wultra/powerauth-mobile-sdk) needs to be available in your project
- [Enrollment Onboarding Server](https://developers.wultra.com/components/enrollment-server/develop/documentation/onboarding/index)

## Maven Central

The library is available in maven-central.

Add `mavenCentral` as a library source if not already added.

```kotlin
repositories {
    mavenCentral()
}
```

Then add a dependency

```kotlin
implementation("com.wultra.android.digitalonboarding:wultra-digital-onboarding:3.0.0")
```

## Read next

- [Process Configuration](Process-Configuration.md)