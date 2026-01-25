import { injectScript } from 'wxt/utils/inject-script';
import type { LiveSource, LocationChangeDetail } from '@/utils/types';
import { getLivestream } from '@/utils/api';
import { modifyVideoElement, hasVideoElements } from '@/utils/player';
import { insertReplacementButton, removeReplaceButton } from '@/utils/button';

let replaceButtonClicked = false;
let currentLiveSource: LiveSource | null = null;
let currentObserver: MutationObserver | null = null;

function handleNavigation(url: string) {
  replaceButtonClicked = false;

  getLivestream(url).then((source) => {
    currentLiveSource = source;

    const root = document.querySelector('[data-a-player-state]');
    if (root && !hasVideoElements()) {
      modifyVideoElement(source);
      replaceButtonClicked = true;
      removeReplaceButton();
    }

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
}

function observeSubscribeButton() {
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
    tryInsertButton();
  }
}

export default defineContentScript({
  matches: ['*://www.twitch.tv/*'],
  main() {
    console.log('[twitch-adblock] Extension loaded');
    injectScript('/inject-history.js', { keepInDom: false });

    window.addEventListener('twitch-adblock:locationchange', ((e: CustomEvent<LocationChangeDetail>) => {
      handleNavigation(e.detail.url);
    }) as EventListener);

    handleNavigation(window.location.href);
    observeSubscribeButton();
  },
});
