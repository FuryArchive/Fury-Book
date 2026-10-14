# Furybook backups

Not a deployable checkout. Main is unchanged.

Clone the full bundle inside latest.zip (base cfb3dd6). Fetch and checkout HEAD from after-cfb3dd6-to-2b37180.bundle, then after-2b37180-to-c6aaaf9.bundle, in that order. Keep all three files. Run git fsck --full.

Expected source HEAD: c6aaaf973a5a961370cda2e64b112d8f39b0c257

See current.manifest.json for hashes and verification limits. Source includes the first pure Overexertion transition kernel; durable storage, Undo, events and UI are not yet integrated. No production flags enabled.
