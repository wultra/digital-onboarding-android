# Wultra Digital Onboarding for Android

<!-- begin remove -->
<p align="center"><img src="docs/images/intro.jpg" alt="Wultra Digital Onboarding for Android" width="100%" /></p>
<!-- end -->

Elevate your standard device activation, user login, and request signing scenarios by incorporating facial recognition and document scanning in diverse situations:

- Reclaim access or recover lost credentials through authenticating the user's genuine presence and verifying document validity.
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
The Wultra Digital Onboarding SDK functions as an extension of [Wultra Mobile Authentication (PowerAuth)](https://github.com/wultra/powerauth-mobile-sdk) that is required.
<!-- end -->

Before initiating the integration, it's essential to ensure that your server environment is prepared with appropriately configured services capable of managing user verification and onboarding, seamlessly connecting to your systems.

Given the unique characteristics of each customer system, the utilization of this SDK may vary. To accurately outline the user verification process, we recommend consulting with our technical team for tailored guidance.

## Document ORC and face verification

We seamlessly incorporate industry-leading solutions for document scanning, ensuring versatility and effectiveness in your operations.

- iProov for genuine presence
- Innovatrics for document scanning and genuine presence
- ZenID for document scanning

Our dedicated technical and sales representatives are available to guide you in selecting the optimal solution that aligns perfectly with your needs.

## Open Source Code

The code of the library is open source and you can freely browse it in our GitHub at [https://github.com/wultra/digital-onboarding-apple](https://github.com/wultra/digital-onboarding-android/tree/develop/)

<!-- begin remove -->
## Integration Tutorials
- [SDK Integration](SDK-Integration.md)
- [Device Activation With Email* Only](Device-Activation.md)
- [Verifying User With Document Scan And Genuine Presence Check](Verifying-User.md)
- Onboarding a new user _(not available at the moment)_
- [Language Configuration](Language-Configuration.md)
- [Logging](Logging.md)
- [Changelog](Changelog.md)

_* or similar weak identification like userID or phone number_
<!-- end -->

## Read next

- [SDK Integration](SDK-Integration.md)
