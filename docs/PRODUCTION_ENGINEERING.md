# Production Engineering Contract

This document defines the engineering bar for VVF Smart Manager.

## Non-negotiable gates

1. Release signing must never fall back to a debug key.
2. CI must be deterministic and fail on build, test, lint, or security regressions.
3. Sensitive data must use authenticated encryption and must not be logged.
4. External integrations must fail closed when correctness cannot be established.
5. Coroutine cancellation must propagate; cancellation is not an ordinary failure.
6. Destructive or restore operations must be verifiable and recoverable before reporting success.
7. Production changes must be covered by automated tests at the appropriate boundary.
8. Secrets belong in the platform secret manager, never in source control.

## Dependency order

Build and release -> security primitives -> data contracts -> persistence -> integrations -> domain -> presentation -> observability -> tests -> deployment.

## Evidence standard

A feature is not considered production-ready merely because it compiles. Readiness requires static review plus successful CI execution for the relevant build, tests, lint, and release path. When execution is unavailable, the limitation must remain explicit.
