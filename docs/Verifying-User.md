# Verifying the User

If your `PowerAuthSDK` instance was activated with the `ActivationService`, it will be in the state that needs additional verification. Without such verification, it won't be able to properly sign requests.

Additional verification requires the user to scan their face and provide documents such as an ID card or passport.

## When is the verification needed?

Verification is needed if the `activationFlags` in the `io.getlime.security.powerauth.core.ActivationStatus` contains `VERIFICATION_PENDING` or `VERIFICATION_IN_PROGRESS` value.

<!-- begin box info -->
These values can be accessed via the extension methods `verificationPending()` and `verificationInProgress()` or just simply `needVerification()` if one of them is true.
<!-- end -->

Example:

```kotlin
val powerAuth: PowerAuthSDK // configured and activated PowerAuth instance

powerAuth.fetchActivationStatusWithCallback(
    appContext,
    object : IActivationStatusListener {
        override fun onActivationStatusSucceed(status: ActivationStatus) {
            // note that `needVerification()` method is an extension
            // from the `WultraDigitalOnboarding` space
            if (status.needVerification()) {
                // navigate to the verification flow 
                // and call `VerificationService.status`
            } else {
                // handle PA status
            }
        }

        override fun onActivationStatusFailed(t: Throwable) {
            // handle error
        }
    }
)
```

## Starting a re-verification (Re-KYC)

<!-- begin box warning -->
Requires **PA Enrollment Onboarding Server `2.2.3`** or newer. Calling `startReVerification` against an older PA Enrollment Onboarding Server will fail.
<!-- end -->

In some cases, you might require the user to repeat identity verification even though the `PowerAuthSDK` instance is already fully activated and does not need any verification (`needVerification()` is `false`). Deciding *when* a Re-KYC should be triggered is entirely up to the app/backend logic (a business rule, a server-driven prompt, or a dedicated backend call outside of this SDK).

To start such a re-verification (Re-KYC), call `VerificationService.startReVerification`. Unlike `ActivationService.start`, this call does not create a new PowerAuth activation - it reuses the current one. `additionalData` is optional and analogous to `credentials` passed to `start`.

`startReVerification` automatically fetches the verification status right after a successful start (same as calling `status()` would).

<!-- begin box warning -->
Once the Re-KYC process is started, the verification flow proceeds the same way as a regular verification. Note that `ActivationStatus.needVerification()` may not flip to `true` until the subsequent `VerificationService.start(...)` call initializes the identity verification on the backend. Once it becomes `true`, it stays `true` until the process finishes. By default the server signals this via the standard `VERIFICATION_IN_PROGRESS` flag, unless the backend is configured to use an additional/dedicated custom flag instead - `reKycInProgress()` covers the common `RE_KYC_IN_PROGRESS` convention, otherwise inspect `activationFlags()` directly for your backend's specific flag name. Use `needVerification()` as the reliable general check to resume the flow.
<!-- end -->

Example:

```kotlin
val verificationService: VerificationService // configured verification service

verificationService.startReVerification { result ->
    result.onSuccess { statusResult ->
        // use statusResult.state to display the next state (usually INTRO)
    }.onFailure { fail ->
        // handle error
    }
}

// ...later, e.g. after an app restart, resume with the regular verification flow
// (without calling startReVerification again) once needVerification() is true:
if (powerAuthStatus.needVerification()) {
    // navigate to the verification flow and call `VerificationService.status`
}
```

## Example app flow

<p align="center"><img src="images/verification-mockup.png" alt="Example verification flow" width="100%" /></p>

<!-- begin box info -->
This mockup shows a __happy user flow__ of an example setup. Your usage may vary.   
The final flow (which screens come after another) is controlled by the backend.
<!-- end -->

## Server driven flow

- The screen that should be displayed is driven by the state on the server "session".   
- At the beginning of the verification process, you will call the status which will tell you what to display to the user and which function to call next.
- Each API call returns a result and a next screen to display.
- This repeats until the process is finished or an "endstate state" is presented which terminates the process.

