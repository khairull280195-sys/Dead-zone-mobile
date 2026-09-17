# DeadZone Mobile — no Unity required

This is an original Godot 4 mobile zombie-survival prototype. GitHub Actions builds the Android APK automatically, so Unity Hub is not required.

## Automatic APK build

1. Upload this project to a GitHub repository.
2. Open the repository's **Actions** tab.
3. Select **Build DeadZone Android APK**.
4. When the job finishes, download the **DeadZoneMobile-APK** artifact.
5. Extract the artifact ZIP and install `DeadZoneMobile.apk` on Android.

The first build can take several minutes because the Android build environment is downloaded. The APK is a debug build intended for personal testing.

## Gameplay

- Left side touch: move.
- Right side touch: aim and fire.
- Survive increasing zombie waves.
- Get 10 kills and reach the green safe room.
- Desktop testing: WASD, mouse click, R to reload.

Version 0.2 uses lightweight procedural 2D graphics so it runs on a wider range of Android phones. Future versions can add 3D graphics, campaign maps, special infected, weapons and online co-op.
