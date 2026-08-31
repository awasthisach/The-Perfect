# OpenAI API security

The Android application must never embed `OPENAI_API_KEY` in source code, `BuildConfig`, resources, or the APK. An Android client is user-controlled and embedded credentials can be extracted.

## Credential location

- Local development: an untracked `.env` or environment variable.
- CI: GitHub Actions repository secret named `OPENAI_API_KEY`.
- Production API calls: a trusted backend should own the credential.

## Recommended production flow

`Android app -> authenticated backend -> OpenAI API`

The backend should enforce authentication, authorization, request validation, rate limits, timeouts, bounded retries, usage controls, and redacted audit logging.

The Android client should receive only the response it needs and should never receive, persist, or log the OpenAI credential.

## Repository rule

`OPENAI_API_KEY` may appear only as a placeholder in `.env.example` or as a secret reference in CI configuration. Never commit a real value.
