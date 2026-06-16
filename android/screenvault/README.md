# ScreenVault Native Android MVP

This is the Android Studio version of ScreenVault.

It is a separate app module inside the existing `android/` Gradle project.

## How to open it

1. Download or clone this branch.
2. Open the existing `android/` folder in Android Studio.
3. Let Gradle sync.
4. In the run configuration/module selector, choose `screenvault`.
5. Run it on your phone or emulator.

## What works now

- Native Android app module: `:screenvault`
- Jetpack Compose UI
- Inbox, Action Queue, Vault, Reports, and Settings tabs
- Manual paste analysis
- Image import through Android photo picker
- Screenshot scan through Android media access permission
- Local rule-based analyzer
- Local SharedPreferences storage
- Search/filter in Vault
- Receipt/tax/order/link reports
- CSV/JSON share export
- Sample records for testing

## Important MVP truth

OCR is not wired in yet. When a screenshot is imported or scanned, ScreenVault stores the image and lets the user paste visible text into the review screen. The analyzer then extracts categories, dates, amounts, links, order numbers, and suggested actions from that text.

Next native milestone:

- Add on-device OCR with ML Kit or Android text recognition.
- Add barcode/QR detection.
- Add Android-native screenshot observer/background worker.
- Add better Gallery-style thumbnail grid scanning.

## Package name

`com.joemagicstr8zzz.screenvault`
