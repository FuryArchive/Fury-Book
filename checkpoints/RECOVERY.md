# Furybook recoverable checkpoint

Current source: 6cb1d5f996242459612bcba673d4c86d0d41e363

Extract checkpoints/latest.zip and clone its repository.bundle. Apply each bundle from current.manifest.json in order using git fetch /absolute/path/to/bundle HEAD then git checkout --detach FETCH_HEAD. Verify SHA-256 before applying each bundle and run git fsck --full. The final tree must equal a22772a06b0abe32d5b2caba743217bc1388718f. Older base manifest describes the immutable base, not the current checkpoint.

Production gates disabled; no user data migrated. Browser runtime blocked by local test-server connection refusal. Android native/device acceptance, full UI integration, durations and full platform builds remain open. Not a new APK or deployment.
