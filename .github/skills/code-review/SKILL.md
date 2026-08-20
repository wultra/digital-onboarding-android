---
name: code-review
description: Review pull requests in the Digital Onboarding SDK for Android. Use when reviewing Kotlin APIs, onboarding state, identity verification, security, or documentation changes.
---

# Digital Onboarding Android SDK review

This is a strictly read-only review. Review only PR and repository content
already available. Do not run or suggest commands, scripts, builds, tests,
linters, formatters, validation tasks, or Git operations.

## Review contract

Use the already available PR metadata to identify the target and head. The
normal base is `develop`; releases target `release/a.b.x`. Default to approval
and report only a demonstrated defect from the PR with path/line, concrete
impact, and a correction. Do not make formatting, style, CI/workflow,
speculative, or generic test comments. Never post to GitHub without user
approval; prefix any postable content with `🤖`. Only check grammar in public
documentation/Javadoc, and only when the PR base is not a release branch.

Public API/behavior changes require relevant public documentation and
`docs/Changelog.md`. The publication version is `VERSION_NAME` in
`library/gradle.properties`; all release-to-`develop` declarations must be
`0.0.1-dev`. The tracked `scripts/prepare-release.sh` file defines coordinated
release preparation changes.

## Module map and public flow

This single-module Android library is `:library`; Java 17/minSdk 28/build
constants live in `buildSrc`. Public API is in
`library/src/main/java/com/wultra/android/digitalonboarding/`:

* `ActivationService` performs onboarding start/status/cancel and creates the
  initial PowerAuth activation.
* `VerificationService`, `VerificationState`, and
  `VerificationStatusNextStep` drive server-defined identity verification.
* `ConfigurationService` obtains process type, document groups/types and OTP
  configuration.
* `CustomerVerificationScanProcess`, `DocumentFile`, and
  `DocumentPayloadBuilder` retain/select/upload document state.
* `WDOResult`, `PowerAuthExtensions`, `Storage`, and `log/` define callback,
  PowerAuth/persistence, and logging contracts.
* `networking/CustomerOnboardingApi`, `CustomerVerificationApi`, and
  `CustomerConfigurationApi` with `networking/model/` own wire models and
  signed/encrypted endpoint transport.

Public docs are `docs/SDK-Integration.md`, `Device-Activation.md`,
`Process-Configuration.md`, `Verifying-User.md`, `Language-Configuration.md`,
`Logging.md`, migration guides, and `docs/Changelog.md`. JVM tests are under
`library/src/test`; integration tests/config fixtures are under
`library/src/androidTest`.

## State, callbacks, and serialization

The app must call `VerificationService.status()` and use
`VerificationStateData`/`VerificationStatusNextStep` to decide next actions;
do not hardcode a client workflow. `DocumentType` is a backend string, not an
enum: it must originate in `ConfigurationService.getConfiguration(processType)`.
Preserve the requirement for status-derived `processId` and
`ActivationMissingStatusException` behavior.

Keep the `WDOResult(onSuccess/onFailure)` contract and
`VerificationService.Fail.state` intact. Flag only actual double delivery,
missing delivery, callback on the wrong execution context where the established
contract requires otherwise, or state loss due to asynchronous races. Do not
convert an error to a nullable/success value. Changes in phase/status mapping
must preserve all explicit combinations and update
`VerificationStatusNextStepTest`, `DocumentActionTest`, or
`VerificationScanProcessTest` when their existing coverage applies; inspect
these tests as available review evidence.

`Storage` persists scan/process state across restarts. Reject changes that use
an unstable process key, erase retry-required document metadata, or break
resubmission identity. Treat network model JSON as untrusted: maintain exact
keys, nullable handling, enum/string wire mappings, and error decoding across
`networking/model` and API clients.

## Security and validation focus

Review the three networking APIs for preserved PowerAuth signing/E2EE scopes,
request-body integrity after signing, correct authentication error propagation,
and no insecure fallback. Never log activation codes, credentials, tokens,
keys, document images/content, identity data, signed requests, or raw server
responses through `WDOLogger`/`WDOLogListener` or exceptions. Do not weaken
encrypted storage/key management or treat protocol/transport failure as
successful onboarding.

Tracked scripts, build configuration, tests, and workflows may be inspected as
review evidence only. Do not report CI configuration concerns.
