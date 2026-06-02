# Copilot Instructions for Amaravati Smart City

## Build, test, and lint

The Android project lives in `android-amaravati-smart-city/`.

- Build debug APK: `cd android-amaravati-smart-city && .\gradlew.bat assembleDebug`
- Run all unit tests: `cd android-amaravati-smart-city && .\gradlew.bat test`
- Run one unit test class: `cd android-amaravati-smart-city && .\gradlew.bat testDebugUnitTest --tests com.uc.amaravatismartcity.ExampleUnitTest`
- Run instrumented tests: `cd android-amaravati-smart-city && .\gradlew.bat connectedDebugAndroidTest`
- Run lint: `cd android-amaravati-smart-city && .\gradlew.bat lint`

On macOS/Linux, use `./gradlew` instead of `gradlew.bat`.

## High-level architecture

- `MainActivity` is the app entry point and uses Compose + `NavDisplay` from Navigation 3 to switch between routes.
- Navigation is defined in `navigation/AppRoute.kt` as a `@Serializable sealed interface` so destinations stay type-safe.
- `ui/screens/MainMenuScreen.kt` is the landing screen; `ui/screens/CityViewScreen.kt` is the main gameplay surface.
- Game state is centralized in `game/GameState.kt` and exposed through `game/GameViewModel.kt` with `MutableStateFlow`/`StateFlow`.
- The theme layer is in `ui/theme/` and supports dark mode and dynamic color through `AmaravatiSmartCityTheme`.
- 3D rendering currently uses SceneView/Filament-related APIs from the Compose game screen; assets are expected under `app/src/main/assets/`.

## Key conventions

- Use the package root `com.uc.amaravatismartcity` for all app code.
- Keep UI in Compose-first screens under `ui/screens/`; use Android Views only when a performance or integration need is explicit.
- Prefer immutable state models and `copy(...)` updates; `GameViewModel` should remain the single place that mutates game state.
- Keep resource-style values in `GameState` as bounded values where appropriate (for example, percentages stay within `0..100`).
- Treat `.glb` paths as asset-relative strings from the assets root, not filesystem paths.
- Preserve the existing Material 3 + edge-to-edge Compose approach when adding new screens or overlays.
- Android XML is only used for manifest and basic resource wiring; app UI is Compose-based.
