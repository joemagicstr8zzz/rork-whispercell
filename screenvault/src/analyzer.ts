import {
  AnalysisInput,
  CodeKind,
  ExtractedAmount,
  ExtractedCode,
  ExtractedContact,
  ExtractedDate,
  ExtractedLink,
  ExtractedOrderInfo,
  Priority,
  ScreenshotCategory,
  ScreenshotItem
} from './types';

export const makeId = (prefix = 'sv') =>
  `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 9)}`;

const nowIso = () => new Date().toISOString();

const keywordGroups: Record<ScreenshotCategory, string[]> = {
  receipt: ['receipt', 'subtotal', 'transaction', 'paid', 'purchase', 'invoice', 'total'],
  order: ['order', 'order number', 'confirmed', 'confirmation', 'shipped', 'delivered', 'package'],
  return: ['return', 'refund', 'exchange', 'replacement', 'return window', 'return by', 'eligible for return'],
  tracking: ['tracking', 'tracking number', 'estimated delivery', 'out for delivery', 'carrier'],
  subscription: ['subscription', 'renews', 'renewal', 'trial', 'free trial', 'cancel', 'monthly', 'annual'],
  appointment: ['appointment', 'reservation', 'booking', 'scheduled', 'meeting', 'doctor', 'dentist', 'interview'],
  qr_barcode: ['qr', 'barcode', 'scan code', 'check-in code'],
  link: ['http://', 'https://', 'www.', '.com', '.jp', '.org'],
  memo_idea: ['idea', 'note to self', 'remember', 'concept', 'brainstorm', 'draft', 'prompt', 'plan'],
  message_followup: ['reply', 'respond', 'get back', 'let me know', 'please confirm', 'follow up', 'can you send'],
  document_form: ['form', 'application', 'paperwork', 'document', 'upload', 'submit', 'signature', 'registration'],
  travel: ['flight', 'hotel', 'passport', 'visa', 'itinerary', 'boarding', 'check-in', 'airport'],
  finance_tax: ['tax', 'deductible', 'expense', 'reimbursement', 'bill', 'payment', 'balance', 'statement'],
  funny_share: ['meme', 'joke', 'funny', 'share this', 'lol', 'haha'],
  personal: ['personal', 'family', 'home', 'health', 'medical', 'school'],
  unknown: []
};

const vendorHints = [
  'amazon', 'walmart', 'target', 'ebay', 'etsy', 'rakuten', 'paypal', 'venmo',
  'starbucks', 'home depot', 'lowes', 'costco', 'apple', 'google', 'samsung'
];

const actionCategories: ScreenshotCategory[] = [
  'return', 'tracking', 'subscription', 'appointment', 'message_followup',
  'document_form', 'travel', 'finance_tax', 'order'
];

const lower = (text: string) => text.toLowerCase();
const hasAny = (text: string, words: string[]) => words.some((word) => text.includes(word));

export const categoryLabel = (category: ScreenshotCategory) => {
  const labels: Record<ScreenshotCategory, string> = {
    receipt: 'Receipt',
    order: 'Order',
    return: 'Return',
    tracking: 'Tracking',
    subscription: 'Subscription',
    appointment: 'Appointment',
    qr_barcode: 'QR / Barcode',
    link: 'Link',
    memo_idea: 'Memo / Idea',
    message_followup: 'Message Follow-up',
    document_form: 'Document / Form',
    travel: 'Travel',
    finance_tax: 'Finance / Tax',
    funny_share: 'Funny / Share Later',
    personal: 'Personal',
    unknown: 'Unknown'
  };
  return labels[category];
};

export const priorityLabel = (priority: Priority) =>
  priority.charAt(0).toUpperCase() + priority.slice(1);

export function extractLinks(text: string): ExtractedLink[] {
  const matches = text.match(/https?:\/\/[^\s)]+|www\.[^\s)]+/gi) ?? [];
  return Array.from(new Set(matches)).map((url) => {
    const normalized = url.startsWith('www.') ? `https://${url}` : url;
    let domain = normalized;
    try { domain = new URL(normalized).hostname.replace(/^www\./, ''); } catch {}
    return { id: makeId('link'), url: normalized, domain };
  });
}

