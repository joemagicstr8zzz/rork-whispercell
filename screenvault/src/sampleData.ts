import { analyzeScreenshot } from './analyzer';
import { ScreenshotItem } from './types';

const placeholder = (label: string) => `sample://${encodeURIComponent(label)}`;

export function createSampleItems(): ScreenshotItem[] {
  const samples = [
    analyzeScreenshot({
      imageUri: placeholder('amazon-order'),
      sourceType: 'sample',
      sourceHint: 'Amazon',
      ocrText: `Amazon order #503-8291472-1923410\nItem: Anker 737 Power Bank\nOrdered on May 1, 2025\nDelivered on May 4, 2025\nPrice: ¥17,980\nReturn window closes on May 18, 2025\nThe power bank is heavier than expected. Need to decide if keeping it.`
    }),
    analyzeScreenshot({
      imageUri: placeholder('dental-appointment'),
      sourceType: 'sample',
      ocrText: `Dental Appointment\nJune 20, 2026 at 10:00 AM\nPlease arrive 15 minutes early.\nClinic: Sasebo Dental Office\nPlease confirm appointment before the visit.`
    }),
    analyzeScreenshot({
      imageUri: placeholder('home-depot-receipt'),
      sourceType: 'sample',
      sourceHint: 'Home Depot',
      ocrText: `THE HOME DEPOT\nReceipt\n05/03/2025 11:42 AM\nLumber 2x4x8 ¥2,180\nScrews 1lb Box ¥680\nPaint Brush ¥420\nDrop Cloth ¥1,540\nSubtotal ¥4,820\nTax ¥0\nTotal ¥4,820\nPossible work expense. Needs review for tax records.`
    }),
    analyzeScreenshot({
      imageUri: placeholder('qr-code'),
      sourceType: 'sample',
      barcodeValues: ['https://example.com/checkin'],
      ocrText: `Event Check-In QR Code\nScan to open check-in page\nhttps://example.com/checkin`
    }),
    analyzeScreenshot({
      imageUri: placeholder('project-idea'),
      sourceType: 'sample',
      ocrText: `Idea: AI receipt and screenshot organizer\nRemember this product concept.\nCould help people find why they saved screenshots.\nPotential features: tags, reminders, tax folders, search.`
    }),
    analyzeScreenshot({
      imageUri: placeholder('funny-share'),
      sourceType: 'sample',
      ocrText: `Funny screenshot\nShare this later 😂\nThis is probably just a meme or joke to send to someone.`
    }),
    analyzeScreenshot({
      imageUri: placeholder('electric-bill'),
      sourceType: 'sample',
      ocrText: `Electric Bill\nPayment due in 3 days\nAmount due: ¥7,860\nDue May 15, 2025\nPlease pay before due date to avoid late fee.`
    })
  ];

  return samples.map((item, index) => ({
    ...item,
    id: `sample_${index + 1}`,
    importedAt: new Date(Date.now() - index * 3600 * 1000).toISOString(),
    updatedAt: new Date(Date.now() - index * 3600 * 1000).toISOString()
  }));
}
