# Logging

The library is intensively logging into the console via `Logger`.

Possible log levels:

- `DEBUG` - Debug logging that outputs more granular data, use this only during development
- `INFO` - prints info logs + logs for warning level produced by the library (__default level__)
- `WARNING` - prints warnings and errors
- `ERROR` - prints errors only
- `OFF` - logging is turned off

You can set the log level by `Logger.verboseLevel = Logger.VerboseLevel.OFF`.

<!-- begin box info -->
`Logger` calls internally the `android.util.Log` class.
<!-- end -->