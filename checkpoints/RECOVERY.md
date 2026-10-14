# Furybook recovery checkpoint

This branch contains backups, not a deployable source checkout. Main is unchanged.

Latest source: 2b37180544b6af49fa178e5f77513ce7161b79ae.

Keep latest.zip (base cfb3dd6) AND after-cfb3dd6-to-2b37180.bundle together. Unpack latest.zip, clone its self-contained Git bundle, then run inside the clone:

    git fetch /absolute/path/after-cfb3dd6-to-2b37180.bundle HEAD
    git switch -c recovered-2b37180 FETCH_HEAD
    git fsck --full

Expected HEAD: 2b37180544b6af49fa178e5f77513ce7161b79ae.
See current.manifest.json for hashes and verification limits. Latest.zip remains the older immutable base, not the current source by itself. No production flags or deployment are enabled. Full automation and real UI acceptance are not complete.
