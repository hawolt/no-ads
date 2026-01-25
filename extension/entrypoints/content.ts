import { injectScript } from 'wxt/utils/inject-script';
import type { LiveSource, LocationChangeDetail } from '@/utils/types';
import { getLivestream } from '@/utils/api';
import { modifyVideoElement, hasVideoElements } from '@/utils/player';
import { insertReplacementButton, removeReplaceButton } from '@/utils/button';

const LOG_PREFIX = '[twitch-adblock]';

function log(...args: unknown[]) {
  console.log(LOG_PREFIX, ...args);
}

let replaceButtonClicked = false;
let currentLiveSource: LiveSource | null = null;
let currentObserver: MutationObserver | null = null;

function handleNavigation(url: string) {
  log('navigated to:', url);
  replaceButtonClicked = false;

  getLivestream(url).then((source) => {
    currentLiveSource = source;
    log('Got live source:', source);

    const root = document.querySelector('[data-a-player-state]');
    if (root && !hasVideoElements()) {
      log('navigated with ad-free player, replacing video element');
      modifyVideoElement(source);
      log('removing replace button');
      replaceButtonClicked = true;
      removeReplaceButton();
    }

    // Try to insert button immediately after getting source
    tryInsertButton();
  });
}

function tryInsertButton() {
  if (replaceButtonClicked) return;

  insertReplacementButton(currentLiveSource, () => {
    replaceButtonClicked = true;
    if (currentObserver) {
      currentObserver.disconnect();
      currentObserver = null;
    }
  });
  // Don't disconnect observer - Twitch may re-render and remove our button
}

function observeSubscribeButton() {
  // Disconnect existing observer if any
  if (currentObserver) {
    currentObserver.disconnect();
    currentObserver = null;
  }

  if (replaceButtonClicked) return;

  currentObserver = new MutationObserver(() => {
    tryInsertButton();
  });

  if (document.body) {
    currentObserver.observe(document.body, {
      childList: true,
      subtree: true,
    });
    log('MutationObserver started');
    // Try immediately after setting up observer
    tryInsertButton();
  } else {
    log('Warning: document.body not available yet');
  }
}

export default defineContentScript({
  matches: ['*://www.twitch.tv/*'],
  main() {
    log('extension loaded');

    injectScript('/inject-history.js', { keepInDom: false })
      .then(() => log('History script injected'))
      .catch((err) => log('Failed to inject history script:', err));

    window.addEventListener('twitch-adblock:locationchange', ((e: CustomEvent<LocationChangeDetail>) => {
      handleNavigation(e.detail.url);
    }) as EventListener);

    log('Setting up initial navigation handler');
    handleNavigation(window.location.href);
    observeSubscribeButton();
  },
});
