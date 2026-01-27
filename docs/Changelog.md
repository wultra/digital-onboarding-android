# Changelog

## TBA

- `SDKInitRequestDataAttributes` now contains `platform` property (mainly to support BlinkID SDK).
- Refactored document upload to use the new v2 API.
- `ConfigurationService` allows to fetch Wultra Digital Onboarding configuration from the server.
- Added support for `processType` and `activationCode` in `ActivationService`
- Support for optional Identity Consent:
  - `VerificationStateIntroData` contains `consentRequired` flag.
  - `VerificationService.start` handles mandatory and optional consent (replaces `consentApprove`).
  - `consentGet(callback:)` renamed to `getConsent(callback:)`.
  - Removed `VerificationState.CONSENT` state; consent is resolved via `start` method.
- `getOTP` methods in `ActivationService` and `VerificationService` were moved to `DemoEndpoints` helper class.
- added `ONBOARDING_APPROVAL` process type constant, which signals that the onboarding requires approval step.
- new `ACTIVATION_FINISH` state in `VerificationState`
  -  when this status is reached, the activation needs to be finalized by calling `ActivationService.finishActivation` method.

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
