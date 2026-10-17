# Fury Book application icon

Canonical artwork: `branding/furybook-flaming-skull.png` (original user-provided source, 1254 × 1254 pixels).

The same artwork is installed in every application variant:

- Android: density-qualified launcher PNGs, round launcher PNGs, adaptive icon foreground and XML.
- Desktop: window PNG, multi-resolution Windows ICO, Linux packages and AppImage.
- Web: site and web client favicons, Apple touch icon, normal and maskable PWA icons.

After updating the source artwork, regenerate all assets from the repository root:

```sh
python3 -m pip install 'Pillow>=11.3'
python3 tools/generate_app_icons.py
```

Commit the generated assets together with the source. The website under `site/` and the browser app under `webApp/src/jsMain/resources/` each need their own copies because they are hosted at different URL paths.
