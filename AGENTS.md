# Repository Guidelines

## Project Structure & Module Organization

HorrorHunterGame is a Java/libGDX game with three Gradle modules:

- `core/src/main/java/ru/darvell/game/hhg/`: shared game logic; `Main` creates the initial `FirstScreen`. Keep platform-independent gameplay here.
- `lwjgl3/src/main/java/`: desktop launcher and startup helpers; desktop icons and resources live in `lwjgl3/icons/` and `lwjgl3/src/main/resources/`.
- `android/src/main/java/`: Android launcher. Android resources and manifest live in `android/res/` and `android/AndroidManifest.xml`.
- `assets/`: shared runtime assets, currently a placeholder. Gradle generates `assets/assets.txt`; do not edit or commit that generated list.

## Build, Test, and Development Commands

Run commands from the repository root using the checked-in wrapper (`gradlew.bat` on Windows). The Gradle daemon configuration selects JDK 21; application sources target Java 8. Android builds require an Android SDK with platform 36.

- `./gradlew lwjgl3:run`: launch the desktop game using `assets/` as its working directory.
- `./gradlew lwjgl3:jar`: create the runnable game JAR in `lwjgl3/build/libs/`.
- `./gradlew build`: build all modules and run configured checks.
- `./gradlew core:test`: run shared-code unit tests, when added.
- `./gradlew android:assembleDebug`: build the debug APK.
- `./gradlew android:lint`: run Android static validation.

## Coding Style & Naming Conventions

Follow `.editorconfig`: UTF-8, LF endings, spaces, final newlines, four-space Java indentation, and two-space Gradle indentation. Remove trailing whitespace except where Markdown requires it. No dedicated formatter is configured.

Use `UpperCamelCase` for classes, `lowerCamelCase` for methods and fields, and lowercase package names under `ru.darvell.game.hhg`. Preserve Java 8 compatibility. Release owned libGDX resources in the appropriate lifecycle methods.

## Testing Guidelines

No tests, test framework dependencies, or coverage thresholds are currently configured. Add shared unit tests under `core/src/test/java/`, mirror production packages, and name classes `*Test`. Configure the chosen framework before relying on the test task. For gameplay changes, manually check desktop startup, rendering, resizing, and relevant Android behavior.

## Commit & Pull Request Guidelines

Git history is unavailable in this checkout, so existing commit conventions cannot be verified. Use concise, imperative subjects such as `Add player movement` and keep commits focused.

PRs should explain the behavior changed, link relevant issues, and report checks performed. Include screenshots or recordings for visible changes. Keep SDK paths in ignored `local.properties`; exclude generated builds and native libraries.
