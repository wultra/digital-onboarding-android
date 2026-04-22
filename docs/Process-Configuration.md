# Process Configuration

With `ConfigurationService` you can retrieve the configuration of the onboarding process from the server.
The configuration contains information about which steps are required to be performed during the onboarding process and which document types are supported or required for scanning.

Use returned document `type` values in `VerificationService.documentsSetSelectedTypes(...)`.

## Retrieving the configuration

To retrieve the configuration, create an instance of `ConfigurationService` and call the `getConfiguration` method with the process type identifier.

Example:

```kotlin
lateinit var service: ConfigurationService // configured instance
val processType = "PROCESS_TYPE_IDENTIFIER"

service.getConfiguration(processType) { result ->
    result.onSuccess { data ->
        // Access configuration data using responseObject
        // e.g.: data.responseObject.otpResendPeriodSeconds
    }.onFailure { error ->
        // Handle failure
    }
}
```

## Configuration response

```kotlin
/** Configuration response */
data class ConfigurationResponseData(
    /** Is the onboarding process enabled */
    val enabled: Boolean,
    /** Is OTP required for the first part - identification/activation. */
    val otpForIdentification: Boolean,
    /** Is OTP required for the second part - identity verification. */
    val otpForIdentityVerification: Boolean,
    /** Is the onboarding process configured with temporary activation that should be exchanged for the permanent one. */
    val useTemporaryActivation: Boolean,
    /** Time in seconds user needs to wait between OTP resend calls. Null when backend doesn't provide the value. */
    val otpResendPeriodSeconds: Int?,
    /** Documents required for identity verification. */
    val documents: ConfigurationDocuments
)

/** Documents required for identity verification */
data class ConfigurationDocuments(
    /** Number of total required documents */
    val totalRequiredDocumentsCount: Int,
    /** Groups of documents */
    val groups: List<ConfigurationDocumentGroup>
)

/** Configuration for a document */
class ConfigurationDocumentGroup(
    /** Number of required documents in the group */
    val requiredDocumentsCount: Int,
    /** Documents in the group */
    val items: List<ConfigurationDocument>
)

/** Group of documents in the configuration */
data class ConfigurationDocument(
    /** Type of the document */
    val type: String,
    /** Number of sides the document has */
    val sideCount: Int,
    /** Country of origin of the document as ISO 3166-1 alpha-3 code */
    val country: String?
)

```

`type` is a backend-defined string such as `ID_CARD` or `PASSPORT`.

Use `otpResendPeriodSeconds` to drive the cooldown for OTP resend in activation or verification UI.

## Read next
- [Device Activation](Device-Activation.md)
