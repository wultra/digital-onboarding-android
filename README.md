# Wultra Digital Onboarding for Android

<!-- begin remove -->
<p align="center"><img src="docs/images/intro.jpg" alt="Wultra Digital Onboarding for Android" width="100%" /></p>

[![Lint](https://github.com/wultra/digital-onboarding-android/actions/workflows/lint.yml/badge.svg)](https://github.com/wultra/digital-onboarding-android/actions/workflows/lint.yml) [![build](https://github.com/wultra/digital-onboarding-android/actions/workflows/build.yml/badge.svg)](https://github.com/wultra/digital-onboarding-android/actions/workflows/build.yml) 
![GitHub release (latest by date)](https://img.shields.io/github/v/release/wultra/digital-onboarding-android)
![date](https://img.shields.io/github/release-date/wultra/digital-onboarding-android) 
[![mavenCentral](https://img.shields.io/maven-central/v/com.wultra.android.digitalonboarding/wultra-digital-onboarding)](https://mvnrepository.com/artifact/com.wultra.android.digitalonboarding/wultra-digital-onboarding)
[![license](https://img.shields.io/github/license/wultra/digital-onboarding-android)](LICENSE)  
<!-- end -->

<!-- begin box info -->
The Wultra Digital Onboarding SDK functions as an extension of [Wultra Mobile Authentication (PowerAuth)](https://wultra.com/powerauth) that is required.
<!-- end -->

## Introduction

Elevate your standard device activation, user login, and request signing scenarios by incorporating facial recognition and document scanning in diverse situations:

- Reclaim access or recover lost credentials by authenticating the user's genuine presence and verifying document validity.
- Reinforce conventional password or PIN-based authentication with an extra layer of security through face recognition.
- Seamlessly onboard new customers into your systems, authenticating them with identification cards and facial scans for access to your app.

### Minimal requirements

| Requuirement |      Value                     |  
|--------------|--------------------------------|
| Min. system  |  __Android 9__ (API level 28)  | 
| Integration  |  __MavenCentral__              | 

### Other resources

We also provide an [iOS version of this library](https://github.com/wultra/digital-onboarding-apple).

## What will you need before the implementation

<!-- begin box info -->
The Wultra Digital Onboarding SDK functions as an extension of Wultra Mobile Authentication that is required.
<!-- end -->

Before initiating the integration, it's essential to ensure that your server environment is prepared with appropriately configured services capable of managing user verification and onboarding, seamlessly connecting to your systems.

Given the unique characteristics of each customer system, the utilization of this SDK may vary. To accurately outline the user verification process, we recommend consulting with our technical team for tailored guidance.

## Document ORC and face verification

We seamlessly incorporate industry-leading solutions for document scanning, ensuring versatility and effectiveness in your operations.

- iProov for genuine presence
- Innovatrics for document scanning and genuine presence
- ZenID for document scanning

Our dedicated technical and sales representatives are available to guide you in selecting the optimal solution that aligns perfectly with your needs.

## Documentation

<!-- begin box warning -->
Please be aware that the scenarios outlined in the documentation are illustrative examples. Your user flow and implementation may vary based on your specific requirements and circumstances.
<!-- end -->

The documentation is available at the [Wultra Developer Portal](https://developers.wultra.com/components/digital-onboarding-android) or inside the [docs folder](docs).

## License

All sources are licensed using the Apache 2.0 license.

## Contact

If you need any assistance, do not hesitate to drop us a line at [hello@wultra.com](mailto:hello@wultra.com) or our official [wultra.com/discord](https://wultra.com/discord) channel.

### Security Disclosure

If you believe you have identified a security vulnerability with Wultra Digital Onboarding, you should report it as soon as possible via email to [support@wultra.com](mailto:support@wultra.com). Please do not post it to a public issue tracker.