export function extractAmounts(text: string): ExtractedAmount[] {
  const regex = /(?:¥|\$|USD|JPY)\s?\d{1,3}(?:[,\d]{0,12})(?:\.\d{2})?|\d{1,3}(?:[,\d]{0,12})(?:\.\d{2})?\s?(?:yen|usd|jpy)/gi;
  const matches = text.match(regex) ?? [];
  return matches.slice(0, 10).map((rawText) => {
    const currency = rawText.includes('¥') || /yen|jpy/i.test(rawText) ? 'JPY' : rawText.includes('$') || /usd/i.test(rawText) ? 'USD' : undefined;
    const amount = Number(rawText.replace(/[^0-9.]/g, '')) || undefined;
    return { id: makeId('amt'), rawText, currency, amount };
  });
}

export function extractDates(text: string): ExtractedDate[] {
  const patterns = [
    /(?:due|expires|return by|return until|appointment|delivery|delivered|renews|renewal|scheduled|booking)\s*(?:on|by|until)?\s*([A-Z][a-z]{2,8}\s+\d{1,2},?\s*\d{0,4})/gi,
    /\b\d{4}-\d{2}-\d{2}\b/g,
    /\b\d{1,2}\/\d{1,2}(?:\/\d{2,4})?\b/g,
    /\b(?:today|tomorrow|next week|this weekend)\b/gi,
    /\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{1,2}(?:,?\s*\d{4})?\b/gi
  ];
  const found: string[] = [];
  patterns.forEach((pattern) => {
    const matches = [...text.matchAll(pattern)];
    matches.forEach((match) => found.push(match[1] ?? match[0]));
  });

  return Array.from(new Set(found)).slice(0, 12).map((rawText) => {
    const context = text.slice(Math.max(0, text.toLowerCase().indexOf(rawText.toLowerCase()) - 30), text.toLowerCase().indexOf(rawText.toLowerCase()) + rawText.length + 30).toLowerCase();
    let dateType: ExtractedDate['dateType'] = 'unknown';
    if (context.includes('return')) dateType = 'return_by';
    else if (context.includes('appointment') || context.includes('scheduled') || context.includes('booking')) dateType = 'appointment';
    else if (context.includes('delivery') || context.includes('delivered')) dateType = 'delivery';
    else if (context.includes('renew')) dateType = 'renewal';
    else if (context.includes('due') || context.includes('expires')) dateType = 'due';
    return { id: makeId('date'), rawText, dateType };
  });
}

