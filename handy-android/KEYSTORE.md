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
