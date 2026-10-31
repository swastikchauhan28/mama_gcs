# Phase 134 — MapLibre and OkHttp compatibility investigation

Date: 2026-10-31
Starting revision: `debcdff0942661860efb28bc9e88acd48f509a7f`, clean and synchronized with `origin/main`. Model/version unavailable.

## Scope and evidence

Investigate resolved map/network dependencies, upstream release and advisory information, and the previously recorded native crash. This phase does not authorize a dependency or build change, a vehicle connection, or a release. No production code, dependency version, build configuration or user data was changed.

Offline command: `.\gradlew.bat -I "$env:TEMP\mama-gcs-phase58-local-build.gradle" :app:dependencies --configuration debugRuntimeClasspath --offline --no-daemon --max-workers=1 --console=plain`. BUILD SUCCESSFUL, one task executed; evidence `app/build/phase134/runtime-dependencies.txt`. This is the debug runtime graph, not the release graph or a full security scan. It resolves `org.maplibre.gl:android-sdk:13.6.1`, whose graph requests `com.squareup.okhttp3:okhttp:4.12.0`; the app also directly requests OkHttp 4.12.0. The graph resolves Okio 3.6.0 and Kotlin stdlib 2.2.10. Resolved values need not match every direct catalog entry.

`MamaGcsApplication.onCreate` initializes MapLibre and registers an OkHttp client that adds an application User-Agent; `VehicleMap` uses the map SDK. Network and map style/tile content are external inputs. A compatibility change can affect rendering, HTTPS, caching, startup and third-party SDK integration; the dependency tree alone is not proof that another version is compatible.

## Native crash and upstream changes

The saved emulator log `app/build/acceptance-20261030-170631-cf3d7385/android-crash-logcat.txt` records a SIGSEGV/null dereference in `MapRenderer::actor`, via `AndroidRendererFrontend::querySourceFeatures` and `GeoJSONSource::querySourceFeatures`. Repository search found feature-query calls in Android instrumentation and none in the searched `app/src/main` Kotlin sources; this does not rule out queries through a dependency or other code. The stack does not establish why the renderer was unavailable.

[MapLibre Android 13.7.1 release notes](https://github.com/maplibre/maplibre-native/releases/tag/android-v13.7.1) describe a fix for feature-query races with map detachment or renderer reset, including `querySourceFeatures`. This is a credible candidate to investigate because the API and renderer involved overlap the saved stack. It is an inference, not proof that the recorded crash has that cause or that upgrading resolves it. Other changes include renderer and Android fixes and must be assessed in a separate update review.

## Security and advisories

The [MapLibre Native advisory page](https://github.com/maplibre/maplibre-native/security/advisories) and the [OkHttp advisory page](https://github.com/square/okhttp/security/advisories) showed no published repository advisories at the time reviewed; the latter redirected to `lysine-dev/okhttp`. Repository advisory pages are not complete vulnerability databases and an empty page is not a security clearance. The [OkHttp changelog](https://github.com/square/okhttp/blob/master/CHANGELOG.md) describes a 5.5.1 certificate-blocking change with platform-specific caveats. Actual device trust-store state, applicable exposure, transitive advisories and the impact on this app have not been assessed. No comprehensive advisory or SBOM scanner was run.

## Recommendation and future validation

Recommend a separately approved, isolated evaluation of MapLibre 13.7.1 while initially retaining OkHttp 4.12.0, subject to checking the candidate's published dependency metadata, release notes, license and compatibility first. Compare an identical reproducible feature-query/lifecycle scenario on both versions; if no baseline reproduction exists, record that limitation and do not declare the crash fixed. Run a fresh build, lint, unit tests, and healthy Android map recovery, map teardown/recreation, marker/source, offline/error and configured-map visual checks. Verify the resolved runtime and release graphs and the custom client integration. Review UI and network changes and repeat relevant tests on supported devices before considering release. The emulator currently has startup ANRs, so the health precondition remains mandatory.

Defer an OkHttp 5.x change to its own review: determine supported Android/JDK/Kotlin and MapLibre compatibility, transitive changes, TLS/advisory exposure and a targeted HTTPS/redirect/offline test plan. A version suggestion alone is not proof that upgrading is safe or necessary for this specific device.

Rollback plan for a future approved experiment: record and verify a known-good artifact and revision first; use an isolated branch or checkout; restore its MapLibre version and rebuild/retest if the candidate fails, preserving user data and unrelated work. The current revision was not modified and is not represented as a tested production release. Ignored build artifacts and temporary reports may be cleaned. No Android instrumentation, hardware, full build, lint, vulnerability scanner or field test was run in Phase 134.
