"""Cut the static Unbounded and Onest instances bundled in shared Compose resources.

Run once from the repository root: python3 tools/fonts/build_fonts.py
Sources are the OFL variable fonts published in github.com/google/fonts.
"""
from __future__ import annotations

import io
import urllib.request
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'shared/src/commonMain/composeResources/font'
BASE = 'https://raw.githubusercontent.com/google/fonts/main/ofl'
FAMILIES = {
    'unbounded': ('Unbounded%5Bwght%5D.ttf', 'OFL-Unbounded.txt', {'unbounded_bold': 700, 'unbounded_black': 900}),
    'onest': ('Onest%5Bwght%5D.ttf', 'OFL-Onest.txt', {
        'onest_regular': 400, 'onest_medium': 500, 'onest_semibold': 600, 'onest_bold': 700,
    }),
}
# Latin, Latin-1, Cyrillic, common punctuation, arrows and currency. Anything else falls back
# to the platform font, which keeps each file small enough for the Web build to load quickly.
KEEP = (
    list(range(0x0020, 0x007F)) + list(range(0x00A0, 0x0100)) + list(range(0x0400, 0x0460))
    + [0x0490, 0x0491] + list(range(0x2010, 0x2027)) + [0x2030, 0x2039, 0x203A, 0x20AC, 0x20BD, 0x2116]
    + list(range(0x2190, 0x2194)) + [0x2212, 0x2260, 0x2264, 0x2265]
)
MINUS = 0x2212
HYPHEN = 0x002D


def fetch(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=60) as response:
        return response.read()


def ensure_minus(font: TTFont) -> None:
    for table in font['cmap'].tables:
        if table.isUnicode() and MINUS not in table.cmap and HYPHEN in table.cmap:
            table.cmap[MINUS] = table.cmap[HYPHEN]


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for family, (source, licence, instances) in FAMILIES.items():
        variable = fetch(f'{BASE}/{family}/{source}')
        (OUT / licence).write_bytes(fetch(f'{BASE}/{family}/OFL.txt'))
        for name, weight in instances.items():
            font = instantiateVariableFont(TTFont(io.BytesIO(variable)), {'wght': weight})
            options = subset.Options()
            options.layout_features = ['*']
            options.name_IDs = ['*']
            options.notdef_outline = True
            subsetter = subset.Subsetter(options)
            subsetter.populate(unicodes=KEEP)
            subsetter.subset(font)
            ensure_minus(font)
            font.save(OUT / f'{name}.ttf')
            print(f'{name}.ttf wght={weight}')


if __name__ == '__main__':
    main()
