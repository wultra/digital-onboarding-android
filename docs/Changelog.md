# Changelog

## 3.0.0 (TBA)

- **⚠️ BREAKING**: `VerificationStateOtpData` now carries only `remainingAttempts`.
- `ConfigurationResponseData` now includes optional `otpResendPeriodSeconds` (`null` on older backends that do not provide the field yet).
- Fixed R8 minification ([#76](https://github.com/wultra/digital-onboarding-android/issues/76)).

## 2.0.0 (Apr, 2026)

- This release requires `enrollment-onboarding-server` version `2.1.0` or higher.
- Fixed: Document scan flow no longer proceeds to processing when additional documents are still selected locally.
- `SDKInitRequestDataAttributes` now contains `platform` and `origin` properties (mainly to support BlinkID SDK).
- Refactored document upload to use the new v2 API.
- `ConfigurationService` allows to fetch Wultra Digital Onboarding configuration from the server.
- Added support for `processType` and `activationCode` in `ActivationService`.
- `ActivationService.activate(...)` now accepts optional OTP.
- Support for optional Identity Consent:
  - `VerificationStateIntroData` contains `consentRequired` flag.
  - `VerificationService.start` handles mandatory and optional consent (replaces `consentApprove`).
  - `consentGet(...)` renamed to `getConsent(...)`.
  - Removed `VerificationState.CONSENT` state (and `VerificationStateConsentData` class); consent is resolved via `start` method.
- `getOTP` methods in `ActivationService` and `VerificationService` were moved to `DemoEndpoints` helper class.
- Added `ProcessingItem.ONBOARDING_APPROVAL`, which signals onboarding approval wait state.
- Added `ACTIVATION_FINISH` state in `VerificationState`.
  - When this status is reached, activation should be finalized by calling `VerificationService.finishActivation(...)`.
- Replaced `createPowerAuthActivationData(...)` with `createActivationBuilder(...)`, used internally by `activate(...)` and for advanced workflows.
- `DocumentType` changed from an enum to a typealias of `String` to better accommodate dynamic configuration of scanned documents.
- Updated configuration response objects with `useTemporaryActivation` and document `country`.
- Added `processType` to verification status response data and exposed it from `VerificationService`.
- `VerificationStateOtpData` now carries `otpResendPeriodInSeconds` alongside `remainingAttempts`.
- `VerificationStateEndstateData` now carries optional `rejectReason` with server-provided rejection details.
- `VerificationService.status` now returns `VerificationService.StatusResult` containing `state` and `serverData` (`processId`, `processType`).
- `VerificationService.documentsSubmit` now automatically resolves missing `originalDocumentId` values from cached scan process data (temporary workaround until backend auto-resolution is available).
- Demo `getOTP(...)` now accepts endpoint strategy via `GetOTPEndpointStrategy`.

## 1.3.0 (Oct, 2024)

- Using PowerAuth SDK `1.9.x`
- Minor fixes

## 1.2.0 (May, 2024)

- Changed name of the log class to the `WDOLogger`
- Added listener to the log class

## 1.1.1 (Mar 6, 2024)

- Minor fixes
- Improved error handling
- Introduced `WDOResult` that provides strong concrete error types

## 1.1.0 (Mar 4, 2024)

- Using PowerAuth SDK `1.8.x`

## 1.0.3 (Mar 4, 2024)

- Documentation improvements
- Logs improvements

## 1.0.2 (Nov 10, 2023)

- Fixed error handling in verifying OTP endpoint

## 1.0.1 (Oct 3, 2023)

- Fixed bug where in some cases callbacks were not called

## 1.0.0 (Aug 8, 2023)

Initial release.
