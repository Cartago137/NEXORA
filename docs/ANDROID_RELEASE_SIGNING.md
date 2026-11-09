# NEXORA Android release signing

NEXORA uses one persistent Android upload key for every installable update. Never commit the keystore, passwords, or private key to Git.

## One-time setup

Create a keystore on a trusted computer and keep a secure backup. Example with Java keytool:

```bash
keytool -genkeypair -v -keystore nexora-upload.jks -alias nexora-upload -keyalg RSA -keysize 4096 -validity 10000
```

Choose strong passwords and store them in a password manager. Do not send the keystore or passwords in chat, issues, commits, or public files.

## Configure GitHub Actions secrets

In the NEXORA repository, open Settings > Secrets and variables > Actions > New repository secret. Add:

- `NEXORA_KEYSTORE_BASE64`: Base64 encoding of the complete `nexora-upload.jks` file.
- `NEXORA_STORE_PASSWORD`: keystore password.
- `NEXORA_KEY_ALIAS`: `nexora-upload` (or the alias chosen when creating the key).
- `NEXORA_KEY_PASSWORD`: key password.

On macOS/Linux, create the Base64 value with `base64 -w 0 nexora-upload.jks` (on macOS, use `base64 nexora-upload.jks | tr -d '\\n'`). On Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("nexora-upload.jks"))
```

Paste the output as the secret value. Never commit the Base64 output: it is the private keystore encoded as text.

## Build and update behavior

- The Android application ID remains `com.nexora.app`.
- Version 0.2.0 uses versionCode 2.
- The workflow continues to produce a debug APK for development.
- It produces a separate signed release APK only after all four secrets are configured.
- Every later release must use this exact same keystore and a higher versionCode.
- Android can install an update over an existing installation only when the application ID matches, the signing certificate matches, and the new versionCode is higher.

The current debug APK was built by CI with an ephemeral debug key. The persistent release key cannot be assumed to match it. The first signed release may therefore require uninstalling the old debug build. Back up any app data first; uninstalling removes private app data. After the first signed release is installed, future signed releases can update over it while normally preserving app data.

Losing the keystore can prevent future in-place updates. Keep at least two secure backups.
