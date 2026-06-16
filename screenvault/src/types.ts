export type ScreenshotCategory =
  | 'receipt'
  | 'order'
  | 'return'
  | 'tracking'
  | 'subscription'
  | 'appointment'
  | 'qr_barcode'
  | 'link'
  | 'memo_idea'
  | 'message_followup'
  | 'document_form'
  | 'travel'
  | 'finance_tax'
  | 'funny_share'
  | 'personal'
  | 'unknown';

export type ScreenshotStatus =
  | 'inbox'
  | 'saved'
  | 'action'
  | 'snoozed'
  | 'done'
  | 'archived'
  | 'ignored';

export type Priority = 'low' | 'medium' | 'high' | 'urgent';
export type Confidence = 'low' | 'medium' | 'high';

export type DateKind =
  | 'due'
  | 'return_by'
  | 'appointment'
  | 'order_date'
  | 'delivery'
  | 'renewal'
  | 'unknown';

export type CodeKind =
  | 'qr'
  | 'barcode'
  | 'tracking'
  | 'order'
  | 'confirmation'
  | 'coupon'
  | 'unknown';

export type ContactKind = 'email' | 'phone' | 'address' | 'person' | 'company';

export type TaxCategory =
  | 'work'
  | 'business'
  | 'medical'
  | 'travel'
  | 'education'
  | 'charity'
  | 'personal'
  | 'needs_review';

export interface ExtractedDate {
  id: string;
  rawText: string;
  normalizedDate?: string;
  dateType?: DateKind;
}

export interface ExtractedAmount {
  id: string;
  rawText: string;
  currency?: string;
  amount?: number;
  context?: string;
}

export interface ExtractedLink {
  id: string;
  url: string;
  label?: string;
  domain?: string;
}

export interface ExtractedCode {
  id: string;
  type: CodeKind;
  value: string;
  format?: string;
}

export interface ExtractedContact {
  id: string;
  type: ContactKind;
  value: string;
}

export interface ExtractedOrderInfo {
  vendor?: string;
  orderNumber?: string;
  trackingNumber?: string;
  productName?: string;
  returnBy?: string;
  deliveredDate?: string;
}

export interface ScreenshotItem {
  id: string;
  imageUri: string;
  thumbnailUri?: string;
  sourceType: 'screenshot' | 'photo' | 'manual' | 'sample';
  sourceApp?: string;
  sourceSite?: string;
  createdAt: string;
  importedAt: string;
  updatedAt: string;

  title: string;
  summary: string;
  category: ScreenshotCategory;
  status: ScreenshotStatus;
  priority: Priority;

  extractedText: string;
  detectedLanguage?: string;
  confidence: Confidence;

  detectedDates: ExtractedDate[];
  detectedAmounts: ExtractedAmount[];
  detectedLinks: ExtractedLink[];
  detectedCodes: ExtractedCode[];
  detectedContacts: ExtractedContact[];
  detectedOrderInfo?: ExtractedOrderInfo;

  suggestedAction?: string;
  reminderAt?: string;
  dueDate?: string;

  isReceipt: boolean;
  isTaxRecord: boolean;
  taxCategory?: TaxCategory;
  needsReview: boolean;
  isSensitive: boolean;

  tags: string[];
  userNotes?: string;
}

export interface AppSettings {
  onboardingComplete: boolean;
  localOnlyProcessing: boolean;
  cloudAIProcessing: boolean;
  hideSensitiveScreenshots: boolean;
  confirmBeforeSavingSensitive: boolean;
  scanOnOpen: boolean;
  lastScanAt?: string;
}

export interface AnalysisInput {
  imageUri: string;
  sourceType: ScreenshotItem['sourceType'];
  ocrText?: string;
  barcodeValues?: string[];
  createdAt?: string;
  sourceHint?: string;
}

export interface ScanResult {
  granted: boolean;
  message: string;
  items: ScreenshotItem[];
}
