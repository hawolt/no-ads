import Hls from 'hls.js';
import { logger } from './logger';
import { getLivestream } from "@/utils/api";

let currentHlsInstance: Hls | null = null;

export async function modifyVideoElement() {
  const source: LiveSource | null = await getLivestream(window.location.href);

  if (!source || !source.live || !source.playlist) return;

  logger.info("using source:", source);

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

    // liveSyncDuration of sub 2 is not possible due to twitch segments being 2 seconds long
    // twitch has its own player which supports binary streams unlike hls.js which requires a full segment
    // thus liveSyncDuration: 2.5 will achieve:
    // 1. jump to the latest segment
    // 2. fail to fetch it when not jet present, with a return code of 500 as the file is not available when requested
    // 3. buffer once or twice until we are stable with 2-4 seconds delay behind the actual stream
    // 4. if we fall behind further than 4 seconds liveMaxLatencyDuration: 4 will cause rebuffering

    const hls = new Hls({
      lowLatencyMode: true,
      liveSyncDuration: 2.5,
      liveMaxLatencyDuration: 4,
      backBufferLength: 0,
      maxBufferLength: 6,
      maxBufferSize: 10 * 1000 * 1000,
      liveDurationInfinity: true,
      enableWorker: true,
      abrEwmaFastLive: 3,
      abrEwmaSlowLive: 9,
    });

    currentHlsInstance = hls;
    hls.loadSource(source.playlist);
    hls.attachMedia(inject);

    hls.on(Hls.Events.ERROR, (_event, data) => {
      if (data.fatal) {
        logger.error('[twitch-adblock] HLS fatal error:', data.type, data.details);
      }
    });

    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      inject.play().catch(() => {});
    });
    
    parent.appendChild(inject);
  } catch (err) {
    logger.error('[twitch-adblock] Failed to set up player:', err);
  }
}

export function hasVideoElements(): boolean {
  const root = document.querySelector('[data-a-player-state]');
  if (!root) return false;
  return root.querySelectorAll('video').length > 0;
}
