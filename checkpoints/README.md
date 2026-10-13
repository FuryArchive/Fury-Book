# Furybook recovery backups

This directory stores immutable checkpoint backups. **This is a backup branch, not a deployable source branch.** Repository source files outside this directory remain at the branch's base commit. The checkpoint source is inside the ZIP.

## Latest checkpoint: Web source/compile

- Archive: [latest.zip](latest.zip)
- Manifest: [latest.manifest.json](latest.manifest.json)
- Source commit: `cfb3dd67efdff3c4810e91abb9a1180435de2cdf`
- Source tree: `d6b20930e13cefc9c426800a536895c2ad650f7a`
- Archive size: 10,531,906 bytes
- Archive SHA-256: `40c9b9506295913e9fb0bbdae086c15378287c8354f15827f1fd8f7eb47c075c`

**Acceptance limit:** This is a source/compile checkpoint. Twenty browser test methods were NOT EXECUTED. Do not interpret this backup as browser runtime acceptance or as completion of later work.

Download the ZIP, verify its SHA-256, and extract it into a new directory. It includes the complete source snapshot, a self-contained Git bundle preserving original source history, results, and evidence. Review RESULTS.md, SOURCE_MANIFEST.json, EVIDENCE_MANIFEST.json, and SHA256SUMS inside the archive before restoring.

## Earlier R5 checkpoint

The exact original R5 ZIP and manifest remain preserved in [backup commit 5e9186d9](https://github.com/FuryArchive/Fury-Book/tree/5e9186d9af05ba2d7af444f8a0b3cb708d518892/checkpoints). Its source commit was `8758f59b58a99f55332d9d2dacd62026d5e897cb`. The stable latest paths keep only one archive in the current branch tree; earlier checkpoint archives remain available through Git history.

This publication does not change main. Backup commit IDs are not source commit IDs, and the backup branch's tree is not the checkpoint source tree. Do not merge or deploy this branch as application source.
