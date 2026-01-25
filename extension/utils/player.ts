import Hls from 'hls.js';
import type { LiveSource } from './types';

let currentHlsInstance: Hls | null = null;

export function modifyVideoElement(source: LiveSource | null): void {
  if (!source || !source.live || !source.playlist) return;

  const root = document.querySelector('[data-a-player-state]');
  if (!root || !Hls.isSupported()) return;

  root.removeAttribute('data-a-player-state');

  let videos = root.querySelectorAll('video');
  if (!videos.length) {
    videos = document.querySelectorAll('video');
  }
  if (!videos.length) return;

  const video = videos[0];
  const parent = video.parentNode;
  if (!parent) return;

  video.remove();

  const ref = document.querySelector('[data-a-target="video-ref"]');
  if (ref) {
    const videoChild = ref.querySelector('[class*="video"]') as HTMLElement | null;
    if (videoChild) {
      videoChild.style.display = 'none';
    }
  }

  if (currentHlsInstance) {
    currentHlsInstance.destroy();
    currentHlsInstance = null;
  }

  const inject = document.createElement('video');
  inject.playsInline = true;
  inject.controls = true;
  inject.autoplay = true;
  inject.muted = false;

  try {
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
    hls.loadSource(source.playlist);
    hls.attachMedia(inject);

    hls.on(Hls.Events.ERROR, (_event, data) => {
      if (data.fatal) {
        console.error('[twitch-adblock] HLS fatal error:', data.type, data.details);
      }
    });

    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      inject.play().catch(() => {});
    });

    parent.appendChild(inject);
  } catch (err) {
    console.error('[twitch-adblock] Failed to set up player:', err);
  }
}

export function hasVideoElements(): boolean {
  const root = document.querySelector('[data-a-player-state]');
  if (!root) return false;
  return root.querySelectorAll('video').length > 0;
}
