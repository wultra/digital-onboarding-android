# Logging

The library is intensively logging into the console via `WDOLogger`.

Possible log levels:

- `DEBUG` - Debug logging that outputs more granular data, use this only during development
- `INFO` - prints info logs + logs for warning level produced by the library (__default level__)
- `WARNING` - prints warnings and errors
- `ERROR` - prints errors only
- `OFF` - logging is turned off

You can set the log level by `WDOLogger.verboseLevel = WDOLogger.VerboseLevel.OFF`.

<!-- begin box info -->
`WDOLogger` calls internally the `android.util.Log` class.
<!-- end -->

## Log Listener

The `WDOLogger` class offers a static `logListener` property. If you provide a listener, all logs will also be passed to it (the library always logs into the Android default log).

<!-- begin box info -->
Log listener comes in handy when you want to log into a file or some online service.
<!-- end -->