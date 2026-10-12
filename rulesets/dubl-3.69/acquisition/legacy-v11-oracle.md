# Legacy v11 migration evidence

Economic source is the executable implementation at Git commit
`f47a5256a66ddc5e1b7b5c1e204e57279fc7227f`, not the corrected source-price oracle.
`legacy-v11-catalog.json` is development provenance and is byte-identical to the
manifest-declared `dubl.legacy-acquisition-v11` FCP runtime snapshot. Its source
list pins every old catalog layer, both old manifests (including the Chi claim),
the skill table, magic spell IDs, calculator dependencies, and snapshot decoder.
`python tools/legacy/freeze_legacy_v11.py` reproduces both copies from those Git
objects. It does not read the current catalogs to obtain historical prices.

The independent executable oracle is `tools/legacy/LegacyEconomyV11Oracle.kt`.
Compile it with the **original** commit's `core/json/*.kt`, `dubl/model/*.kt`,
`dubl/data/CatalogData.kt`, and `dubl/data/SnapshotCodec.kt`, then run it with
arguments `<original commonMain/resources> <fixture output directory>`.
It uses the original manifest ordering, original Chi exclusion/claims,
`SnapshotCodec.decode`, local overrides, and original `CharacterEconomy.breakdown`.
It has no dependency on `LegacyEconomyV11` or the new acquisition calculator.
The six original JSON snapshots are committed in
`shared/src/desktopTest/resources/legacy-v11/`.

| Original snapshot | Core XP / AP | Core + Chi XP / AP |
| --- | ---: | ---: |
| self_false | 100 / 0 | 100 / 0 |
| self_true | 100 / 0 | 100 / 0 |
| eagle | 100 / 0 | 100 / 0 |
| negative | −10 / 0 | −10 / 0 |
| mixed | 1624 / −3 | 1524 / −3 |
| legacy_mana | 100 / 0 | 100 / 0 |

Self-Taught cost 40 XP in this old catalog. The unfinished snapshot has funding
170 and skill expenditure 30; the finished snapshot has funding 155 and skill
expenditure 15. Both old balances are exactly 100. Migration does not apply a new
discount or change stage. Eagle's Sight was explicitly 0 XP in the old catalog.

The mixed snapshot independently exercises attribute expenditure 115, hidden
skill expenditure 60, local Eagle override 7 × manual rank 3 = 21, mana 100,
recognized school 50, spells 37, and adjustment −7. Total expenditure is 376;
funding 2000 leaves 1624. Unknown/custom school costs and unpriced/incomplete
spells retain the **known old calculator's** zero behavior. AP funding 1 less
old access option expenditure 4 leaves −3. Activating old Chi adds 100 XP of
expenditure. The legacy mana snapshot normalizes old development mana rank 3
into the mana field once and spends 300, not 600.

These are historical preservation anchors. They grant no admission, quote, refund,
new purchase, or permission to treat a missing old catalog as a zero-price catalog.
