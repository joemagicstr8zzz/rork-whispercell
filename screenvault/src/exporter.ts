import { ScreenshotItem } from './types';

const safe = (value: unknown) => String(value ?? '').replace(/"/g, '""');

export function toCsv(items: ScreenshotItem[]): string {
  const header = [
    'title',
    'category',
    'status',
    'priority',
    'dateCaptured',
    'vendor',
    'amounts',
    'dueDate',
    'isReceipt',
    'isTaxRecord',
    'taxCategory',
    'tags',
    'notes'
  ];

  const rows = items.map((item) => [
    item.title,
    item.category,
    item.status,
    item.priority,
    item.createdAt,
    item.detectedOrderInfo?.vendor ?? item.sourceSite ?? '',
    item.detectedAmounts.map((amount) => amount.rawText).join('; '),
    item.dueDate ?? item.reminderAt ?? '',
    item.isReceipt ? 'yes' : 'no',
    item.isTaxRecord ? 'yes' : 'no',
    item.taxCategory ?? '',
    item.tags.join('; '),
    item.userNotes ?? ''
  ]);

  return [header, ...rows]
    .map((row) => row.map((cell) => `"${safe(cell)}"`).join(','))
    .join('\n');
}

export function toJson(items: ScreenshotItem[]): string {
  return JSON.stringify(items, null, 2);
}

export function buildReportSummary(items: ScreenshotItem[]) {
  const receipts = items.filter((item) => item.isReceipt);
  const tax = items.filter((item) => item.isTaxRecord || item.taxCategory === 'needs_review');
  const orders = items.filter((item) => ['order', 'return', 'tracking'].includes(item.category));
  const links = items.filter((item) => item.detectedLinks.length > 0 || item.detectedCodes.length > 0);
  const ideas = items.filter((item) => item.category === 'memo_idea');

  const detectedReceiptTotal = receipts.reduce((total, item) => {
    const firstAmount = item.detectedAmounts[0]?.amount ?? 0;
    return total + firstAmount;
  }, 0);

  return {
    receipts,
    tax,
    orders,
    links,
    ideas,
    detectedReceiptTotal
  };
}
