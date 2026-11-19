# Contributing to Antigravity Pulse

Thank you for your interest in contributing to **Antigravity Pulse**! This project is open source and maintained by **Solomon Ogar Bukie**.

## Ways to Contribute

- **Report Bugs**: Open a bug report with clear reproduction steps, Android version, and device model.
- **Suggest Enhancements**: Propose focused features that improve fullscreen usability, performance, or reliability.
- **Submit Pull Requests**: Fix bugs, improve accessibility, refine UI polish, or expand test coverage.

## Development Workflow

1. **Fork & Clone** the repository:
   ```bash
   git clone https://github.com/<your-username>/antigravity-pulse.git
   cd antigravity-pulse
   ```
2. **Create a Feature Branch**:
   ```bash
   git checkout -b feat/your-feature-name
   ```
3. **Prepare Local Environment**:
   ```bash
   cp .env.example .env
   ```
4. **Make Your Changes**:
   - Follow standard Kotlin & Jetpack Compose idioms.
   - Use Material Design 3 tokens and `AppearanceSettings` theme colors instead of hardcoded colors.
   - Maintain minimum `48dp` touch targets and meaningful `contentDescription` / `testTag` values for interactive elements.
   - Never add broad Android storage permissions (`READ_EXTERNAL_STORAGE` / `READ_MEDIA_*`); always use Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT`) or Photo Picker.
5. **Run Verification Locally**:
   ```bash
   gradle :app:testDebugUnitTest
   gradle :app:assembleDebug
   ```
6. **Open a Pull Request**:
   Provide a clear summary of what changed, why it was needed, and screenshots or test results when applicable.

## Code of Conduct

Be respectful, constructive, and welcoming to all contributors. Harassment or abusive behavior is not tolerated.

## Maintainer

**Solomon Ogar Bukie** — [solomonogarbukie@gmail.com](mailto:solomonogarbukie@gmail.com)
