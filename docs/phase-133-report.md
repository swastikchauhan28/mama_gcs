# Phase 133 — dependency-version warning review

Date: 2026-10-31
Starting revision: `f05faa11ff92e48ed317d68a98ed039e409ead85`, clean and aligned with `origin/main`. Model/version unavailable.

## Scope and evidence

Review all 26 version warnings in Phase 132's fresh lint XML against `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, and the app's dependency declarations. No versions, dependencies, repositories, build configuration, application or test code were changed. This is not a complete transitive dependency or vulnerability audit. Lint's suggested versions below are captured observations, not recommendations or independently verified current stable releases; confirm release channel, compatibility, advisories and artifacts before proposing an upgrade.

The counts below sum to 26; warnings for artifacts sharing a version are grouped. Priority means investigation order, not a finding that the current component is vulnerable. AndroidX components are maintained by Google/AndroidX; Kotlin and coroutines by JetBrains; Hilt/Dagger by Google; MapLibre by the MapLibre community; OkHttp by Square/Block; Gradle by Gradle Inc.; AGP by Google. Their project source/release references are linked below.

| Component / warning count | Current -> lint suggestion | Role and disposition |
| --- | --- | --- |
| Gradle wrapper / 1 | 8.13 -> 8.14.6 | Build execution; defer separate toolchain compatibility review with AGP. |
| Android Gradle Plugin / 1 | 8.13.2 -> 9.5.0 | Android build; high migration risk; investigate coordinated Gradle/Kotlin/Hilt changes before any proposal. |
| Kotlin Android, kapt, Compose plugins / 3 | 2.1.0 -> 2.4.21 | Compiler and generated code; keep versions aligned; defer to toolchain review. |
| AndroidX core-ktx / 1 | 1.17.0 -> 1.19.1 | Android compatibility APIs; review compatibility and permission behavior, then schedule separately. |
| AndroidX lifecycle process/runtime/compose / 3 | 2.9.4 -> 2.11.0 | Lifecycle observation; review as a group with Compose and activity. |
| AndroidX activity-compose / 1 | 1.12.2 -> 1.13.0 | Compose activity integration; assess with lifecycle and UI tests. |
| AndroidX Compose UI, test JUnit, test manifest, tooling, preview / 5 | 1.9.0 -> 1.12.1 | UI rendering and instrumentation; keep shared version aligned and assess layout and test compatibility. |
| AndroidX Material3 / 1 | 1.3.0 -> 1.4.0 | UI components; review visual, accessibility and Compose compatibility. |
| AndroidX navigation-compose / 1 | 2.9.8 -> 2.10.2 | Screen navigation; review migration notes and navigation regression. |
| AndroidX hilt-navigation-compose / 1 | 1.3.0 -> 1.4.0 | Hilt navigation integration; assess with AndroidX and Hilt compatibility. |
| Dagger/Hilt plugin, runtime and compiler / 3 | 2.56.2 -> 2.60.1 | Dependency injection and code generation; keep versions matched; assess with Kotlin/AGP. |
| Kotlin coroutines Android and test / 2 | 1.9.0 -> 1.11.0 | Async operations and tests; review cancellation and scheduling behavior, align runtime/test. |
| AndroidX DataStore preferences / 1 | 1.1.1 -> 1.3.0 | Persistent settings; review compatibility and recovery with copies of synthetic data before proposing. |
| MapLibre Android / 1 | 13.6.1 -> 13.7.1 | Maps and native rendering; prioritize crash/release-note investigation and compatibility assessment. An upgrade is not shown to fix the prior crash. |
| OkHttp / 1 | 4.12.0 -> 5.5.1 | HTTP client used by MapLibre; prioritize advisory and major-version compatibility investigation together with map integration. No vulnerability finding is established by the warning. |

## Findings and planned verification

1. MapLibre and OkHttp merit an early focused investigation: `MamaGcsApplication` initializes MapLibre and supplies a custom OkHttp client with an app User-Agent; `VehicleMap` consumes the map SDK and configured map services are external inputs. Review the specific vendor release notes and dependency compatibility, then, only after separately approving a concrete update, run fresh build/lint/unit checks, healthy Android map load/recovery, route-marker and offline/error flows, plus device visual checks. The prior `GeoJSONSource::querySourceFeatures` native crash is unproven and must not be declared fixed without reproduction or defensible evidence.
2. AGP 9 is a coordinated migration rather than a simple version bump: Google documents built-in Kotlin and Gradle compatibility requirements. The current project explicitly applies Kotlin Android and kapt plugins. Determine a supported Gradle/AGP/Kotlin/Hilt combination, JDK requirements and rollback, then seek specific approval before edits. Run clean build/lint/unit and full healthy emulator regression on an approved migration.
3. AndroidX/Compose/lifecycle/navigation and coroutines/Hilt affect broad UI and concurrency. Review vendor release notes and interdependencies before proposing small compatible groups. Test navigation, Bluetooth permission UI, accessibility, orientation, and lifecycle behavior; use healthy emulator/device instrumentation. DataStore changes additionally require synthetic persistence, backup/recovery and migration checks without touching operator data.
4. The captured versions do not establish whether a dependency is supported, secure, or affected by an advisory. Review direct and transitive resolved dependencies, upstream advisories and applicable exposure separately. No production dependency update is authorized by this review.

References: [AndroidX release notes](https://developer.android.com/jetpack/androidx/versions), [AGP built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin), [AGP/Gradle compatibility](https://developer.android.com/build/releases/about-agp), [MapLibre releases](https://github.com/maplibre/maplibre-native/releases), [OkHttp project](https://github.com/square/okhttp), [Kotlin releases](https://github.com/JetBrains/kotlin/releases), [Dagger/Hilt releases](https://github.com/google/dagger/releases), [coroutines releases](https://github.com/Kotlin/kotlinx.coroutines/releases), [Gradle releases](https://gradle.org/releases/).

## Verification and limitations

Reconciled the Phase 132 lint XML: 14 `GradleDependency`, 10 `NewerVersionAvailable`, 2 `AndroidGradlePluginVersion`; grouping by catalog/wrapper line accounts for all 26. The earlier 478 unit tests and lint success are historical Phase 132 results, not newly run here. No emulator, physical device, hardware, build or security scanner was used. Emulator ANRs, the prior MapLibre crash, physical telemetry, field accuracy and release gates remain open. The referenced lint evidence is in the temporary configured build directory and may be cleaned. Commit/push require explicit authorization.
