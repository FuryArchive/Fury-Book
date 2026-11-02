# Linux packaging

## End-user artifact

Linux x86_64 users should receive a single executable file:

`Fury-Book-<version>-linux-x86_64.AppImage`

They do not need Gradle, a JDK, Android Studio, or the Android SDK.

## Canonical release build

`packaging/linux/build-appimage.sh` first builds the real Compose Desktop distributable (including its JVM runtime) and then converts it to an AppImage. `.github/workflows/linux-appimage.yml` runs this for `main` pushes and manual dispatches. Production tags are built and published by `.github/workflows/release.yml`.

