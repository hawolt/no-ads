import { injectScript } from 'wxt/utils/inject-script';
import type { LocationChangeDetail } from '@/utils/types';
import { insertReplacementButton } from '@/utils/button';
import { logger } from '@/utils/logger';
import { getAutoReplaceEnabled } from '@/utils/storage';
import { modifyVideoElement } from '@/utils/player';

let replaceButtonClicked = false;
let currentObserver: MutationObserver | null = null;

function hasSubscribeButton(): boolean {
  return !!document.querySelector('button[data-a-target="subscribe-button"]');
}

async function handleNavigation(url: string) {
  logger.log(`handle navigation to: ${url}`);

  replaceButtonClicked = false;

  const autoReplace = await getAutoReplaceEnabled();
  
  if (autoReplace) {
    observeForAutoReplace();
  } else {
    observeSubscribeButton();
  }
}

function observeForAutoReplace() {
  if (currentObserver) {
    currentObserver.disconnect();
    currentObserver = null;
  }

  if (replaceButtonClicked) return;

  currentObserver = new MutationObserver(() => {
    tryAutoReplace();
  });

  if (document.body) {
    currentObserver.observe(document.body, {
      childList: true,
      subtree: true,
    });
    tryAutoReplace();
  }
}

async function tryAutoReplace() {
  if (replaceButtonClicked) return;

  if (hasSubscribeButton()) {
    const hasVideo = document.querySelector('video');
    if (hasVideo) {
      logger.log('Auto-replacing player (subscribe button found - not subscribed)...');
      replaceButtonClicked = true;
      if (currentObserver) {
        currentObserver.disconnect();
        currentObserver = null;
      }
      await modifyVideoElement();
    }
  }
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
  },
});
