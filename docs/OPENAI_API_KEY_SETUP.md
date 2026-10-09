# OpenAI API key: secure project setup

## Security rule

Never place an OpenAI API key in Android source, resources, `BuildConfig`, the APK, a browser bundle, a `VITE_*` variable, an index backup, logs, or a committed `.env` file. OpenAI's official guidance is to send requests through a trusted backend that stores the key as a secret.

Official references:
- Create/manage keys: https://help.openai.com/en/articles/4936850-where-do-i-find-my-openai-api-key
- API key safety: https://help.openai.com/en/articles/5112595-best-practices-for-api-key-safety
- Cloudflare Worker secrets: https://developers.cloudflare.com/workers/configuration/secrets/

## Current integration status (verified against this branch)

- The Android app is a native Kotlin application; it must not call OpenAI directly with a private API key.
- The current embedding service contract is the external Cloudflare Worker at `https://drive-semantic-embed.awasthi-sach.workers.dev`.
- The existing Worker implementation uses `GEMINI_API_KEY` and the `gemini-embedding-2` model. Adding an `OPENAI_API_KEY` secret by itself will **not** switch or enable OpenAI: the Worker currently has no OpenAI provider implementation.
- Android embedding requests remain disabled until the documented native authentication/App Check gate is implemented and verified. Do not bypass Origin checks or use a Drive OAuth token as an embedding bearer token.
- Local keyword and metadata search must remain available when embeddings are unavailable.

## Safe key creation and storage

1. Create a dedicated project API key from the OpenAI Platform setup flow. Prefer a restricted key and an expiry date compatible with the project's rotation plan.
2. Save or download the key when it is first shown. The full secret may not be viewable again; if it is lost, create a replacement.
3. Do not paste the key into a GitHub issue, pull request, commit, chat, or app configuration.
4. Before the key is used, implement and review an OpenAI provider in the trusted backend Worker. Keep the existing Gemini provider as the default until the OpenAI path passes tests.
5. Once the backend provider exists, add the key through Cloudflare **Workers & Pages → the embed Worker → Settings → Variables and Secrets → Add → Secret**, named `OPENAI_API_KEY`, and deploy the Worker. Never store the value as a plain-text `vars` entry.
6. Configure the backend provider/model explicitly and bump the embedding model/version contract so old vectors cannot be mixed with vectors from a different model. Rebuild/re-embed the index after switching providers.
7. Verify authentication, rate limits, bounded input/output, response dimensions, timeouts, and redacted logging before enabling the provider in the app.

## Release gate

This document does not claim that OpenAI is already wired into the deployed app. Do not enable the provider or mark neural search ready until backend implementation, secret configuration, Android authentication, migration/re-index behavior, and live integration tests have passed. Never expose the API key to the Android client.
