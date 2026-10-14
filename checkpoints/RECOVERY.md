# Furybook recovery

Extract latest.zip and clone its repository.bundle. Apply every increment in current.manifest.json in order with git fetch /absolute/path/to/bundle HEAD then git checkout --detach FETCH_HEAD. Verify SHA-256 and git fsck --full. Expected source aa21d8a349aea5eaa6c20e6b775be6a2114208bd; tree 229c1f15331384b49c4717c41932bd4ef3a4408c. The old base manifest describes only the immutable base.

All production gates disabled. Compose panels/hosts remain uncompiled and not UI-accepted. Durable event boundaries are implemented; event UI and source-specific timed-effect activation are not. No implicit HP/endurance/AP/reaction refresh. Full automation/platform acceptance remain open. No new APK or deployment.
