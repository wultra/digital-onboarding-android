# Test Configuration

Android integration tests load their environment setup from `config.json` in this folder.

The file is parsed by `IntegrationTestConfig.loadEnvironments()` and consumed by `IntegrationTests` / `TestHelper` in `src/androidTest/java/com/wultra/android/digitalonboarding`.

The file format is:

```json
{
  "environments": [
    {
      "name": "smoke",
      "processTypes": ["onboarding"],
      "reKycProcessType": "re-kyc",
      "esUrl": "https://example.com/enrollment-server/",
      "esoUrl": "https://example.com/enrollment-server-onboarding/",
      "mobileConfig": "...",
      "otpMock": "AUTO",
      "servicesMock": true
    }
  ]
}
```

## Properties

- `environments`: List of test environments. Tests run for every environment in this array.
- `name`: Environment name used in test logs.
- `processTypes`: List of onboarding process types. Tests run once for each process type. These process types must exist and be configured on the server for the selected environment.
- `esUrl`: Base URL of the enrollment server used for `PowerAuthSDK` configuration.
- `esoUrl`: Base URL of the enrollment onboarding server used by `ActivationService`, `VerificationService`, and `ConfigurationService`.
- `mobileConfig`: PowerAuth mobile configuration string for the given environment.
- `otpMock`: OTP detail endpoint strategy.
- `servicesMock`: Whether the environment supports mocked downstream services required by the full integration flow.
- `reKycProcessType`: Re-KYC onboarding process type.

## `otpMock` values

- `ESO`: Use SDK demo OTP retrieval (`ActivationService` / `VerificationService` internal demo endpoint calls).
- `AUTO`: Automatically derive mock OTP endpoint from `esoUrl` by replacing `-eso` with `-eso-mock` and appending `/otp/detail`.
- Full URL: Use the provided URL as a custom OTP detail endpoint.

## Notes

- CI can provide this file through the `TESTS_CONFIG` secret.
- Keep real credentials and internal URLs out of commits unless they are meant to be public.