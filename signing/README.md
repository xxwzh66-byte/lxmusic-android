# signing/

`jijian.keystore` — the **release** signing key for every distributed APK.

- Alias: `jijian` · Store/Key password: `jijian2026` · Validity: 30 years
- **Never lose this file.** An APK update signed with a different key is
  rejected by Android (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`); losing it means
  every user must uninstall first.
- CI reads it from the `JIJIAN_KEYSTORE_B64` secret (base64 of this file);
  locally the Gradle signing config falls back to the debug keystore.
- Rotate only by shipping a renumbered app (`applicationId` change), not by
  re-keying this one.

## Why this exists (V1.1.1)

Until 1.1.0 every APK was signed with whatever debug keystore built it — the
local machine's and GitHub Actions' runner certificates differed, so a release
APK could not update a locally installed build (and vice versa) without an
uninstall. `app/build.gradle` now pins `release` to this keystore.
