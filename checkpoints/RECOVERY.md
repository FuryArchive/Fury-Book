# Furybook recovery

Extract latest.zip and clone its repository.bundle. Apply all increments in current.manifest.json in order: git fetch /absolute/path/to/bundle HEAD; git checkout --detach FETCH_HEAD. Check each SHA-256 before use. Run git fsck --full. Expected source fc9205d47529b247ef962e466aa2842ed027271e, tree a131cf714b2933206691046d87c24e199768f033. The older base manifest describes only the immutable base.

Production gates disabled. Shared Compose panel and Desktop opt-in host staged but NOT Compose-compiled or UI-tested. Browser runtime pending connection refusal. Android native/device acceptance, Android/Web screen wiring, built-in dice, durations and full builds remain open. No new APK or deployment.
