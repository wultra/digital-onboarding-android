# Welcome to the Wultra Digital Onboarding SDK Android repository!

In this file, you'll find topics that help with local setup, running lint and tests, creating pull requests, and preparing a new release.

## Table of Contents

- [Getting Started](#getting-started)
- [Project Structure](#project-structure)
- [Running Lint and Tests](#running-lint-and-tests)
- [Creating a Pull Request](#creating-a-pull-request)
- [Preparing a New Release](#preparing-a-new-release)

## Getting Started

> [!WARNING]
> If you're not a Wultra employee or contractor, please fill out the [Wultra Contributor License Agreement](https://forms.gle/r715RoVDoji4GD7K7) before you start contributing.

Before you start development, make sure you have the following prerequisites:

- macOS or Linux machine
- Java 17 installed
- Android SDK installed
- Android emulator setup for instrumentation tests
- `curl` installed (required by `scripts/prepare-release.sh` and `scripts/lint.sh`)

To verify your local setup, run this command in the project root:

```bash
./gradlew clean build
```

## Project Structure

The most important files and directories are:

```text
digital-onboarding-android/
├── .github/                            # GitHub workflows and contribution docs
├── buildSrc/                           # Shared Gradle build logic and constants
├── docs/                               # Public documentation
├── gradle/                             # Gradle wrapper config
├── library/                            # Android library module
│   ├── src/main/                       # SDK sources
│   ├── src/test/                       # JVM unit tests
│   └── src/androidTest/                # Android instrumentation tests
├── scripts/                            # Build, lint, test, and release scripts
├── build.gradle.kts                    # Root build script
├── settings.gradle.kts                 # Gradle modules setup
└── .prepare-release.json               # Release preparation metadata
```

## Running Lint and Tests

Before you run tests, make sure:

- Java 17 is selected in your shell.
- Android SDK and emulator are available for instrumentation tests.
- `library/src/androidTest/assets/config.json` is configured correctly for integration tests. See `library/src/androidTest/assets/Readme.md` for the file format.

Run lint:

```bash
./scripts/lint.sh
```

Run Android Lint:

```bash
./gradlew clean :library:lint
```

Run unit tests:

```bash
./scripts/test.sh -type unit
```

Run Android instrumentation tests:

```bash
./scripts/test.sh -type android
```

Pass integration config JSON from CI or another source:

```bash
./scripts/test.sh -type android -config "$TESTS_CONFIG"
```

The test script writes provided JSON into `library/src/androidTest/assets/config.json` before instrumentation tests.

The configuration file format is described in `library/src/androidTest/assets/Readme.md`.

## Creating a Pull Request

> [!WARNING]
> Before you create a pull request, make sure:
>
> - an issue exists for the change
> - all required tests are passing
> - `./scripts/lint.sh` does not report issues

1. If you're not a Wultra employee or contractor, fork the repository and work in your fork.
2. Create a branch named `issues/issue-number-short-description`, for example `issues/123-fix-status-docs`.
3. Make your changes and commit them with a clear commit message.
4. Push the branch to the remote repository.
5. Create a pull request targeting the `develop` branch.
6. Reference the related issue in the pull request description using `#issue-number`.
7. If you're not a Wultra employee or contractor, wait for a Wultra team member to approve workflows to run.
8. If you're a Wultra employee or contractor, wait for workflows to pass and request review.

## Preparing a New Release

> [!WARNING]
> This section is intended for Wultra employees and contractors only.

### Release streams

- `develop` is the development branch
- release branches use the format `release/a.b.x`, for example `release/2.0.x`
- release branch history should stay linear
- changes to a release branch should go through pull requests and be squash-merged

### Release versioning

The version number has format `major.minor.patch`, for example `2.0.0`.

- increment `major` for larger milestones or major compatibility changes
- increment `minor` for new features or API changes
- increment `patch` for bug fixes only

### Each release should include

- updated `library/gradle.properties`
- updated `docs/Changelog.md`
- updated `docs/SDK-Integration.md` when version examples or compatibility information change
- updated migration guide or other public documentation if the release changes the public API

You can use:

```bash
./scripts/prepare-release.sh -v VERSION
```

Verification modes:

```bash
./scripts/prepare-release.sh -v VERSION --verify
./scripts/prepare-release.sh
```

To prepare publication artifacts:

```bash
./scripts/build-and-publish.sh local
```

Use `central` instead of `local` only when publishing to Maven Central with the required credentials in place.

### Example release flow

1. Create an issue for the release.
2. Make or update the target `release/a.b.x` branch from `develop`.
3. Create a working branch, for example `issues/65-prepare-release-2_0_0`.
4. Update all files required for the release.
5. Run lint and tests.
6. Run `./scripts/prepare-release.sh -v VERSION`.
7. Create a pull request into the target `release/a.b.x` branch.
8. After approval, squash-merge the pull request.
9. Publish the tag and create the GitHub release.
10. Verify Maven Central publication and public documentation updates.
