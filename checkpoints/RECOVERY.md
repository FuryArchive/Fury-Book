# Furybook recovery

Extract latest.zip and clone its repository.bundle. Apply all increments from current.manifest.json in order using git fetch /absolute/path/to/bundle HEAD and git checkout --detach FETCH_HEAD. Verify each SHA-256 and run git fsck --full. Expected source 47a4e4a91f9ca0a0aabb092a80cfb037fc172121; tree cada654b7b46c02f51fbe70c8cb832b45454f09c. Base manifest describes the immutable base only.

Production gates disabled. All three platform panel hosts, migration review and built-in dice aid staged but NOT platform-compiled or UI-tested. Browser runtime remains blocked by test-server connection refusal. Durations, full automation and full platform acceptance remain open. No new APK or deployment.