## Possible state values

The service can return the state via the `status()` method or various other calls. The result of the status is a class that inherits from `VerificationStateData` and represents a screen in the verification flow.

### Intro

| `VerificationState` value | `VerificationStateData` class                                             |  
|---------------------------|---------------------------------------------------------------------------|
| `INTRO`                   | `VerificationStateIntroData` with `val consentRequired: Boolean` property | 

Show the verification introduction screen where the user can start the activation.

If `consentRequired` is `true`, the next step should be calling the `getConsent()`. If it is `false`, 
you can skip the consent and call `start(ConsentResponse.NOT_REQUIRED)` to start the verification process.

### Select documents to scan

| `VerificationState`        | `VerificationStateData` class                |  
|----------------------------|----------------------------------------------|
| `DOCUMENTS_TO_SCAN_SELECT` | `VerificationStateDocumentsToScanSelectData` | 

Show document selection to the user. Which documents are available and how many can the user select is up to your backend configuration.  
The next step should be calling the `documentsSetSelectedTypes`.

### Scan document

| `VerificationState` | `VerificationStateData` class                                                                        |  
|---------------------|------------------------------------------------------------------------------------------------------|
| `SCAN_DOCUMENT`     | `VerificationStateScanDocumentData` with `val scanDocumentProcess: VerificationScanProcess` property | 

User should scan documents - display UI for the user to scan all necessary documents.

Which document should be scanned can be obtained via the `scanDocumentProcess` property.

The next step should be calling the `documentsSubmit`.

### Processing

| `VerificationState` | `VerificationStateData` class                                                        |  
|---------------------|--------------------------------------------------------------------------------------|
| `PROCESSING`        | `VerificationStateProcessingData` with `val processingItem: ProcessingItem` property | 

The system is processing data - show loading with text hint from provided `ProcessingItem`.

The next step should be calling the `status`.

### Presence check

| `VerificationState` | `VerificationStateData` class        |  
|---------------------|--------------------------------------|
| `PRESENCE_CHECK`    | `VerificationStatePresenceCheckData` | 

The user should be presented with a presence check.  

Presence check is handled by third-party SDK based on the project setup.  

The next step should be calling the `presenceCheckInit` to start the check and `presenceCheckSubmit` to mark it finished.
Note that these methods won't change the status and it's up to the app to handle the process of the presence check.

### OTP

| `VerificationState` | `VerificationStateData` class                                          |  
|---------------------|------------------------------------------------------------------------|
| `OTP`               | `VerificationStateOtpData` with `val remainingAttempts: Int?` and `val otpResendPeriodInSeconds: Int?` properties | 

Show enter OTP screen with the resend button. `remainingAttempts` contains the number of OTP attempts a user can still try and `otpResendPeriodInSeconds` contains resend cooldown in seconds.

The next step should be calling the `verifyOTP` with the user-entered OTP. The OTP is usually SMS or email.

### Finish Activation

| `VerificationState` | `VerificationStateData` class           |  
|---------------------|-----------------------------------------|
| `ACTIVATION_FINISH` | `VerificationStateActivationFinishData` | 

Show "finish activation" with PIN prompt screen.

The next step should be calling the `finishActivation` with user entered PIN.

### Failed

| `VerificationState` | `VerificationStateData` class |  
|---------------------|-------------------------------|
| `FAILED`            | `VerificationStateFailedData` | 

Verification failed and can be restarted

The next step should be calling the `restartVerification` or `cancelWholeProcess` based on the user's decision if he wants to try it again or cancel the process.

### Endstate

| `VerificationState` | `VerificationStateData` class                                                      |  
|---------------------|------------------------------------------------------------------------------------|
| `ENDSTATE`          | `VerificationStateEndstateData` with `val endstateReason: EndstateReason` and `val rejectReason: String?` properties | 