export function extractCodes(text: string, barcodeValues: string[] = []): ExtractedCode[] {
  const results: ExtractedCode[] = barcodeValues.map((value) => ({ id: makeId('code'), type: 'qr', value }));

  const candidates = [
    { type: 'order' as CodeKind, regex: /(?:order(?: number| #| no\.)?[:\s]+)([A-Z0-9-]{6,})/gi },
    { type: 'tracking' as CodeKind, regex: /(?:tracking(?: number| #| no\.)?[:\s]+)([A-Z0-9-]{8,})/gi },
    { type: 'confirmation' as CodeKind, regex: /(?:confirmation(?: number| #| code)?[:\s]+)([A-Z0-9-]{5,})/gi },
    { type: 'coupon' as CodeKind, regex: /(?:coupon|promo)(?: code)?[:\s]+([A-Z0-9-]{4,})/gi }
  ];

  candidates.forEach(({ type, regex }) => {
    [...text.matchAll(regex)].forEach((match) => {
      if (match[1]) results.push({ id: makeId('code'), type, value: match[1] });
    });
  });

  return results.slice(0, 20);
}

export function extractContacts(text: string): ExtractedContact[] {
  const results: ExtractedContact[] = [];
  const emails = text.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi) ?? [];
  emails.forEach((value) => results.push({ id: makeId('contact'), type: 'email', value }));
  const phones = text.match(/(?:\+?\d[\d\s().-]{7,}\d)/g) ?? [];
  phones.slice(0, 5).forEach((value) => results.push({ id: makeId('contact'), type: 'phone', value }));
  vendorHints.forEach((vendor) => {
    if (lower(text).includes(vendor)) results.push({ id: makeId('contact'), type: 'company', value: titleCase(vendor) });
  });
  return results;
}

function titleCase(input: string) {
  return input.replace(/\b\w/g, (char) => char.toUpperCase());
}

function inferSource(text: string, sourceHint?: string) {
  const t = lower(`${sourceHint ?? ''} ${text}`);
  const found = vendorHints.find((vendor) => t.includes(vendor));
  if (found) return titleCase(found);
  const link = extractLinks(text)[0];
  return link?.domain;
}

function inferCategory(text: string, codes: ExtractedCode[]): ScreenshotCategory {
  const t = lower(text);
  if (codes.length && !text.trim()) return 'qr_barcode';

  const scored = Object.entries(keywordGroups)
    .filter(([category]) => category !== 'unknown')
    .map(([category, words]) => ({
      category: category as ScreenshotCategory,
      score: words.reduce((total, word) => total + (t.includes(word) ? 1 : 0), 0)
    }))
    .sort((a, b) => b.score - a.score);

  const best = scored[0];
  if (!best || best.score === 0) {
    if (codes.length) return 'qr_barcode';
    if (extractLinks(text).length) return 'link';
    return text.trim().length > 0 ? 'memo_idea' : 'unknown';
  }

  if (best.category === 'receipt' && hasAny(t, keywordGroups.return)) return 'return';
  if (best.category === 'order' && hasAny(t, keywordGroups.return)) return 'return';
  return best.category;
}

function inferPriority(text: string, category: ScreenshotCategory, dates: ExtractedDate[]): Priority {
  const t = lower(text);
  if (hasAny(t, ['overdue', 'final notice', 'expires today', 'due today', 'urgent', 'last day'])) return 'urgent';
  if (hasAny(t, ['return window', 'bill', 'payment due', 'appointment', 'renewal', 'cancel before', 'submit'])) return 'high';
  if (dates.some((d) => d.dateType === 'return_by' || d.dateType === 'due' || d.dateType === 'appointment')) return 'high';
  if (actionCategories.includes(category)) return 'medium';
  return 'low';
}

function inferTitle(text: string, category: ScreenshotCategory, orderInfo: ExtractedOrderInfo, vendor?: string) {
  const cleanLines = text.split('\n').map((line) => line.trim()).filter(Boolean);
  const productLine = cleanLines.find((line) => /item|product|description/i.test(line));
  if (category === 'return') return vendor ? `${vendor} Return` : 'Return Window';
  if (category === 'order') return vendor ? `${vendor} Order` : 'Order Confirmation';
  if (category === 'receipt') return vendor ? `${vendor} Receipt` : 'Receipt';
  if (category === 'appointment') return 'Appointment';
  if (category === 'subscription') return 'Subscription Renewal';
  if (category === 'qr_barcode') return 'QR / Barcode';
  if (category === 'document_form') return 'Document or Form';
  if (category === 'message_followup') return 'Message Follow-up';
  if (category === 'travel') return 'Travel Record';
  if (category === 'memo_idea') return 'Saved Idea';
  if (productLine) return productLine.slice(0, 52);
  return cleanLines[0]?.slice(0, 52) || 'Imported Screenshot';
}

function inferSummary(text: string, category: ScreenshotCategory, vendor?: string) {
  const line = text.split('\n').map((x) => x.trim()).find(Boolean);
  if (category === 'return') return `Possible return/refund item${vendor ? ` from ${vendor}` : ''}.`;
  if (category === 'order') return `Order or confirmation screenshot${vendor ? ` from ${vendor}` : ''}.`;
  if (category === 'receipt') return `Receipt or purchase record${vendor ? ` from ${vendor}` : ''}.`;
  if (category === 'appointment') return 'Appointment or scheduled event found.';
  if (category === 'qr_barcode') return 'QR code, barcode, or scannable value saved.';
  if (category === 'link') return 'Link or web page screenshot saved.';
  if (category === 'memo_idea') return 'Possible idea, note, or memory item.';
  if (category === 'message_followup') return 'Message may need a reply or follow-up.';
  if (!text.trim()) return 'Image imported. Add visible text to improve analysis.';
  return line.slice(0, 120);
}

function inferSuggestedAction(category: ScreenshotCategory, dates: ExtractedDate[]) {
  if (category === 'return') return 'Review before the return window closes.';
  if (category === 'subscription') return 'Review renewal and cancel if you no longer need it.';
  if (category === 'appointment') return 'Confirm details and set a reminder.';
  if (category === 'message_followup') return 'Send a reply or follow up.';
  if (category === 'document_form') return 'Complete or file the document.';
  if (category === 'finance_tax') return 'Review amount and mark as tax record if needed.';
  if (category === 'receipt') return 'Save for records or mark as tax-related.';
  if (category === 'tracking') return 'Track the shipment or save the tracking number.';
  if (category === 'order') return dates.length ? 'Save order details and watch for delivery or return dates.' : 'Save order details for later.';
  if (category === 'qr_barcode' || category === 'link') return 'Save code or link for later use.';
  return 'Save, tag, or ignore this item.';
}

function inferOrderInfo(text: string, codes: ExtractedCode[], dates: ExtractedDate[], vendor?: string): ExtractedOrderInfo {
  const orderCode = codes.find((code) => code.type === 'order');
  const trackingCode = codes.find((code) => code.type === 'tracking');
  const returnDate = dates.find((date) => date.dateType === 'return_by');
  const deliveryDate = dates.find((date) => date.dateType === 'delivery');
  const productLine = text.split('\n').find((line) => /item|product/i.test(line));
  return {
    vendor,
    orderNumber: orderCode?.value,
    trackingNumber: trackingCode?.value,
    productName: productLine?.replace(/item|product|:/gi, '').trim(),
    returnBy: returnDate?.rawText,
    deliveredDate: deliveryDate?.rawText
  };
}

function inferTags(category: ScreenshotCategory, source?: string, isReceipt?: boolean, isTaxRecord?: boolean) {
  const tags = [categoryLabel(category).toLowerCase()];
  if (source) tags.push(source.toLowerCase());
  if (isReceipt) tags.push('receipt');
  if (isTaxRecord) tags.push('tax');
  return Array.from(new Set(tags));
}

function detectSensitive(text: string) {
  const t = lower(text);
  return hasAny(t, ['password', 'bank account', 'credit card', 'passport number', 'medical record', 'diagnosis', 'prescription', 'address']);
}

export function analyzeScreenshot(input: AnalysisInput): ScreenshotItem {
  const importedAt = nowIso();
  const createdAt = input.createdAt ?? importedAt;
  const extractedText = input.ocrText?.trim() ?? '';
  const links = extractLinks(extractedText);
  const dates = extractDates(extractedText);
  const amounts = extractAmounts(extractedText);
  const codes = extractCodes(extractedText, input.barcodeValues);
  const contacts = extractContacts(extractedText);
  const source = inferSource(extractedText, input.sourceHint);
  const category = inferCategory(extractedText, codes);
  const priority = inferPriority(extractedText, category, dates);
  const isReceipt = category === 'receipt' || hasAny(lower(extractedText), ['receipt', 'invoice', 'subtotal', 'total']);
  const isTaxRecord = hasAny(lower(extractedText), ['tax', 'deductible', 'business expense', 'work expense', 'reimbursement']);
  const isSensitive = detectSensitive(extractedText);
  const orderInfo = inferOrderInfo(extractedText, codes, dates, source);
  const dueDate = dates.find((date) => ['return_by', 'due', 'appointment', 'renewal'].includes(date.dateType ?? ''))?.rawText;
  const hasAction = actionCategories.includes(category) || !!dueDate || priority === 'high' || priority === 'urgent';

  return {
    id: makeId('item'),
    imageUri: input.imageUri,
    sourceType: input.sourceType,
    sourceSite: source,
    createdAt,
    importedAt,
    updatedAt: importedAt,
    title: inferTitle(extractedText, category, orderInfo, source),
    summary: inferSummary(extractedText, category, source),
    category,
    status: hasAction ? 'action' : 'inbox',
    priority,
    extractedText,
    confidence: extractedText.length > 40 ? 'high' : extractedText.length > 0 ? 'medium' : 'low',
    detectedDates: dates,
    detectedAmounts: amounts,
    detectedLinks: links,
    detectedCodes: codes,
    detectedContacts: contacts,
    detectedOrderInfo: orderInfo,
    suggestedAction: inferSuggestedAction(category, dates),
    dueDate,
    isReceipt,
    isTaxRecord,
    taxCategory: isTaxRecord ? 'needs_review' : undefined,
    needsReview: isSensitive || extractedText.length === 0 || category === 'unknown',
    isSensitive,
    tags: inferTags(category, source, isReceipt, isTaxRecord)
  };
}

export function reanalyzeItem(item: ScreenshotItem, nextText: string): ScreenshotItem {
  const analyzed = analyzeScreenshot({
    imageUri: item.imageUri,
    sourceType: item.sourceType,
    ocrText: nextText,
    barcodeValues: item.detectedCodes.map((code) => code.value),
    createdAt: item.createdAt,
    sourceHint: item.sourceSite ?? item.sourceApp
  });
  return {
    ...item,
    ...analyzed,
    id: item.id,
    importedAt: item.importedAt,
    updatedAt: nowIso()
  };
}
