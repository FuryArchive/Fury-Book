# Furybook backups

Not a deployable checkout. Main remains untouched.

Clone the full bundle inside latest.zip (base cfb3dd6). Fetch and checkout HEAD from these bundles in order:

1. after-cfb3dd6-to-2b37180.bundle
2. after-2b37180-to-c6aaaf9.bundle
3. after-c6aaaf9-to-14e3511.bundle

Keep latest.zip and all three bundles. Run git fsck --full. Expected source HEAD: 14e35119142c9775186ea0925b94e46b1c423c43.

See current.manifest.json for checksums and scoped verification. No production format migration, Undo, event engine or UI enablement yet.
