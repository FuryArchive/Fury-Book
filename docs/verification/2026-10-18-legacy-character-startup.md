# Existing character startup compatibility

The reported Desktop startup failure displays `Unknown character extras field`. `DesktopCharacterExtrasStore` used the same strict decoder for ordinary sheet loading and for financial migration. A readable old sheet containing an extra field therefore prevented construction of `DesktopAppState` and hid the entire character.

The shared `LegacyExtrasDocument` now reads the known sheet projection while retaining the complete JSON document. Desktop and Web use it for ordinary loading and editing. Unknown root/character fields, other characters and opaque unchanged values survive edits and reopening. Unrecognized condition/resource/attribute values are retained alongside edited recognized values. Old literal grouping separators and Unicode escapes remain supported. Desktop saves also retain the exact original `sheet-extras.json.pre-compatibility.bak`, without replacing an existing backup.

Financial migration remains separate and strict. Source fields which cannot be represented, unsupported versions and malformed financial inputs cannot silently enter canonical purchases. A future extras version can be viewed but cannot be overwritten. Opening does not create a migration, debit XP, reprice ownership or rewrite the old character. This fixes valid legacy documents with extra fields; recovery from actually damaged JSON is a separate case.

The patch is based on icon commit `8bb5e923ace4f6fa210867a8b9b2a27da9a10179`. No artwork, platform icon assets or icon wiring are changed. Cloud format 2 writes remain disabled; no cloud queue is cleared.

Local verification: real Shared code compiled with Kotlin 2.4.0; legacy document/file compatibility and native acquisition storage/transfer checks passed. The regular local Gradle invocation could not resolve Android plugin 9.3.0, so it is not recorded as a passing Gradle build. Linux CI additionally opens and reopens a schema-7 character with its old extras in the production Compose sheet; Web CI exercises the actual browser extras adapter. Those platform results and packages are recorded after CI completes.
