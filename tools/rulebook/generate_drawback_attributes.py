"""Generate the eight explicit native drawback attribute modifiers, without changing purchased bases."""
from pathlib import Path
import argparse
from tools.rulebook.native_drawback_rules import load_native_drawback_rules

ATTRIBUTES = {'STR': 'STRENGTH', 'DEX': 'DEXTERITY', 'CON': 'CONSTITUTION', 'SPD': 'SPEED',
              'INT': 'INTELLIGENCE', 'PER': 'PERCEPTION', 'WIL': 'WILL', 'CHA': 'CHARISMA'}
TARGET = 'shared/src/commonMain/kotlin/com/furybook/dubl/model/GeneratedDrawbackAttributeRules.kt'


def render(repo_root):
    rows = [row for row in load_native_drawback_rules(repo_root)['records'] if row.get('attributePenaltyPerRank')]
    assert len(rows) == 8
    lines = ['package com.furybook.dubl.model', '',
             '// Generated from rulesets/dubl-3.69/acquisition/native_drawback_rules.json.',
             '// Effective modifiers only: purchased bases, paid history and initial AP remain unchanged.',
             'internal fun DublCharacter.drawbackAttributeModifier(attribute: AttributeId): Int = when (attribute) {']
    for row in sorted(rows, key=lambda row: row['attributePenaltyPerRank']['attributeId']):
        effect = row['attributePenaltyPerRank']
        assert row['repeatPolicy'] == {'type': 'Single'} and effect['amount'] == -1
        lines.append(f'    AttributeId.{ATTRIBUTES[effect["attributeId"]]} -> -developmentRank("{row["id"]}")')
    lines += ['}', '', 'internal fun drawbackAttributeAffectedBy(entryId: String): AttributeId? = when (entryId) {']
    for row in rows:
        lines.append(f'    "{row["id"]}" -> AttributeId.{ATTRIBUTES[row["attributePenaltyPerRank"]["attributeId"]]}')
    return '\n'.join(lines + ['    else -> null', '}', ''])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo-root', type=Path, default=Path('.'))
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    expected = render(args.repo_root)
    path = args.repo_root / TARGET
    if args.check:
        if path.read_text() != expected:
            raise SystemExit('Generated drawback attribute rules are stale')
    else:
        path.write_text(expected)
