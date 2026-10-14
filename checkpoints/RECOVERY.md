# Furybook recovery

Extract latest.zip and clone its repository.bundle. Apply every increment from current.manifest.json in order with git fetch /absolute/path/to/bundle HEAD and git checkout --detach FETCH_HEAD. Verify each SHA-256, then git fsck --full. Expected source 9f8dd00394ec1f685c1f24530a01c116511651c7, tree 229fea4770ad9fdfa9fcd19c0bd61941723fb093. Base manifest covers the immutable base only.

Production gates remain off. Shared action/migration/event panels are wired to all three hosts but not Compose-compiled or UI-accepted. Event controls are collapsed and explicitly do not heal/refresh resources or activate timed effects. Source-specific activation/expiry and event Undo remain open, along with full platform acceptance. No new APK or deployment.
