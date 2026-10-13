# Rule automation: current implementation boundary

This is functional work in progress, not a complete ruleset acceptance or a released build.

## Current additional functionality

- Independent canonical character copies preserve their original ledger, receipts and migration provenance without requiring the original device's legacy files. Original migrations still retain local source-drift protection.
- Skill effect bonuses obey their own source conditions: Ace and Talented Performer require four ranks in the affected skill; creature identification requires five; Erudition's numeric bonus requires a learned, non-secret Knowledge skill.
- Erudition permits untrained ordinary Knowledge; secret Knowledge remains excluded. A disabled effect does not grant this exception.
- Versatile Performer uses the highest learned Performance rank for other learned Performance skills without changing saved ranks, purchase prices or ownership. The optional simultaneous-performance bonus remains separate.
- Quick Search applies its one hindrance only at rank one. Tactile lock perception no longer erroneously adds a hindrance. Its selective immunity does NOT remove unrelated manually entered dice; attribution of those dice still requires explicit context.
- Passive Awareness and the natural-armor bonus from Tough Musculature are calculated from owned ranks and displayed on all three character sheets. Passive Awareness is not added to active Awareness rolls. Armor is labeled as a bonus, not total equipment armor.

Source identities and wording are retained in the existing `skill_effects_catalog.json` (review indices 21, 22, 29, 46, 49, 50, 51, 99, 213). No costs or source uncertainty are rewritten.

- Parry without the owned Parry feature now uses half of its full bonus, rounded upward. False Movement enables Dexterity for Feint only when owned.
- Explicit per-roll condition selectors apply Charge/Raid, Focused Dodge, Falcon Feathers and Team Player without pretending to know distance, prior movement or encounter state.

Targeted model tests and compilation of Android, Desktop and Web succeeded for this functional batch. Browser/device UI execution remains unverified.

## Remaining scope

The 283-row effect catalogue still contains stateful, encounter, conditional, alternative, reroll and rule-gap entries which are not fully automated. Reminder text is not implementation. Requirements with unresolved source facts remain blocked rather than guessed. Encounter/resource actions, attribution of situational penalties and remaining formula transitions need implementation before claiming full automation.

The acquisition-wide T19 report is unfinished, T20 reconstruction is pending, and external browser/device/server release gates remain open. Existing production capability defaults have not been enabled. Targeted changed-area checks occur during implementation; a full regression is reserved for the completed functional batch. No independent-review claim is made for direct work performed while delegated execution is unavailable.
