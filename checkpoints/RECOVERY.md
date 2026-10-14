# Furybook recovery

Restore latest.zip, then apply the ordered bundle increments in current.manifest.json. Each bundle exports HEAD. Fetch and check out HEAD in order. Verify the final source tree against the manifest.

Current source: 363c9e824335527d90e040aee3431be12421d11c.

258 scoped gameplay assertions and 68 JUnit tests passed in the consolidated current-source non-UI JVM run. UI remains uncompiled/unaccepted, production gates off; no new APK. See docs/automation-progress.md for details.
