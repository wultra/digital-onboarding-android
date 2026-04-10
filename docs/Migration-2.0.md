# Migration from 1.3.x to 2.0.x

This guide covers public API changes between `1.3.x` and `2.0.x`.

SDK `2.0.x` requires `enrollment-onboarding-server` `2.1.0+`.

## Breaking Changes

1. Document types are now backend strings (`typealias DocumentType = String`).

```kotlin
val selectedTypes: List<DocumentType> = listOf("ID_CARD", "PASSPORT")
```

Use values returned by `ConfigurationService.getConfiguration(processType)`.

2. Verification status now returns `StatusResult` with state + server metadata.

```kotlin
verificationService.status { result ->
    result.onSuccess { statusResult ->
        val state = statusResult.state
        val processId = statusResult.serverData.processId
        val processType = statusResult.serverData.processType
    }
}
```

3. Consent flow was updated.

- `consentGet(...)` was renamed to `getConsent(...)`.
- `consentApprove(...)` was replaced by `start(consentApprovedByUser = ...)`.
- `VerificationState.CONSENT` was removed.
- Use `VerificationStateIntroData.consentRequired` to decide whether consent has to be resolved.

4. OTP helpers moved from services to `DemoEndpoints`.

```kotlin
DemoEndpoints.getOTP(activationService) { /* ... */ }
DemoEndpoints.getOTP(verificationService) { /* ... */ }
```

## Additions

- Added `ConfigurationService` and configuration response models.
- Added `processType` argument to `ActivationService.start(...)`.
- `ActivationService.activate(...)` now accepts optional OTP (`String?`).
- Added `ActivationService.createActivationBuilder(...)` for advanced activation workflows.
- Added `VerificationState.ACTIVATION_FINISH` and `VerificationService.finishActivation(...)`.
- Added `VerificationStateOtpData.otpResendPeriodInSeconds`.
- Added `VerificationStateEndstateData.rejectReason`.
- Added `ProcessingItem.ONBOARDING_APPROVAL`.

## Checklist

- Replace enum-style document type usage with backend string values.
- Update verification status handling to consume `StatusResult.state` and `StatusResult.serverData`.
- Replace old consent methods with `getConsent(...)` and `start(ConsentResponse...)`.
- Move demo OTP retrieval to `DemoEndpoints.getOTP(...)`.
- Handle `ACTIVATION_FINISH`, `rejectReason`, and OTP resend cooldown in state handling.
