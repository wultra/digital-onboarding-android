# Migration from 2.0.x to 3.0.x

This guide covers public API changes between `2.0.x` and `3.0.x`.

## Breaking Changes

1. Read OTP resend cooldown from configuration instead of verification state.

```kotlin
configurationService.getConfiguration(processType) { result ->
    result.onSuccess { config ->
        val otpResendPeriodSeconds = config.responseObject.otpResendPeriodSeconds
    }
}
```

`otpResendPeriodSeconds` is optional and can be `null` when you are still integrating with an older backend that does not return the field yet.

2. Update OTP state handling.

Before:

```kotlin
when (val state = statusResult.state) {
    is VerificationStateOtpData -> {
        val remainingAttempts = state.remainingAttempts
        val resendCooldown = state.otpResendPeriodInSeconds
    }
    else -> Unit
}
```

After:

```kotlin
when (val state = statusResult.state) {
    is VerificationStateOtpData -> {
        val remainingAttempts = state.remainingAttempts
    }
    else -> Unit
}
```

Use `config.responseObject.otpResendPeriodSeconds` to drive the resend cooldown in activation and verification UI.

## Checklist

- Fetch and keep `ConfigurationResponseData.otpResendPeriodSeconds` for the active process type, and handle `null` for older backends.
- Update `when` branches and type checks for `VerificationStateOtpData`.
- Stop relying on OTP resend cooldown from `VerificationService.status()` results.
