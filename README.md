# MY Vault

A private Android media vault for photos and videos.

## Planned features
- Import photos and videos from the device.
- Keep imported media in app-private internal storage.
- Verify a private copy before offering to remove the public original.
- Restore media to public storage when requested.
- Create named albums and organize photos/videos.
- Play videos inside the app with play/pause, seek bar, and 10-second skip forward/back.
- Protect entry with a PIN or device fingerprint authentication.
- Change the PIN from Settings.

## App icon asset
Add the logo image at this exact path:

`app/src/main/res/drawable-nodpi/my_logo.png`

Expected format: PNG. Keep the exact filename `my_logo.png`.

The Android project and GitHub Actions APK build workflow will be added in the implementation stage after the logo is in place.

## Privacy note
App-private storage hides files from ordinary gallery and file-manager apps. It is not a guarantee against rooted devices, malware, or every form of physical access.