# Electric Lens — UI/UX Handoff

This bundle redesigns the Electric Lens Android app's UI/UX. Hand the whole folder to Claude (e.g. Claude Code in the repo) and have it follow the steps below.

## What's inside
- **BUILD_PROMPT.md** — the authoritative spec. The full visual system + your stack, data, architecture rules, and safety language. **This is the main instruction set — read it first.**
- **theme/Color.kt, Type.kt, Dimens.kt** — ready-to-commit design tokens. Drop into `ui/theme/`.
- **reference/visual-reference.html** — the target look as a clickable web prototype. **Reference only** — do NOT port web code; rebuild natively in Compose. Open it in a browser to see the intended colors, spacing, chips, stepper, and motion.

## Instructions for Claude (do in this order)
1. Read **BUILD_PROMPT.md** fully. Treat §1 (hard constraints) and §7/§8 (safety language, acceptance path) as non-negotiable.
2. Inspect the repo first (build.gradle.kts, MainActivity, SessionViewModel, AppState, AppNavHost.kt, existing screens, ui/theme/*). Adapt existing state before adding new.
3. Add the three **theme/** files to `ui/theme/`:
   - Change the `package` line in each to match the repo's actual package.
   - Wire them into `ElectricLensTheme` in Theme.kt (snippet is in Color.kt's header comment): `colorScheme = ElectricDarkColorScheme`, `typography = ElTypography`, `shapes = ElShapes`, and wrap content in `CompositionLocalProvider(LocalElColors provides DefaultElExtendedColors)`.
   - Type.kt compiles as-is with system fallbacks; the real fonts are optional (instructions inside the file).
4. Build the 5 screens per BUILD_PROMPT.md §6, using the shared composables in §5, honoring the mobile layout contract in §4.
5. Preserve the state-machine navigation (no NavController), Mock + VLM modes, CameraX, TtsManager, and PdfDocument generation.
6. Run the Gradle build, fix all compile errors, leave no broken/unused imports.
7. Report the §4 mobile self-check (PASS/FAIL per item) at 360.dp and 430.dp.

## Non-negotiables (repeated so they don't get lost)
- Kotlin + Jetpack Compose + Material 3 only. No XML, no WebView, no NavController, no internet permission, no cloud calls.
- Keep Mock mode deterministic; never fake VLM success on failure.
- Never claim equipment is safe. Green = "evidence captured/verified". Use the approved copy in the prompt.
- Use the demo data verbatim: F071 OC1 / Overcurrent Phase B / Conveyor CV-104 / VFD-104 / MCC-2 | Bucket 17 / B-201 + B-205 / PowerFlex_753_VFD_Manual.pdf.
