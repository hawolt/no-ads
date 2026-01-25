import Hls from 'hls.js';
import type { LiveSource } from './types';

const LOG_PREFIX = '[twitch-adblock]';

function log(...args: unknown[]) {
  console.log(LOG_PREFIX, ...args);
}

function error(...args: unknown[]) {
  console.error(LOG_PREFIX, ...args);
}

let currentHlsInstance: Hls | null = null;

export function modifyVideoElement(source: LiveSource | null): void {
  if (!source || !source.live || !source.playlist) {
    log('User is not live or no playlist available - no modifications made');
    return;
  }

  const root = document.querySelector('[data-a-player-state]');

  if (!root) {
    error('Player root not found');
    return;
  }

  if (!Hls.isSupported()) {
    error('HLS.js is not supported in this browser');
    return;
  }

  log('Removing data-a-player-state attribute to disable React control...');
  root.removeAttribute('data-a-player-state');

  let videos = root.querySelectorAll('video');

  if (!videos.length) {
    videos = document.querySelectorAll('video');
  }

  if (!videos.length) {
    error('No video elements found in the DOM');
    return;
  }

  const video = videos[0];
  const parent = video.parentNode;

  if (!parent) {
    error('Video parent not found');
    return;
  }

  log('Removing existing video element...');
  video.remove();

  const ref = document.querySelector('[data-a-target="video-ref"]');

  if (ref) {
    const videoChild = ref.querySelector('[class*="video"]') as HTMLElement | null;

    if (videoChild) {
      videoChild.style.display = 'none';
    }
  }

  if (currentHlsInstance) {
    log('Destroying previous HLS instance...');
    currentHlsInstance.destroy();
    currentHlsInstance = null;
  }

  log('Creating new video element...');
  const inject = document.createElement('video');

  inject.playsInline = true;
  inject.controls = true;
  inject.autoplay = true;
  inject.muted = false;

  try {
    log('Creating HLS instance...');
    const hls = new Hls({
      lowLatencyMode: true,
      liveSyncDurationCount: 2,
      liveMaxLatencyDurationCount: 4,
      backBufferLength: 30,
      maxBufferLength: 60,
      maxBufferSize: 30 * 1000 * 1000,
      liveDurationInfinity: true,
    });

    currentHlsInstance = hls;

    log('Loading source:', source.playlist);
    hls.loadSource(source.playlist);

    log('Attaching media...');
    hls.attachMedia(inject);

    hls.on(Hls.Events.ERROR, (event, data) => {
      error('HLS error:', data.type, data.details, data.fatal);
      if (data.url) {
        error('Failed URL:', data.url);
      }
      if (data.response) {
        error('Response:', data.response);
      }
      if (data.reason) {
        error('Reason:', data.reason);
      }
    });

    hls.on(Hls.Events.MANIFEST_PARSED, (event, data) => {
      log('HLS manifest parsed, levels:', data.levels?.length);
      inject.play().catch(e => error('Autoplay failed:', e));
    });

    hls.on(Hls.Events.FRAG_LOADED, (event, data) => {
      log('Fragment loaded:', data.frag?.url);
    });

    log('Appending video to DOM...');
    parent.appendChild(inject);
    log('Video element replacement complete');
  } catch (err) {
    error('Failed to set up HLS player:', err);
  }
}

export function hasVideoElements(): boolean {
  const root = document.querySelector('[data-a-player-state]');
  if (!root) return false;
  return root.querySelectorAll('video').length > 0;
}
