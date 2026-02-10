// Storage utilities for extension settings

const STORAGE_KEYS = {
  AUTO_REPLACE: 'autoReplaceEnabled'
} as const;

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
    const result = await storage.sync.get(STORAGE_KEYS.AUTO_REPLACE);
    return result[STORAGE_KEYS.AUTO_REPLACE] ?? false;
  }
  const stored = localStorage.getItem(STORAGE_KEYS.AUTO_REPLACE);
  return stored === 'true';
}

export async function setAutoReplaceEnabled(enabled: boolean): Promise<void> {
  const storage = getStorageAPI();
  if (storage) {
    await storage.sync.set({ [STORAGE_KEYS.AUTO_REPLACE]: enabled });
  } else {
    localStorage.setItem(STORAGE_KEYS.AUTO_REPLACE, enabled.toString());
  }
}