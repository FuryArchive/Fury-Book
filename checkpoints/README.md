# Furybook recovery backups

This directory stores immutable checkpoint backups. **This is a backup branch, not a deployable R5 source branch.** The repository source files outside this directory remain at the branch's base commit; the accepted R5 source is inside the ZIP.

## R5 checkpoint

- Archive: [8758f59.zip](8758f59.zip)
- Source commit: `8758f59b58a99f55332d9d2dacd62026d5e897cb`
- Source tree: `d2a9d79f7e1d19dfa887a5ee55f74411c23714b1`
- Archive size: 10,220,717 bytes
- Archive SHA-256: `a0f4ede25e5f1ea9e3b45aaae2f9dcff484fcaa10215093800e617b208b30f63`
- Machine-readable metadata: [8758f59.manifest.json](8758f59.manifest.json)

Download the ZIP, verify its SHA-256, and extract it into a new directory. It contains the complete source snapshot, a self-contained Git bundle with the original history, results, and verification evidence. Consult RESULTS.md, SOURCE_MANIFEST.json, EVIDENCE_MANIFEST.json, and SHA256SUMS inside the archive before restoring. Do not merge or deploy this backup branch as application source.

This publication does not change main or claim that the backup branch's tree equals the accepted source tree. Later work is not included in this R5 checkpoint.
