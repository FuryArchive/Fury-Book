#!/usr/bin/env python3
"""Regenerate Fury Book platform icons from branding/furybook-flaming-skull.png.

Requires Pillow (recommended: python -m pip install 'Pillow>=11.3').
Run from any working directory: python tools/generate_app_icons.py
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "branding/furybook-flaming-skull.png"
RES_ANDROID = ROOT / "app/src/main/res"
RES_DESKTOP = ROOT / "desktopApp/src/main/resources"
WEB_RESOURCES = (
    ROOT / "webApp/src/jsMain/resources",
    ROOT / "site",
)


def save_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=True)


def square_art(source: Image.Image, size: int) -> Image.Image:
    return source.resize((size, size), Image.Resampling.LANCZOS)


def centered_art(source: Image.Image, size: int, fraction: float, background: tuple[int, int, int, int]) -> Image.Image:
    icon_size = round(size * fraction)
    art = square_art(source, icon_size)
    canvas = Image.new("RGBA", (size, size), background)
    offset = ((size - icon_size) // 2, (size - icon_size) // 2)
    canvas.alpha_composite(art, offset)
    return canvas


def round_art(source: Image.Image, size: int) -> Image.Image:
    image = centered_art(source, size, 0.77, (9, 6, 4, 255))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    image.putalpha(mask)
    return image


def save_ico(source: Image.Image, path: Path, sizes: list[int]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    # The largest high-quality original acts as the input for every internal size.
    largest = square_art(source, max(sizes))
    largest.save(path, format="ICO", sizes=[(size, size) for size in sizes])


def main() -> None:
    with Image.open(SOURCE) as input_image:
        source = input_image.convert("RGBA")
        assert source.width == source.height, "App icon artwork must be square"

        # Android: density-qualified fallback icons + an adaptive foreground with
        # sufficient padding for circular, squircle, and other launcher masks.
        for density, size in {
            "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192,
        }.items():
            folder = RES_ANDROID / f"mipmap-{density}"
            save_png(square_art(source, size), folder / "ic_launcher.png")
            save_png(round_art(source, size), folder / "ic_launcher_round.png")
        save_png(
            centered_art(source, 432, 0.66, (0, 0, 0, 0)),
            RES_ANDROID / "drawable-nodpi/ic_fury_adaptive_foreground.png",
        )

        # Desktop: true multi-resolution Windows ICO plus a 512px icon used by
        # the Compose window, Linux installers and AppImage desktop entries.
        save_png(square_art(source, 512), RES_DESKTOP / "fury-icon.png")
        save_ico(source, RES_DESKTOP / "fury-icon.ico", [16, 24, 32, 48, 64, 128, 256])

        # Both the GitHub Pages landing page and the browser client need their
        # own icons: Pages is /, whereas the application is served under /app/.
        for folder in WEB_RESOURCES:
            save_ico(source, folder / "favicon.ico", [16, 32, 48])
            for size in (16, 32):
                save_png(square_art(source, size), folder / f"favicon-{size}.png")
            save_png(
                centered_art(source, 180, 0.94, (9, 6, 4, 255)),
                folder / "apple-touch-icon.png",
            )
            for size in (192, 512):
                save_png(square_art(source, size), folder / f"icon-{size}.png")
                save_png(
                    centered_art(source, size, 0.72, (9, 6, 4, 255)),
                    folder / f"icon-{size}-maskable.png",
                )
            manifest = {
                "name": "Fury Book",
                "short_name": "Fury Book",
                "description": "Fury Book character manager for DUBL",
                "start_url": "./",
                "scope": "./",
                "display": "standalone",
                "background_color": "#090604",
                "theme_color": "#0E1014",
                "icons": [
                    {"src": f"icon-{size}.png", "sizes": f"{size}x{size}", "type": "image/png", "purpose": "any"}
                    for size in (192, 512)
                ] + [
                    {"src": f"icon-{size}-maskable.png", "sizes": f"{size}x{size}", "type": "image/png", "purpose": "maskable"}
                    for size in (192, 512)
                ],
            }
            (folder / "manifest.webmanifest").write_text(
                json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )


if __name__ == "__main__":
    main()
