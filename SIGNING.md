# Release Signing

ImageSeek uses a fixed release signing identity for the 2.1.2 release line and future compatible updates.

## Public certificate information

- Alias: `imageseek`
- Keystore type: PKCS12
- Key algorithm: RSA 4096-bit
- Certificate signature algorithm: SHA256withRSA
- Certificate validity: 2026-09-23 through 2051-09-17

SHA-256 certificate fingerprint:

```text
41:79:9F:10:29:28:43:07:16:85:0B:DB:CE:22:EC:DE:31:3E:56:B9:FF:E0:83:36:0B:96:EF:15:BE:40:71:62
```

SHA-1 certificate fingerprint:

```text
18:C1:12:9F:13:75:39:9A:CE:0D:66:0E:6A:28:4F:A7:49:B2:B0:36
```

## ImageSeek 2.1.2 signed APK

SHA-256:

```text
0c0149be33ee860afe91e42083bad36c71910906d46e7be5ad9ea5c309d165c6
```

The APK verifies with Android APK Signature Scheme v3 and passes 16 KiB zipalign verification.

## Private key policy

The private release keystore and its password are **not stored in this public repository**.

The maintainer must preserve the original release keystore. Losing it means future APKs cannot be installed as in-place updates over builds signed with this certificate.

Never commit:

- `*.p12`
- `*.jks`
- `*.keystore`
- `keystore.properties`
- passwords or CI signing secrets
