# ScreenVault

**Smart Screenshot Organizer**

ScreenVault is a serious Android-first mobile utility for organizing screenshots into useful records, reminders, receipts, orders, links, QR/barcode captures, tax items, memos, and action items.

The product goal is simple:

> My screenshots finally have a brain.

## What this MVP does

- Imports screenshots/images manually.
- Scans the media library when permission is granted.
- Creates structured screenshot records.
- Runs local rule-based analysis on pasted/extracted text.
- Detects likely categories such as receipts, orders, returns, appointments, QR/barcodes, links, memos, messages, documents, travel, tax/finance, and unknown.
- Extracts dates, amounts, links, likely order numbers, tracking numbers, and source/vendor clues.
- Flags sensitive screenshots for review.
- Builds an Action Queue from deadlines, return windows, appointments, bills, follow-ups, and reminders.
- Stores everything locally.
- Provides a searchable Vault.
- Creates Reports for receipts, tax records, orders/returns, links, and ideas.
- Exports JSON or CSV text through the system share sheet.

## What this MVP intentionally does not fake

- It does not silently monitor screenshots in the background yet.
- It does not pretend OCR succeeded if OCR is unavailable.
- It does not connect to Amazon, Gmail, banks, or tax software yet.
- It does not delete original photos from the user photo library.
- It does not upload images to cloud AI unless that is added later and explicitly enabled.

## Run

```bash
cd screenvault
npm install
npm start
```

Then open on Android through Expo.

## Important product direction

This should feel like something Samsung or Android could ship as a stock utility: Gallery intelligence + Google Lens + Notes + Reminders + Files + receipt vault.

No mascot. No cartoon task killer theme. The voice is professional, calm, and privacy-first.

## Future build path

1. Native Android screenshot observer/background worker.
2. On-device OCR through ML Kit or Android text recognition.
3. Barcode/QR scanner integration.
4. Optional cloud AI for difficult screenshots.
5. Gmail/Outlook/Calendar authorized connectors.
6. Export formats for tax/accounting tools.
7. Encrypted cloud backup.
8. Household/shared vault.
