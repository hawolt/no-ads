// Storage utilities for extension settings

const STORAGE_KEYS = {
  VOLUME: 'twitchPlayerVolume',
  AUTO_REPLACE: 'autoReplaceEnabled'
} as const;

export function getSavedVolume(): number | null {
  const saved = localStorage.getItem(STORAGE_KEYS.VOLUME);
  return saved !== null ? parseFloat(saved) : null;
}

export function setSavedVolume(volume: number): void {
  localStorage.setItem(STORAGE_KEYS.VOLUME, volume.toString());
}

function getStorageAPI() {
  if (typeof browser !== 'undefined' && browser.storage) {
    return browser.storage;
  }
  if (typeof chrome !== 'undefined' && chrome.storage) {
    return chrome.storage;
  }
  return null;
}

export async function getAutoReplaceEnabled(): Promise<boolean> {
  const storage = getStorageAPI();
  if (storage) {
    const result = await storage.local.get(STORAGE_KEYS.AUTO_REPLACE);
    return result[STORAGE_KEYS.AUTO_REPLACE] ?? false;
  }
  const stored = localStorage.getItem(STORAGE_KEYS.AUTO_REPLACE);
  return stored === 'true';
}

export async function setAutoReplaceEnabled(enabled: boolean): Promise<void> {
  const storage = getStorageAPI();
  if (storage) {
    await storage.local.set({ [STORAGE_KEYS.AUTO_REPLACE]: enabled });
  } else {
    localStorage.setItem(STORAGE_KEYS.AUTO_REPLACE, enabled.toString());
  }
}