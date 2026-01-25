import type { LiveSource } from './types';

const API_BASE_URL = 'http://localhost:61616';

export function getUsernameFromURL(url: string): string | null {
  try {
    const { pathname } = new URL(url);
    return pathname.split('/').filter(Boolean)[0] || null;
  } catch {
    return null;
  }
}

export async function getFromAPI(endpoint: string): Promise<LiveSource | null> {
  try {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (!response.ok) {
      return null;
    }

    return await response.json();
  } catch {
    return null;
  }
}

export async function getLivestream(url: string): Promise<LiveSource | null> {
  const username = getUsernameFromURL(url);
  if (!username) return null;
  return await getFromAPI(`/live/${username}`);
}
