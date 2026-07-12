# Release Keystore

To generate a release keystore for signing:

```bash
keytool -genkey -v -keystore handy-release.keystore \
  -alias handy \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass <store-password> \
  -keypass <key-password>
```

Set environment variables before building:

```bash
export HANDY_KEYSTORE_PATH=../handy-release.keystore
export HANDY_KEYSTORE_PASSWORD=<store-password>
export HANDY_KEY_ALIAS=handy
export HANDY_KEY_PASSWORD=<key-password>
```

**Never commit the keystore or passwords to version control.**

## CI/CD Signing Setup

To enable automated signing in GitHub Actions:

1. **Generate a keystore** (see instructions above)
2. **Base64-encode the keystore:**
   ```bash
   base64 -w0 handy-release.keystore > handy-release.keystore.b64
   ```
3. **Add GitHub Secrets** in your repository settings (Settings → Secrets and variables → Actions):
   | Secret Name | Value |
   |---|---|
   | `ANDROID_KEYSTORE_BASE64` | Contents of `handy-release.keystore.b64` |
   | `HANDY_KEYSTORE_PASSWORD` | Keystore store password |
   | `HANDY_KEY_ALIAS` | Key alias (default: `handy`) |
   | `HANDY_KEY_PASSWORD` | Key password |
   | `SENTRY_DSN` | Sentry DSN for crash reporting |

4. The CI workflows in `.github/workflows/android-ci.yml` and `.github/workflows/android-release.yml` will automatically use these secrets when building release artifacts.
