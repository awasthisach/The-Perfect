# OpenAI API key security

The Android client must not embed `OPENAI_API_KEY` in the APK or source tree. Android application binaries are client-controlled and secrets embedded in them can be extracted.

## CI

Configure `OPENAI_API_KEY` as a GitHub Actions repository secret. Workflows may expose it only to a trusted backend/deployment step that requires it. Do not print the variable, pass it to build artifacts, or write it to logs.

## Local development

Use an untracked local environment file or environment variable. A template is provided at `config/openai.env.example`.

## Architecture

For production OpenAI calls, prefer:

`Android app -> authenticated backend -> OpenAI API`

The backend owns the OpenAI credential and applies authentication, authorization, rate limiting, request validation, timeout/retry policy, and audit-safe logging.

The Android client should never receive or persist the OpenAI API credential.
