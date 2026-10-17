# Existing character startup compatibility

The reported Desktop startup failure displays `Unknown character extras field`. `DesktopCharacterExtrasStore` used the same strict decoder for ordinary sheet loading and for financial migration. A readable old sheet containing an extra field therefore prevented construction of `DesktopAppState` and hid the entire character.

The shared `LegacyExtrasDocument` now reads the known sheet projection while retaining the complete JSON document. Desktop and Web use it for ordinary loading and editing. Unknown root/character fields, other characters and opaque unchanged values survive edits and reopening. Unrecognized condition/resource/attribute values are retained alongside edited recognized values. Old literal grouping separators and Unicode escapes remain supported. Desktop saves also retain the exact original `sheet-extras.json.pre-compatibility.bak`, without replacing an existing backup.

Financial migration remains separate and strict. Source fields which cannot be represented, unsupported versions and malformed financial inputs cannot silently enter canonical purchases. A future extras version can be viewed but cannot be overwritten. Opening does not create a migration, debit XP, reprice ownership or rewrite the old character. This fixes valid legacy documents with extra fields; recovery from actually damaged JSON is a separate case.

Desktop also displays the legacy sheet and its other ordinary sections when local rules are enabled but financial migration has not been performed. An explicit read-only notice points to the migration controls under Characters. Protected mutation routing remains in place; unsupported/corrupt canonical heads still retain their recovery panel.

The patch is based on icon commit `8bb5e923ace4f6fa210867a8b9b2a27da9a10179`. No artwork, platform icon assets or icon wiring are changed. Cloud format 2 writes remain disabled; no cloud queue is cleared.

Product source: `db573044142c9878b4cf19014f3d3eb449024e52`. Linux, Windows, Android and Web CI completed successfully. Linux opens and reopens a schema-7 character in the production Compose sheet with local rules enabled and no financial migration; default-mode reopening is also covered. The old character/extras bytes, selected identity, XP, rank, AP override, notes and explicit condition remain intact. Web executes the legacy extras edit/recreation scenario in Firefox and retains the exact original source for migration.

APK, real Compose AppImage, Windows EXE/MSI and Web ZIP are built from that same source. The packaged AppImage icon and Web icon files were compared with the new branch assets. [Build provenance, artifact IDs, sizes, checksums and platform limits](2026-10-18-builds.json) distinguish these builds from the earlier release.

The ordinary shared regression gate retains its existing full-corpus exclusions; this is not full acquisition/effect acceptance. Windows interactive installation, physical Android legacy-character interaction and an authenticated Web character UI were not exercised. Financial migration of unrepresented fields and recovery of actually damaged JSON remain separate from viewing a valid old sheet.

Local verification also compiled the real Shared code with Kotlin 2.4.0 and passed legacy document/file compatibility plus native acquisition storage/transfer checks. The regular local Gradle invocation could not resolve Android plugin 9.3.0; the successful platform builds above came from CI.
