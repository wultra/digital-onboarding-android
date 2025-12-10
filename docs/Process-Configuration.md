# Process Configuration

With `ConfigurationService` you can retrieve the configuration of the onboarding process from the server.
The configuration contains information about which steps are required to be performed during the onboarding process and which document types are supported or required for scanning.

## Retrieving the configuration

To retrieve the configuration, create an instance of `ConfigurationService` and call the `getConfiguration` method with the process type identifier.

Example:

```kotlin
lateinit var service: ConfigurationService // configured instance
val processType = "PROCESS_TYPE_IDENTIFIER"

service.getConfiguration(processType) { result ->
    result.onSuccess { data ->
        // Access configuration data using responseObject
        // e.g.: data.responseObject.otpForIdentification
    }.onFailure { error ->
        // Handle failure
    }
}
```

## Configuration response

```kotlin
class ConfigurationResponseData(
    /** Is the onboarding process enabled */
    val enabled: Boolean,
    /** Is OTP required for the first part - identification/activation. */
    val otpForIdentification: Boolean,
    /** Is OTP required for the second part - identity verification. */
    val otpForIdentityVerification: Boolean,
    /** Documents required for identity verification. */
    val documents: ConfigurationDocumentsData
)

class ConfigurationDocumentsData(
    /** Number of required documents */
    val requiredDocumentsCount: Int,
    /** List of documents */
    val items: List<ConfigurationDocumentData>
)

class ConfigurationDocumentData(
    /** Type of the document */
    val type: String,
    /** Is the document mandatory? */
    val mandatory: Boolean,
    /** Number of sides the document has */
    val sideCount: Int
)

```

## Read next
- [Device Activation](Device-Activation.md)