Verification is canceled and the user needs to start again with a new PowerAuth activation. To explain why that happened, you can show additional information to the user based on the `endstateReason` property.

When `endstateReason` is `REJECTED`, the `rejectReason` field may contain server-provided rejection details.

The next step should be calling the `PowerAuthSDK.removeActivationLocal()` and starting activation from scratch.

### Success

| `VerificationState` | `VerificationStateData` class  |  
|---------------------|--------------------------------|
| `SUCCESS`           | `VerificationStateSuccessData` | 

Verification was successfully ended. Continue into your app's regular "log-in" scenario.

## Creating an instance

To create an instance you will need a `PowerAuthSDK` instance that is __already activated__, application `Context`, and configured `OkHttpClient`.

<!-- begin box info -->
[Documentation for `PowerAuthSDK`](https://github.com/wultra/powerauth-mobile-sdk)
<!-- end -->


Example:

```kotlin
val powerAuth = PowerAuthSDK
    .Builder(...)
    .build(appContext)
            
val verificationService = VerificationService(
    "https://server.my/path/", // identityserver URL
    OkHttpClient.Builder().build(), // okhttp client that performs networking
    appContext, // application context
    powerAuth
)
```

## Getting the verification status

When entering the verification flow for the first time (for example fresh app start), you need to retrieve the state of the verification.

The same needs to be done after some operation fails and it's not sure what is the next step in the verification process.

Most verification functions return the result and also the state for your convenience of "what next".

Getting the state directly:

```kotlin
lateinit var verification: VerificationService // configured instance
verification.status { result ->
    result.onSuccess { statusResult ->
        // statusResult.state: VerificationStateData - navigate to the expected screen
        // statusResult.serverData.processId: String - unique ID of this verification process
        // statusResult.serverData.processType: String - configured type of this verification process
    }.onFailure { 
        if (it.state != null) {
            // show expected screen based on the state
        } else {
            // navigate to error screen and show the error in `it.reason`
        }
    }
}
```

## Getting the user consent text

When the state is `INTRO` and `consentRequired` is `true`, the first step in the flow is to get the context text for the user to approve.

```kotlin
lateinit var verification: VerificationService // configured instance
verification.getConsent { result ->
    result.onSuccess { consentText ->
        // show consent text to user
    }.onFailure {
        // navigate to error screen and show the error in `it.reason`
    }
}
```

## Starting the verification (resolving the consent)

When `status()` returns `INTRO` and `consentRequired` is `true`, display the consent text to the user and let the user approve or reject it.

If the user __rejects the consent__, call `start(ConsentResponse.DECLINED)`.

If the user chooses to accept the consent, call `start(ConsentResponse.APPROVED)` function. If successful, `DOCUMENTS_TO_SCAN_SELECT` state will be returned.

If the `INTRO` state reported `consentRequired` as `false`, call `start(ConsentResponse.NOT_REQUIRED)`.

```kotlin
lateinit var verification: VerificationService // configured instance

// example when user approved the consent
verification.start(ConsentResponse.APPROVED) { result ->
    result.onSuccess { stateData ->
        if (stateData.state is VerificationStateDocumentsToScanSelectData) {
            // handle consent state
        }
    }.onFailure {
        if (it.state != null) {
            // show expected screen based on the state
        } else {
            // navigate to error screen and show the error in `it.reason`
        }
    }
}
```

## Set document types to scan

After the user approves the consent, present a document selector for documents which will be scanned. The number and types of documents (or other rules like 1 type required) are completely dependent on your backend system integration, frontend SDK does not provide any hint for this configuration.

For example, your system might require a national ID and one additional document like a driver's license, passport, or any other government-issued personal document.

```kotlin
lateinit var verification: VerificationService // configured instance
val list = listOf("ID_CARD", "PASSPORT") // selected by user from UI or hardcoded
verification.documentsSetSelectedTypes(list) { result ->
    result.onSuccess { stateData ->
        if (stateData.state is VerificationStateScanDocumentData) {
            // handle consent state
        }
    }.onFailure {
        if (it.state != null) {
            // show expected screen based on the state
        } else {
            // navigate to error screen and show the error in `it.reason`
        }
    }
}
```

## Configuring the "Document Scan SDK"

<!-- begin box info -->
This step does not move the state of the process but is a "stand-alone" API call.
<!-- end -->

Since the document scanning itself is not provided by this library but by a 3rd party library, some of them need a server-side initialization.

If your chosen scanning SDK requires such a step, use this function to retrieve necessary data from the server.

ZenID integration example:

```kotlin
lateinit var verification: VerificationService // configured instance

if (ZenId.get().security.isAuthorized) {
    // the instance is already authorized
    return null
}

val token = ZenId.get().security.challengeToken

verification.documentsInitSDK(token) { result ->
    result.onSuccess { responseToken ->
        val success = ZenId.get().security.authorize(appContext, responseToken)
    }.onFailure {
        // handle error
    }
}
```

## Scanning a document

When the state of the process is `SCAN_DOCUMENT` with the `VerificationScanProcess` parameter, you need to present a document scan UI to the user. This UI needs
to guide through the scanning process - scanning one document after another and both sides (if the document requires so).

The whole UI and document scanning process is up to you and the 3rd party library you choose to use.

<!-- begin box warning -->
This step is the most complicated in the process as you need to integrate this SDK, another document-scanning SDK, and integrate your server-side expected logic. To
make sure everything goes as smoothly as possible, ask your project management to provide you with a detailed description/document of the required scenarios and expected documents
for your implementation.
<!-- end -->

## Uploading a document

When a document is scanned (both sides when required), it needs to be uploaded to the server.

<!-- begin box warning -->
__Images of the document should not be bigger than hundreds of kilobytes. Files that are too big will take longer time to upload and process on the server.__
<!-- end -->

To upload a document, use `documentsSubmit` function. Each side of a document is a single `DocumentFile` instance.

Example:

```kotlin
lateinit var verification: VerificationService // configured instance
val passportToUpload = DocumentFile(
    byteArrayOf(), // raw image data from the document scanning library/photo camera
    null, // signature only when supported by the backend
    "PASSPORT",
    DocumentSide.FRONT, // passport has only front side
    null // use only when re-uploading the file (for example when first upload was rejected because of a blur)
)

verification.documentsSubmit(listOf(passportToUpload), { /* progress callback */ }) { result ->
    result.onSuccess {
        // state here will be "processing" - telling you that the file is being processed on the server
    }.onFailure {
        // handle error
    }
}
```

### `DocumentFile`

```kotlin
class DocumentFile {
    /** Image to be uploaded. */
    var data: ByteArray
    /** Image signature. */
    var dataSignature: String?
    /** Type of the document. */
    val type: DocumentType
    /** Side of the document. Use `DocumentSide.FRONT` for one-sided documents. */
    val side: DocumentSide
    /** In case of re-upload */
    val originalDocumentId: String?

    /**
     * Image that can be sent to the backend for Identity Verification
     *
     * @param scannedDocument Document which we're uploading.
     * @param data Image raw data.
     * @param dataSignature Signature of the image data. Optional, `null` by default.
     * @param side Side of the document which the image captures.
     */
    constructor(scannedDocument: ScannedDocument, data: ByteArray, dataSignature: String? = null, side: DocumentSide)

    /**
     * Image that can be sent to the backend for Identity Verification
     *
     * @param data Image data to be uploaded.
     * @param dataSignature Image signature.
     * @param type Type of the document.
     * @param side Side of the document. Use `DocumentSide.FRONT` for one-sided documents.
     * @param originalDocumentId Original document ID in case of a re-upload.
     */
    constructor(data: ByteArray, dataSignature: String? = null, type: DocumentType, side: DocumentSide, originalDocumentId: String? = null)
}
```

<!-- begin box info -->
To create an instance of the `DocumentFile`, you can use `ScannedDocument.createFileForUpload`. The `ScannedDocument` is returned in the process status as a "next document to scan".
<!-- end -->

## Presence check

To verify that the user is present in front of the phone, a presence check is required. This is suggested by the `presenceCheck` state.

When this state is obtained, the following steps need to be done:

1. Call `presenceCheckInit` to initialize the presence check on the server. This call returns a dictionary of necessary data for the presence-check library to initialize.
2. Make the presence check by the third-party library
3. After the presence check is finished, call `presenceCheckSubmit` to tell the server you finished the process on the device.

## Verify OTP

After the presence check is finished, the user will receive an SMS/email OTP and the `OTP` state will be reported. When this state is received, prompt the user for the OTP and verify it via `verifyOTP` method.

The `OTP` state also contains the number of possible OTP attempts. When attempts are depleted, the error state is returned.

Example:

```kotlin
lateinit var verification: VerificationService // configured instance

val userOTP = "123456"

verification.verifyOTP(userOTP) { result ->
    result.onSuccess {
        // React to a new state returned in the result
    }.onFailure {
        // handle error
        // in case that the OTP cannot be filled again (too many attempts or other), the `it.reason` will be type of `OTPFailedException`
    }
}
```

## Finalizing the verification (optional)

When the state `ACTIVATION_FINISH` is received, prompt the user for a PIN code.

This PIN code is then used to activate a new `PowerAuthSDK` object that will be used for signing requests.

Once the new `PowerAuthSDK` instance is activated, the verification process is finished, and the user can proceed to the main app flow *with the new `PowerAuthSDK` instance*.

<!-- begin box info -->
If the user's PIN used for the original activation should be equal to the one used for the new activation, then set the `validatePassword` parameter to `true` in the `finishActivation` call.
<!-- end -->

Example:

```kotlin
val verification: VerificationService // configured instance
val newPaInstance: PowerAuthSDK // new PowerAuth instance to be activated and then used in the app
val password = Password("1234") // user entered PIN code
verification.finishActivation(newPaInstance, "my-new-activation-name", password, true, null) { result ->
    result.onSuccess {
        // When here, the newPaInstance is activated and ready to use (to sign requests and so on).
        // The original PowerAuthSDK instance used for the verification will be in the `REMOVED` state and the `verification` instance can't be used anymore.
    }.onFailure {
        // handle error
    }
}
```

## Success state

When a whole verification is finished, you will receive the `SUCCESS` state. Show a success screen and navigate the user to a common activated flow.

At the same time, the verification flags from the PowerAuth status are removed.

## Failed state

When the process fails, a `FAILED` state is returned. This means that the current verification process has failed and the user can restart it (by calling the `restartVerification` function) and start again (by showing the intro).

## Endstate state

When the activation is no longer able to be verified (for example did several failed attempts or took too long to finish), the `ENDSTATE` state is returned. In this state there's nothing the user can do to continue. `cancelWholeProcess` shall be called and `removeActivationLocal` should be called on the PowerAuthSDK object. After that, the user should be put into the "fresh install state".

## Errors

All functions that can return an exception are of type `ActivationService.Fail` that contains `cause: ApiError` - more about these `ApiError` errors can be found in [the networking library documentation](https://github.com/wultra/networking-android).

There are 3 custom exceptions that this service is adding:

| Custom `Exception` in the `reason` | Description                                                      |  
|------------------------------------|------------------------------------------------------------------|
| `ActivationNotActiveException`     | PowerAuth instance cannot start the activation.                  | 
| `ActivationMissingStatusException` | Verification status needs to be fetched first.                   | 
| `OTPFailedException`               | OTP failed to verify. Refresh status to retrieve current status. | 

## Read next

- [Language Configuration](Language-Configuration.md)