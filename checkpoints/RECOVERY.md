# Furybook backups

Backup branch only. Main is unchanged. No deployment.

Clone the full bundle inside latest.zip, then fetch and checkout HEAD from each incremental bundle in current.manifest.json, in the listed order. Keep the base archive and all four bundles. Run git fsck --full.

Expected source HEAD: 33fce1c341cedffea6f88503397a301eaa309794.

Includes shared action kernel, opt-in journal, durable Desktop action/Undo and backed-up local migration. UI, Android/Web adapters, event durations and full platform acceptance remain open. Production defaults remain disabled.
