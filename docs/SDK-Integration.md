# SDK Integration

## Requirements

- __Android 9__ (API level 28)+
- [PowerAuth Mobile SDK](https://github.com/wultra/powerauth-mobile-sdk) needs to be available in your project
- Enrollment onboarding server `2.1.0`+

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
implementation("com.wultra.android.digitalonboarding:wultra-digital-onboarding:3.0.0-SNAPSHOT")
```

## Guaranteed PowerAuth Compatibility

| WDO SDK           | PowerAuth SDK |
|-------------------|---------------|
| `3.0.x`           | `2.0.x`       |
| `2.0.x`           | `1.9.x`       |
| `1.3.x`           | `1.9.x`       |
| `1.1.x` - `1.2.x` | `1.8.x`       |
| `1.0.x`           | `1.7.x`       |

## Read next

- [Process Configuration](Process-Configuration.md)