# APA Chinese/English Localization Design

Date: 2026-09-28

## Goal

APA supports a real in-app language switch between Simplified Chinese and English. Chinese remains the default. Selecting English updates every user-facing screen and newly generated user-facing output, survives process death and device restart, and does not translate raw device evidence such as model names, vendor names, application labels, package names, command output, or imported report content.

## User experience

The Settings page presents two mutually exclusive controls: `中文` and `English`. The active language is visibly selected. Selecting the other language persists the preference and recreates the activity so Android resources and Compose content are rebuilt in one language. Selecting the already active language does nothing.

The switch covers:

- bottom navigation and the Device, Agent, Capability, Apps, and Settings screens;
- buttons, status labels, empty states, dialogs, errors, privacy disclosures, import help, and accessibility text;
- locally generated device, battery, power-diagnostic, hardware-grade, and observation explanations;
- generated share-card labels and captions;
- the Agent system instruction and attachment headings, so cloud responses use the selected language;
- local fallback Agent responses.

Raw evidence remains unchanged. This includes application labels supplied by Android, package names, device and chipset identifiers, supplier names, kernel scheduler names, paths, commands, numeric values, and imported Bugreport excerpts.

## Language model

`AppLanguage` has exactly two values: Simplified Chinese and English. `AppLanguageStore` persists the selected value in private SharedPreferences and returns Simplified Chinese when no valid preference exists.

`MainActivity.attachBaseContext` wraps the base context with the stored locale before Android inflates resources. The Settings language action stores the new value and calls `recreate()`. This supports the app's minimum Android version without changing the activity superclass or introducing an AppCompat dependency.

Android 13+ per-app language system integration is not required for this first release. APA's own setting remains the single source of truth, which keeps behavior identical across Android 8 through current Android versions.

## Resource architecture

Static UI copy moves from Kotlin literals into Android string resources:

- `res/values/strings.xml` contains the English default resources.
- `res/values-zh-rCN/strings.xml` contains Simplified Chinese resources.
- formatted strings use numbered placeholders such as `%1$s` and `%2$d`.
- repeated concepts use shared keys; unrelated sentences do not reuse keys merely because their current text matches.

Composable UI reads resources with `stringResource`. Non-composable Android boundaries receive translated strings from their caller or use a localized Android `Context` where they already depend on Android.

Pure Kotlin analyzers and report builders remain independent of Android `Context`. They accept `AppLanguage` and select templates through small domain-specific copy providers. This avoids introducing Android dependencies into unit-testable analysis code. Language parameters default to Simplified Chinese only where existing call sites require a staged migration; final production call sites pass the current language explicitly.

## Generated content

Report builders localize their own headings, explanations, confidence labels, advice labels, limitations, and next-step text. Evidence values are interpolated without translation. A report is generated entirely in the language active at generation time.

Share cards use the active language for labels and footer copy. Previously generated bitmaps are not retroactively modified; reopening the preview after a language switch regenerates the card in the new language.

Agent prompt construction receives `AppLanguage`. English mode adds an explicit English response requirement and uses English attachment headings and safety instructions. Chinese mode preserves the current Chinese prompt behavior. The user message is never translated.

Stored conversation messages retain the language in which they were created. Switching languages changes controls and future replies but does not rewrite chat history.

## Migration boundaries

The implementation migrates user-facing strings, not parser tokens. Parser matching constants, package-name rules, shell-output markers, vendor aliases, and imported report headings stay in the language expected by their source data.

Developer-only logs and test fixture strings do not require localization unless they are surfaced to users. Preview/sample data may remain fixed when it represents raw input, but preview UI labels use resources.

To prevent mixed-language screens, production Kotlin sources receive a final scan for Chinese literals. Every remaining Chinese literal must be classified as one of:

1. source-data parser token;
2. raw evidence fixture or vendor alias;
3. Chinese resource/copy-provider implementation;
4. an intentional Chinese example in a safety rule or test.

Unclassified user-facing literals block completion.

## Failure handling

An unknown or corrupt saved language value falls back to Simplified Chinese. If activity recreation is interrupted, the stored preference is applied on the next launch. Formatting errors are prevented with resource-format lint and tests for representative formatted strings.

A missing English resource is treated as a build/review defect rather than silently displaying Chinese, because English is the default resource set. Chinese resources may not rely on English fallback for user-facing copy.

## Accessibility and layout

English text is often wider than Chinese text. Navigation labels and compact controls must be tested for clipping on a narrow phone width. Buttons may wrap when safe; identity rows and values that must remain one line receive explicit overflow behavior. Content descriptions follow the active language.

The language controls expose their selected state through Compose semantics, not only through a visual bullet.

## Testing

Unit tests cover:

- preference parsing and Simplified Chinese fallback;
- representative report builders in both languages;
- Agent prompt response-language instructions;
- raw package, model, and vendor values remaining unchanged.

Compose/instrumentation tests cover:

- Settings shows both language choices and the active selection;
- tapping English recreates the activity and shows English navigation and screen headings;
- English survives activity recreation;
- switching back to Chinese restores Chinese navigation;
- a narrow layout does not clip the main navigation labels;
- a share preview and a diagnostic screen use the active language.

The normal verification gate remains unit tests, AndroidTest compilation, Debug and Release lint, and Debug and Release assembly. Connected UI tests are attempted, but the known Android 17 instrumentation startup hang is reported separately if it prevents execution.

## Acceptance criteria

The feature is complete when a user can select English, navigate every primary workflow without encountering unclassified Chinese UI copy, generate English reports and share cards, receive an English Agent answer, restart APA with English retained, and switch back to Chinese without losing reports, settings, or conversation history.
