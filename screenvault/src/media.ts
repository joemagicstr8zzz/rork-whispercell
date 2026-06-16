import * as ImagePicker from 'expo-image-picker';
import * as MediaLibrary from 'expo-media-library';
import { analyzeScreenshot } from './analyzer';
import { ScanResult, ScreenshotItem } from './types';

export async function importSingleImage(): Promise<ScreenshotItem | null> {
  const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
  if (!permission.granted) return null;

  const result = await ImagePicker.launchImageLibraryAsync({
    mediaTypes: ImagePicker.MediaTypeOptions.Images,
    allowsEditing: false,
    quality: 0.85
  });

  if (result.canceled || !result.assets?.[0]) return null;

  const asset = result.assets[0];
  return analyzeScreenshot({
    imageUri: asset.uri,
    sourceType: 'photo',
    createdAt: new Date().toISOString(),
    ocrText: ''
  });
}

export async function scanRecentScreenshots(existingIds: Set<string>): Promise<ScanResult> {
  const permission = await MediaLibrary.requestPermissionsAsync();
  if (!permission.granted) {
    return {
      granted: false,
      message: 'Photo/media permission was not granted. Manual import still works.',
      items: []
    };
  }

  const assets = await MediaLibrary.getAssetsAsync({
    first: 40,
    mediaType: MediaLibrary.MediaType.photo,
    sortBy: [MediaLibrary.SortBy.creationTime]
  });

  const candidates = assets.assets.filter((asset) => {
    const name = asset.filename?.toLowerCase() ?? '';
    const likelyScreenshot = name.includes('screenshot') || name.includes('screen') || name.includes('capture');
    return likelyScreenshot || assets.assets.length <= 12;
  });

  const items = candidates
    .map((asset) => analyzeScreenshot({
      imageUri: asset.uri,
      sourceType: 'screenshot',
      createdAt: asset.creationTime ? new Date(asset.creationTime).toISOString() : new Date().toISOString(),
      sourceHint: asset.filename,
      ocrText: ''
    }))
    .filter((item) => !existingIds.has(item.imageUri));

  return {
    granted: true,
    message: items.length ? `Found ${items.length} recent image${items.length === 1 ? '' : 's'} to review.` : 'No new screenshots found.',
    items
  };
}

export function ocrUnavailableMessage() {
  return 'OCR is not wired into this MVP yet. Paste the visible screenshot text on the review screen to analyze it.';
}
