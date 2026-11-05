# RuStore API TLS trust

`russian_trusted_root_ca.pem` is the public Russian Trusted Root CA certificate
published by the Ministry of Digital Development and Communications. It contains
no private key and is used only by the Python RuStore API client, not the Android app.

- Official certificate portal: https://www.gosuslugi.ru/crt
- Download source: https://gu-st.ru/content/Other/doc/russian_trusted_root_ca.cer
- Retrieved: 2026-11-05, over verified HTTPS.
- Subject/issuer common name: Russian Trusted Root CA (self-signed root).
- DER SHA-256: `d26d2d0231b7c39f92cc738512ba54103519e4405d68b5bd703e9788ca8ecf31`.
- Valid until: 2032-02-27 21:04:15 UTC.

The downloaded certificate was re-encoded as canonical PEM. The client validates
the DER fingerprint before loading it, so checkout line endings do not affect
integrity checks. Updating this file requires reviewing the official source and
updating the pinned DER fingerprint together.

The certificate is added to a dedicated SSLContext; system trust stores and
Python's global HTTPS context are unchanged. Certificate-chain and hostname
verification remain required. Redirects remain blocked. The intermediate Russian
Trusted Sub CA is supplied by the API server and is not installed as a trust anchor.

On 2026-11-05, TLS to `public-api-m.rustore.ru` failed against the standard Mozilla
root bundle (verification code 20) and passed when this root was added. Both the
root and intermediate signatures were verified in the resulting three-certificate
chain. No `CERT_NONE`, insecure TLS context or disabled hostname checks are used.
