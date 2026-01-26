import { injectScript } from 'wxt/utils/inject-script';
import type { LocationChangeDetail } from '@/utils/types';
import { insertReplacementButton } from '@/utils/button';
import { logger } from '@/utils/logger';

let replaceButtonClicked = false;
let currentObserver: MutationObserver | null = null;

function handleNavigation(url: string) {
  logger.log(`handle navigation to: ${url}`);
  tryInsertButton();
}

function tryInsertButton() {
  if (replaceButtonClicked) return;

  insertReplacementButton(() => {
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
    logger.log('[twitch-adblock] extension loaded');
    injectScript('/inject-history.js', { keepInDom: false });

    window.addEventListener('twitch-adblock:locationchange', ((e: CustomEvent<LocationChangeDetail>) => {
      handleNavigation(e.detail.url);
    }) as EventListener);

    handleNavigation(window.location.href);
    observeSubscribeButton();
  },
});
