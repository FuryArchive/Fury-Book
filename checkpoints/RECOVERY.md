# Furybook recovery

Restore latest.zip, then apply the ordered bundle increments listed in current.manifest.json. Each incremental bundle exports HEAD. Fetch and check out that HEAD in order. Verify the final source tree against the manifest. Current source: a39f672cd3595a9bc7a3079e835b2429eddbf045.

This is a source backup, not a deployed release or new APK. Production gates remain off. See docs/automation-progress.md for precise test evidence and limitations.
