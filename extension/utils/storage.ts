// Storage utilities for extension settings

const STORAGE_KEYS = {
  VOLUME: 'twitchPlayerVolume',
} as const;

/**
 * Get saved volume (used by player.ts)
 */
export function getSavedVolume(): number | null {
  const saved = localStorage.getItem(STORAGE_KEYS.VOLUME);
  return saved !== null ? parseFloat(saved) : null;
}

/**
 * Save volume (used by player.ts)
 */
export function setSavedVolume(volume: number): void {
  localStorage.setItem(STORAGE_KEYS.VOLUME, volume.toString());
}
