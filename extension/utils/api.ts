import type { LiveSource } from './types';

const API_BASE_URL = 'http://localhost:61616';
const LOG_PREFIX = '[twitch-adblock]';

function log(...args: unknown[]) {
  console.log(LOG_PREFIX, ...args);
}

function error(...args: unknown[]) {
  console.error(LOG_PREFIX, ...args);
}

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
      throw new Error(`API call failed: ${response.status}`);
    }

    const result = await response.json();
    log('API response:', result);
    return result;
  } catch (err) {
    error('Error calling localhost API:', err);
    return null;
  }
}

export async function getLivestream(url: string): Promise<LiveSource | null> {
  const username = getUsernameFromURL(url);

  if (!username) {
    error('No username found in URL');
    return null;
  }

  log(`Fetching live data for username: ${username}`);
  return await getFromAPI(`/live/${username}`);
}
