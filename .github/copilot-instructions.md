# Copilot instructions for digital-onboarding-android

## Build, test, and lint commands

Prerequisites used in this repo:
- Java 17
- Android SDK
- Android emulator for instrumentation tests
- `curl` (used by scripts)

Core commands:
- Full CI-style build: `./gradlew clean build`
- Ktlint (custom script, required in PR checks): `./scripts/lint.sh`
- Android lint: `./gradlew clean :library:lint`
- JVM unit tests: `./scripts/test.sh -type unit`
- Android integration tests: `./scripts/test.sh -type android`
- Android integration tests with injected config JSON: `./scripts/test.sh -type android -config "$TESTS_CONFIG"`

Run a single test:
- Single unit test class: `./gradlew :library:testDebugUnitTest --tests "com.wultra.android.digitalonboarding.VerificationStatusNextStepTest"`
- Single unit test method: `./gradlew :library:testDebugUnitTest --tests "com.wultra.android.digitalonboarding.VerificationStatusNextStepTest.unknownPhaseStatusComboThrows"`
- Single instrumentation test class: `./gradlew :library:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wultra.android.digitalonboarding.IntegrationTests`
- Single instrumentation test method: `./gradlew :library:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wultra.android.digitalonboarding.IntegrationTests#testConfig`

Integration test configuration:
- Tests read `library/src/androidTest/assets/config.json`.
- Start from `library/src/androidTest/assets/config.example.json`.
- CI provides this via `TESTS_CONFIG`, and `scripts/test.sh -type android -config ...` writes it into `assets/config.json`.

## High-level architecture

- This repository is a single Android library module (`:library`) with shared Gradle constants in `buildSrc` (Java 17, minSdk 28, compileSdk 33).
- Public SDK flow is split into three services:
  - `ActivationService`: starts onboarding with backend-defined identification payload, manages onboarding process state, and creates the initial PowerAuth activation.
  - `VerificationService`: drives post-activation identity verification (consent, document upload, presence check, OTP, optional activation exchange).
  - `ConfigurationService`: fetches process configuration (`processType`, required document groups, OTP requirements).
- Networking is separated into API clients under `networking/`:
  - `CustomerOnboardingApi`, `CustomerVerificationApi`, `CustomerConfigurationApi`.
  - These wrap enrollment onboarding server endpoints and apply PowerAuth signing / E2EE scopes.
- Verification flow is server-driven:
  - `VerificationStatusNextStep` translates `(identityVerificationPhase, identityVerificationStatus)` into SDK states.
  - App UI should be driven by returned `VerificationStateData` and follow state-specific next calls.
- Scan progress is persisted across app restarts:
  - `VerificationScanProcess` tracks selected document types and server-side document IDs/statuses.
  - `Storage` (encrypted shared preferences fallback) caches process data per `processId`.
- `DemoEndpoints` contains OTP helpers intended for demo/mock environments, not production onboarding flows.

## Key conventions in this codebase

- `DocumentType` is a backend-provided string (`typealias DocumentType = String`), not an enum. Use values from `ConfigurationService.getConfiguration(processType)`.
- In verification flows, call `VerificationService.status()` first. Many operations require `processId` derived from status and will fail with `ActivationMissingStatusException` otherwise.
- Keep the callback/result style based on `WDOResult` (`onSuccess` / `onFailure`) and preserve state-aware failures (`VerificationService.Fail.state`) when changing error handling.
- When changing verification mapping logic, update related tests together (`VerificationStatusNextStepTest`, `DocumentActionTest`, `VerificationScanProcessTest`).
- `library/build.gradle.kts` runs a local `ktlint` task during `preBuild` with `--no-error`; do not treat `build` as a lint gate. Use `./scripts/lint.sh` for strict linting.
- `.editorconfig` intentionally relaxes several ktlint rules (for example annotation formatting and trailing commas) to keep networking model objects compact; follow existing style in `networking/model/*`.
- Release metadata convention: version is in `library/gradle.properties` (`VERSION_NAME`), and release notes are maintained in `docs/Changelog.md`.
- Contribution flow convention from `.github/CONTRIBUTING.md`: PRs target `develop`, and branch names follow `issues/<issue-number>-<short-description>`.
