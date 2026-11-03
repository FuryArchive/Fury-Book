# DUBL 3.69 Rulebook Audit — Mechanics gap pass (load, magic, development, Chi)

Source of truth: local copy `Dубль All Stars 3.69 REWORK(1).docx` (2026-09-11) plus `Dубль, Мастера ближнего боя.docx` and `Книга Архимага.docx`.
The live Google Doc "Dубль All Stars 3.69 REWORK" was edited on 2026-10-26 but could not be fetched (Drive API unavailable), so changes made after 2026-09-11 are **not** covered.

Builds on [2026-09-16-core-skills-rolls.md](2026-09-16-core-skills-rolls.md); items already resolved there are not repeated.

Method: rulebook text was compared to the shared rules code by hand, and checked by script against the bundled FCP catalogs. A throwaway JVM probe then ran the real `DevelopmentRules` / `rollCheck` / `MagicEquipmentRules` against constructed characters.

## Findings and status

| # | Area | Rulebook | Previous app behaviour | Status |
| --- | --- | --- | --- | --- |
| 1 | Equipment load | Equipment = STR + CON, **filled by items' "Требование по силе"** (core, Экипировка) | Summed weight in kg. Plate (`Латы`) had no weight → 0 load; STR 2 / CON 2 with a longsword + chain shirt gave -4 instead of medium -2 | **Fixed.** `catalogGearLoad` reads `Треб.` / `Треб` / `Требование` (leading number; none → 0). Stored catalog items holding the old weight value are repaired by `syncCatalogGearLoads`. UI labels say "Нагрузка (треб. силы)" |
| 2 | Ability-root branches | Root tables have empty `Описание:` / `Особое:` template rows; mechanics live in the child card | 12 roots marked `incomplete`, so 191 / 796 entries showed "manual check" even for a maxed character | **Fixed** in `promote_ability_roots`: empty template rows stay an import diagnostic only. Still incomplete: Связи (draft `???` source) |
| 3 | "A, B, C или D" requirements | — | Read as A **and** B **and** (C or D) | **Not a bug** — confirmed as the intended reading |
| 4 | Double rolled under Hindrance | A double grants an advantage die | Extra die added, then the two lowest kept, so it never helped | **Fixed**: the advantage die competes with the two kept dice, so it can only improve the result (same as before for Normal/Advantage) |
| 5 | Mana without school power | Core: Mana Pool rank grants 1 power. Archmage: power = school rank | Power from school rank only | **Not a bug** — the Archmage book is the source of truth for magic |
| 6 | Magic-school rank price | No price in either book | 25 XP per school rank | **Intended** — house value, absent from the book text |
| 7 | Самоучка | "Doesn't work at character creation" | Completing creation retroactively discounted ranks bought during creation | **Fixed**: `CharacterSkill.creationRank` is recorded by `completeCreation`; only ranks above it (up to 2) are discounted. Legacy saves without it keep their previous totals |
| 8 | Spell-descriptor requirements | "Знать 3 заклинания с дескриптором огонь/холод" | Any 3 learned spells passed | **Fixed**: OK only with that many spells whose text shows the element; too few learned spells → FAIL; otherwise manual check (books have no descriptor field) |
| 9 | Skill-name requirements | "Ремонт 3", "Знание: Магия 3" | Never matched → manual check | **Fixed** via `developmentAlias` (Ремонт → Инженерное дело (ремонт); "X: Y" → "X (Y)" for Знание/Ремесло/Исполнение/Профессия) |
| 10 | Chi schools | Only one Chi school unless the GM allows syncretism | Second school allowed | **Fixed**: a second school FAILs (force-buy still possible for GM-approved syncretism) |
| 11 | Attack outcome | Tie = partial hit; 6-6 crits only if it hits; superiority die only for a missing 6-6 | Generic "equal to target"; superiority die always offered | **Fixed** for the Attack roll: shared `resolveAttack` (miss / partial ½ / full / critical ×1,5 / critical miss) and `availableFollowUp`, which withholds the superiority die when a natural 6-6 already hits. A 6-6 that only hits after the superiority die is a normal hit, not a critical. Shown on Android, Desktop and Web. Flurry: `FlurryAttackRule.resolve` counts one hit per required excess over Defense (short flurry +1), capped at the weapon maximum; a margin below one step scores 0 hits (same wording as Ярость дракона). Feat bonuses to the maximum number of attacks are not added automatically |

## Book rules with no automation yet

- Cumulative -2 for consecutive same-type checks, and reset with 1 AP or 1 Endurance
- -2 to attacks with a weapon you aren't proficient in; two-handed STR thresholds (1.5× / ½); one-handed weapon in two hands = STR × 1.5
- Multi-action penalty; spell concentration DCs (6+cost, 4+total cost for multicast, sustain table)
- Risky checks, Auto 6 / Auto 12 workflow, prolonged checks
- Species base health; armor, resistances, vulnerabilities, Tough Musculature natural armor; Vigilance passive awareness
- Spell Specialization (+2 power for one spell), Initiate (+1 school checks)
- 57 of 265 spells are `incomplete` (duplicate/conflicting cards) and cost 0 XP to learn until overridden

## Book-side inconsistencies (ask the authors, don't fix in code)

- Requirements above the prerequisite's max rank (book says ranks: 1): Пламя ярости 2 (Сжигатель миров), Невидимая работа 2 and Подстава 2 (Король проделок), Ледяная хватка 2 (Воплощение зимы)
- Spell cost differs between core and Archmage: Божественное вмешательство (малое), Внушение, Ментальный барьер

## Verified correct

Attribute cost table; size STR/SPD modifiers; Defense / Health / Reflexes / Initiative / Fortitude / Endurance; full Run table (2 legs and 3+ legs); skill rank costs, Auto 6 / Auto 12 / untrained table; synergy and assistance; flurry table and short flurry; maneuver skill/attribute pairs; mana table incl. >20 increments; spell learn XP; Increased Mana Pool; Meditation; Incredible Health size scaling; Self-taught creation gate; Chi pool (Will+1, min 3; +50 XP ranks ×10; Master +2; Awakened +3); Storm Lord / Still Mountain; ability points 1 per 1000 XP; burden thresholds ×1.5 / ×2 / ×3.
Catalog content: 664 of 665 name-matched XP development entries match the book on cost, ranks and requirements (Кендо carries an extra weapon-group clause); 213 of 216 name-matched spells match on cost.
