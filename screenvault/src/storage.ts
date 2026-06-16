import AsyncStorage from '@react-native-async-storage/async-storage';
import { AppSettings, ScreenshotItem } from './types';

const ITEMS_KEY = 'screenvault.items.v1';
const SETTINGS_KEY = 'screenvault.settings.v1';

export const defaultSettings: AppSettings = {
  onboardingComplete: false,
  localOnlyProcessing: true,
  cloudAIProcessing: false,
  hideSensitiveScreenshots: true,
  confirmBeforeSavingSensitive: true,
  scanOnOpen: false
};

export async function loadItems(): Promise<ScreenshotItem[]> {
  try {
    const raw = await AsyncStorage.getItem(ITEMS_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export async function saveItems(items: ScreenshotItem[]): Promise<void> {
  await AsyncStorage.setItem(ITEMS_KEY, JSON.stringify(items));
}

export async function loadSettings(): Promise<AppSettings> {
  try {
    const raw = await AsyncStorage.getItem(SETTINGS_KEY);
    if (!raw) return defaultSettings;
    return { ...defaultSettings, ...JSON.parse(raw) };
  } catch {
    return defaultSettings;
  }
}

export async function saveSettings(settings: AppSettings): Promise<void> {
  await AsyncStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
}

export async function clearAllData(): Promise<void> {
  await AsyncStorage.multiRemove([ITEMS_KEY, SETTINGS_KEY]);
}
